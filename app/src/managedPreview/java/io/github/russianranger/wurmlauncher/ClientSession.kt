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
    fun graphicsFrame(context: Context) = File(context.filesDir, "client-graphics-frame.bin")
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
        return "Wurm client milestone 0.10.5\nAndroid ${android.os.Build.VERSION.RELEASE}; API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Status: ${state.phase} — ${state.detail}\nDefault target: 127.0.0.1:3724\n" +
            "Gate status: 0.10.0 window/controller diagnostic passed on Thor; 0.10.1 profile/resources passed on Thor; 0.10.2 font rasterization passed on Thor; 0.10.3 created the Wurm window on Thor; 0.10.4 passed measured FBO support on Thor; Java 17 buffer cleanup adapter awaits Thor test. Audio currently falls back to silent mode. Full Wurm rendering, server ticket acceptance and login are not qualified.\n\n" +
            (installed?.inventory ?: "No accepted client import.\n") + "\nController profile:\n" +
            profileFile(context).takeIf { it.isFile }?.readText().orEmpty() + "\nGraphics runtime:\n" +
            runCatching { context.assets.open("client-graphics.json").bufferedReader().use { it.readText() } }.getOrElse { "Unavailable: ${it.message}" } +
            "\nLast graphics frame:\n" + runCatching { graphicsFrame(context).takeIf { it.isFile }?.let {
                val frame = GraphicsFrame.read(it)
                "sequence=${frame.sequence} size=${frame.width}x${frame.height} sha256=${ProbeInputs.sha256(it)}; retained frame, not a new run\n"
            } ?: "No frame\n" }.getOrElse { "Frame invalid: ${it.message}\n" } + "\nSession history:\n" +
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
        require(mode in listOf("start", "local", "input", "render", "window"))
        val installed = if (mode in listOf("input", "render", "window")) null else requireNotNull(store.current()) { "Import the complete client ZIP first" }
        if (mode != "input") {
            graphicsFrame(context).delete()
            File(graphicsFrame(context).path + ".pending").delete()
        }
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
        val overlay = File(session, "client-graphics-overlay.jar")
        context.assets.open("client-compat.jar").use { input -> compat.outputStream().use { input.copyTo(it) } }
        try {
            val graphics = if (mode != "input") GraphicsRuntime.prepare(context, session, ::log).map { it.absolutePath } else emptyList()
            val cp = listOf(helper.absolutePath) + installed?.jars.orEmpty().map { File(installed!!.root, it).absolutePath }
            log("[client] Runtime root=${installed?.root}; no server JARs, server Steam shim or JavaFX launcher added")
            log("[client] CLIENT_JVM_MODE exec; ${if (mode == "render") "LWJGL/GL4ES pbuffer test with frame readback" else "Pojav Java GLFW / owned EGL window adapter installed for entry stage"}")
            val stages = when (mode) { "input" -> listOf("input"); "render" -> listOf("render"); "window" -> listOf("window"); else -> listOf("inventory", "compat", "prepare-graphics", "entry") }
            val player = context.getSharedPreferences("client-settings", Context.MODE_PRIVATE).getString("player", "Thor") ?: "Thor"
            require(player.matches(Regex("[A-Za-z][A-Za-z0-9]{2,19}"))) { "Save a valid local player name" }
            val results = linkedMapOf<String, Int>()
            val graphicsFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            for (stage in stages) {
                checkCancelled(); queue.clear(); inputReady = false
                status(when (stage) { "input" -> "Input diagnostic"; "render" -> "Graphics diagnostic"; "window" -> "Window/input test"; else -> "Starting client" }, "Bootstrap stage: $stage")
                val window = stage in listOf("window", "entry")
                val stageCp = when {
                    window -> listOf(graphics.last()) + graphics.dropLast(1) + listOf(compat.absolutePath) +
                        (if (stage == "entry") listOf(overlay.absolutePath) else emptyList()) + cp
                    stage == "render" -> graphics.dropLast(1)
                    stage == "compat" -> listOf(compat.absolutePath) + cp
                    else -> cp
                }
                log("[client] CLASSPATH stage=$stage ${stageCp.joinToString(":")}")
                val args = listOf(File(native, "libwurmjvm_runner.so").absolutePath, "-Xms32m", "-Xmx1024m",
                    // The packaged JRE is built --enable-headless-only=yes. AWT X11 is unavailable;
                    // native LWJGL window creation is still attempted independently below.
                    "-Djava.home=$home", "-Djava.io.tmpdir=$tmp", "-Duser.home=$user", "-Djava.awt.headless=true",
                    "-Djava.library.path=$home/lib:$home/lib/server:$native", "-Dsun.boot.library.path=$home/lib:$native",
                    "-XX:ErrorFile=$session/hs_err_pid%p.log", "-XX:-CreateCoredumpOnCrash",
                    "-Dwurm.client.host=127.0.0.1", "-Dwurm.client.port=3724", "-Dwurm.client.offline=true", "-Dwurm.client.player=$player",
                    "-cp", stageCp.joinToString(":")) + (if (stage in listOf("prepare-graphics", "entry")) listOf(
                        "-Dwurm.client.offscreenOverlay=$overlay"
                    ) else emptyList()) + (if (stage == "entry") listOf(
                        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
                        "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
                        "-Dwurm.client.fontDir=/system/fonts", "-Dwurm.client.fontConfig=$session/fontconfig.properties"
                    ) else emptyList()) + if (stage == "render" || window) listOf(
                        "-Dorg.lwjgl.librarypath=$native", "-Dorg.lwjgl.opengl.explicitInit=true", "-Dorg.lwjgl.util.Debug=true",
                        "-Dorg.lwjgl.system.bundledLibrary.nameMapper=wurm.graphics.LibraryNames",
                        "-Dorg.lwjgl.system.allocator=system",
                        "-Dwurm.graphics.library=${File(native, "libgl4es.so")}", "-Dwurm.graphics.frame=${graphicsFrame(context)}"
                    ) + when(stage) {
                        "render" -> listOf("wurm.graphics.GraphicsProbe", File(native,"libgl4es.so").absolutePath, graphicsFrame(context).absolutePath)
                        "window" -> listOf("wurm.graphics.WindowProbe")
                        else -> listOf("client.ClientBootstrap", stage)
                    } else listOf("client.ClientBootstrap", stage)
                val process = ProcessBuilder(args).directory(installed?.root ?: session).redirectErrorStream(true).apply {
                    environment().clear(); environment().putAll(ProbeEnvironment.create(home, native, tmp))
                    environment()["WURM_HEAP_TAGGING"] = "off"
                    environment()["WURM_WORLD_LOCK"] = File(store.home, "process.lock").absolutePath
                    if (stage == "render" || window) {
                        environment()["LIBGL_ES"] = "2"; environment()["LIBGL_GL"] = "21"
                        environment()["LIBGL_GLES"] = "libGLESv2.so"; environment()["LIBGL_EGL"] = "libEGL.so"
                        environment()["LIBGL_NOPSA"] = "1"
                        log("[graphics] SHADER_CACHE disabled LIBGL_NOPSA=1; compile shaders per attempt to avoid cached-program failures")
                    }
                }.start().also { child = it }
                val graphicsPassed = java.util.concurrent.atomic.AtomicBoolean(false)
                val reader = Thread({
                    try { RootServerController.consumeLines(process.inputStream) { line ->
                        log(line)
                        if ((stage == "input" || window) && line.startsWith("[client] INPUT_READY ")) inputReady = true
                        if (stage == "window" && line.startsWith("[window] WINDOW_PROBE_PASS")) graphicsPassed.set(true)
                        if (stage == "render" && line == "[graphics] GRAPHICS_PROBE_EXIT code=0") graphicsPassed.set(true)
                        if ((window || stage == "prepare-graphics") && (line.startsWith("[window] WINDOW_PROBE_FAIL ") || line.startsWith("[client] BOOTSTRAP_FAILED ")))
                            graphicsFailure.compareAndSet(null, line.take(400))
                        if (stage == "render" && line.startsWith("[graphics] GRAPHICS_PROBE_FAIL "))
                            graphicsFailure.compareAndSet(null, line.removePrefix("[graphics] GRAPHICS_PROBE_FAIL ").take(400))
                    } } catch (failure: Exception) { log("[client] OUTPUT_CLOSED stage=$stage ${failure.javaClass.simpleName}: ${failure.message}") }
                }, "wurm-client-output").apply { isDaemon = true; start() }
                val writer = if (stage == "input" || window) Thread({
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
                results[stage] = if (stage in listOf("render", "window") && process.exitValue() == 0 && !graphicsPassed.get()) 42 else process.exitValue()
                log("[client] CHILD_EXIT stage=$stage code=${process.exitValue()} accepted=${results[stage]}")
                child = null; inputReady = false
                if (stage == "prepare-graphics" && results[stage] != 0) {
                    log("[client] ENTRY_NOT_STARTED graphics compatibility preparation failed; import was not modified")
                    break
                }
            }
            log("[client] GATE_RESULTS $results; Wurm login/world entry NOT verified; input sink=${if (mode == "input") "diagnostic" else "lwjgl2-queues when WINDOW_READY"}")
            if (mode == "render") status(if (results["render"] == 0) "Graphics test passed" else "Graphics test failed",
                "LWJGL/GL4ES result=$results. ${graphicsFailure.get()?.let { "$it. " } ?: ""}Export Client Report. Wurm window/login are not tested.")
            else if (mode == "window") status(if (results["window"] == 0) "Window test passed" else "Window test failed", "Result=$results. ${graphicsFailure.get().orEmpty()} Export Client Report; Wurm login was not tested.")
            else status(if (mode == "input") "Stopped" else "Blocked", if (mode == "input") "Input diagnostic ended." else "Client attempt finished: $results. ${graphicsFailure.get().orEmpty()} Export Client Report for the startup/graphics result; login is not verified.")
        } finally {
            reapChild()
            session.listFiles().orEmpty().filter { it.name.startsWith("hs_err_pid") }.forEach { f -> f.useLines { it.take(120).forEach(::log) } }
            session.deleteRecursively()
        }
    }
}
