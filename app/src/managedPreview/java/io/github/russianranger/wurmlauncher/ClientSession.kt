package io.github.russianranger.wurmlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Owns only a client process; server lifetime and server lock remain independent. */
object ClientSession {
    data class State(val busy: Boolean, val phase: String, val detail: String)
    @Volatile private var state = State(false, "Stopped", "No client process owned by this app.")
    @Volatile private var cancelled = false
    @Volatile private var child: Process? = null
    @Volatile private var inputReady = false
    private val queue = LinkedBlockingQueue<String>(512)
    private var file: File? = null
    private val lines = ArrayDeque<String>()
    private var worker: Thread? = null
    fun snapshot() = state
    fun inputReady() = inputReady && child?.isAlive == true
    fun store(context: Context) = ClientStore(File(context.filesDir, "managed-client"))
    fun profileFile(context: Context) = File(context.filesDir, "controller.properties")
    @Synchronized fun log(message: String) {
        val line = message.take(4000); lines.addLast(line)
        while (lines.size > 1500) lines.removeFirst()
        runCatching { file?.let { if (it.length() > 2 * 1024 * 1024) it.writeText(lines.joinToString("\n") + "\n") else it.appendText(line + "\n") } }
    }
    @Synchronized fun initialize(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "client-session.txt")
        file?.takeIf { it.isFile }?.useLines { it.toList().takeLast(100).forEach { line -> lines.addLast(line.take(4000)) } }
    }
    private fun status(phase: String, detail: String) { state = State(true, phase, detail); log("[app] ${Instant.now()} $phase — $detail") }
    fun recent() = synchronized(this) { lines.takeLast(80).joinToString("\n") }
    fun report(context: Context): String {
        initialize(context)
        val installed = runCatching { store(context).current() }.getOrNull()
        return "Wurm client milestone 0.8.0\nAndroid ${android.os.Build.VERSION.RELEASE}; API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Status: ${state.phase} — ${state.detail}\nDefault target: 127.0.0.1:3724\n" +
            "Gate status: direct profile/resources launch adapter and local Steam shim implemented; rendering, server ticket acceptance and Wurm login are not qualified.\n\n" +
            (installed?.inventory ?: "No accepted client import.\n") + "\nController profile:\n" +
            profileFile(context).takeIf { it.isFile }?.readText().orEmpty() + "\nSession history:\n" +
            (file?.takeIf { it.isFile }?.readText() ?: recent())
    }
    @Synchronized fun start(context: Context, mode: String, uri: Uri?, done: () -> Unit): Boolean {
        if (state.busy) return false
        initialize(context); cancelled = false; queue.clear()
        status("Preparing", "Client operation: $mode")
        worker = Thread({
            try {
                val store = store(context); store.home.mkdirs()
                RandomAccessFile(store.lock, "rw").channel.use { channel ->
                    val lock = channel.tryLock() ?: error("Another client owns the client files")
                    lock.use {
                        // A dying Android owner cannot allow an earlier child to overlap an import.
                        RandomAccessFile(File(store.home, "process.lock"), "rw").channel.use { probe ->
                            (probe.tryLock() ?: error("Previous client process is still exiting; retry shortly")).release()
                        }
                        if (mode == "import") {
                            requireNotNull(context.contentResolver.openInputStream(requireNotNull(uri))).use {
                                val installed = store.importZip(it) { message -> status("Importing", message) }
                                log(installed.inventory)
                            }
                            status("Stopped", "Client import validated. Start Client runs the bootstrap probes.")
                        } else run(context, store, mode)
                    }
                }
            } catch (failure: Throwable) {
                status(if (cancelled) "Stopped" else "Error", if (cancelled) "Client operation stopped." else failure.message ?: failure.javaClass.simpleName)
                log("[app] CLIENT_OPERATION_FAILED ${failure.javaClass.name}: ${failure.message}")
            } finally {
                reapChild()
                synchronized(this) { worker = null; state = state.copy(busy = false) }
                done()
            }
        }, "wurm-client-owner").apply { start() }
        return true
    }
    fun stop() { cancelled = true; child?.destroy(); worker?.interrupt() }
    private fun reapChild() {
        child?.let { process ->
            if (process.isAlive) process.destroyForcibly()
            var exited = false
            while (!exited) { try { process.waitFor(); exited = true } catch (_: InterruptedException) { } }
            log("[client] OWNED_CHILD_REAPED code=${process.exitValue()} cancelled=$cancelled")
        }
        child = null; inputReady = false
    }
    fun send(event: String): Boolean {
        if (!inputReady()) return false
        if (queue.offer(event)) return true
        queue.clear(); queue.offer("RESET"); log("[input] QUEUE_RESET overflow; held input release queued")
        return false
    }
    private fun checkCancelled() { if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedException("Client operation cancelled") }
    private fun reachable() = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 3724), 300) }; true }.getOrDefault(false)
    private fun run(context: Context, store: ClientStore, mode: String) {
        require(mode in listOf("start", "local", "input"))
        val installed = if (mode == "input") null else requireNotNull(store.current()) { "Import the complete client ZIP first" }
        if (mode == "local") {
            if (!reachable()) {
                val server = ManagedSession.snapshot()
                check(!server.busy || server.phase in listOf("Preparing", "Preflight", "Starting", "Running")) { "Finish the current server operation first" }
                if (!server.busy) {
                    requireNotNull(ManagedSession.workspace(context).imports.current()) { "Import the stopped server runtime from the Server screen, or start your installed 0.6.0 server first" }
                    val prefs = context.getSharedPreferences("managed-settings", Context.MODE_PRIVATE)
                    check(prefs.getInt("port", 3724) == 3724) { "Local Game expects TCP 3724" }
                    context.startForegroundService(Intent(context, ManagedServerService::class.java).setAction(ManagedServerService.START))
                }
                status("Waiting for server", "Waiting for local TCP 3724; server keeps its own controls")
                val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
                while (!reachable()) {
                    checkCancelled(); check(System.nanoTime() < deadline) { "Local server readiness timed out; export server report" }
                    check(ManagedSession.snapshot().phase != "Error") { "Server startup failed; export server report" }
                    Thread.sleep(250)
                }
            }
        }
        log("[connection] TCP_PROBE target=127.0.0.1:3724 reachable=${reachable()}; this is NOT a Wurm login")
        installed?.jars?.forEach { name ->
            checkCancelled(); check(ProbeInputs.sha256(File(installed.root, name)) == installed.hashes[name]) { "Client JAR changed since import: $name" }
        }
        val home = ProbeRuntime.install(context, ::log)
        checkCancelled()
        val native = File(context.applicationInfo.nativeLibraryDir)
        val session = File(store.home, "session-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val tmp = File(session, "tmp").apply { mkdirs() }
        val user = File(store.home, "user").apply { mkdirs() }
        val helper = File(session, "runtime-probe.jar")
        context.assets.open("runtime-probe.jar").use { input -> helper.outputStream().use { input.copyTo(it) } }
        val compat = File(session, "client-compat.jar")
        context.assets.open("client-compat.jar").use { input -> compat.outputStream().use { input.copyTo(it) } }
        try {
            val cp = listOf(helper.absolutePath) + installed?.jars.orEmpty().map { File(installed!!.root, it).absolutePath }
            log("[client] Runtime root=${installed?.root}; no server JARs, server Steam shim or JavaFX launcher added")
            log("[client] CLIENT_JVM_MODE exec; Android graphics surface adapter not installed")
            val stages = if (mode == "input") listOf("input") else listOf("inventory", "compat", "entry", "graphics")
            val player = context.getSharedPreferences("client-settings", Context.MODE_PRIVATE).getString("player", "Thor") ?: "Thor"
            require(player.matches(Regex("[A-Za-z][A-Za-z0-9]{2,19}"))) { "Save a valid local player name" }
            val results = linkedMapOf<String, Int>()
            for (stage in stages) {
                checkCancelled(); queue.clear(); inputReady = false
                status(if (stage == "input") "Input diagnostic" else "Starting client", "Bootstrap stage: $stage")
                val stageCp = if (stage in listOf("compat", "entry")) listOf(compat.absolutePath) + cp else cp
                log("[client] CLASSPATH stage=$stage ${stageCp.joinToString(":")}")
                val args = listOf(File(native, "libwurmjvm_runner.so").absolutePath, "-Xms32m", "-Xmx1024m",
                    // The packaged JRE is built --enable-headless-only=yes. AWT X11 is unavailable;
                    // native LWJGL window creation is still attempted independently below.
                    "-Djava.home=$home", "-Djava.io.tmpdir=$tmp", "-Duser.home=$user", "-Djava.awt.headless=true",
                    "-Djava.library.path=$home/lib:$home/lib/server:$native", "-Dsun.boot.library.path=$home/lib:$native",
                    "-XX:ErrorFile=$session/hs_err_pid%p.log", "-XX:-CreateCoredumpOnCrash",
                    "-Dwurm.client.host=127.0.0.1", "-Dwurm.client.port=3724", "-Dwurm.client.offline=true", "-Dwurm.client.player=$player",
                    "-cp", stageCp.joinToString(":"), "client.ClientBootstrap", stage)
                val process = ProcessBuilder(args).directory(installed?.root ?: session).redirectErrorStream(true).apply {
                    environment().clear(); environment().putAll(ProbeEnvironment.create(home, native, tmp))
                    environment()["WURM_HEAP_TAGGING"] = "off"
                    environment()["WURM_WORLD_LOCK"] = File(store.home, "process.lock").absolutePath
                }.start().also { child = it }
                val reader = Thread({
                    try { RootServerController.consumeLines(process.inputStream) { line ->
                        log(line)
                        if (stage == "input" && line.startsWith("[client] INPUT_READY ")) inputReady = true
                    } } catch (failure: Exception) { log("[client] OUTPUT_CLOSED stage=$stage ${failure.javaClass.simpleName}: ${failure.message}") }
                }, "wurm-client-output").apply { isDaemon = true; start() }
                val writer = if (stage == "input") Thread({
                    try { process.outputStream.bufferedWriter().use { out ->
                        while (process.isAlive) { val event = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue; out.write(event); out.newLine(); out.flush() }
                    } } catch (_: Exception) { }
                }, "wurm-client-input").apply { isDaemon = true; start() } else null
                val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(if (stage == "input") 10 else 2)
                while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    checkCancelled()
                    if (System.nanoTime() > deadline) { log("[client] STAGE_TIMEOUT $stage; terminating only client child"); process.destroyForcibly(); break }
                }
                process.waitFor(); reader.join(3000); writer?.join(1000)
                results[stage] = process.exitValue(); log("[client] CHILD_EXIT stage=$stage code=${process.exitValue()}")
                child = null; inputReady = false
            }
            log("[client] GATE_RESULTS $results; Wurm login/world entry NOT verified; input sink=diagnostic")
            status(if (mode == "input") "Stopped" else "Blocked", if (mode == "input") "Input diagnostic ended." else "Client attempt finished: $results. Export Client Report for the startup/graphics result; login is not verified.")
        } finally {
            reapChild()
            session.listFiles().orEmpty().filter { it.name.startsWith("hs_err_pid") }.forEach { f -> f.useLines { it.take(120).forEach(::log) } }
            session.deleteRecursively()
        }
    }
}
