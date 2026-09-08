package io.github.russianranger.wurmlauncher

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Owns one native child at a time. No shell, su, global process search or PID files. */
class ManagedServerController(private val context: Context, private val config: ManagedLaunch) {
    @Volatile private var stopRequested = false
    @Volatile private var restartRequested = false
    @Volatile private var forced = false
    @Volatile private var child: Process? = null
    @Volatile private var pocReturned = false
    private var worldReport: ManagedWorldReport? = null
    @Volatile private var reportFailed = false
    private val workspace = ManagedSession.workspace(context)
    private fun log(line: String) = ManagedSession.log(line)
    private fun status(phase: String, message: String) = ManagedSession.status(phase, message)
    private fun report(action: (ManagedWorldReport) -> Unit) {
        if (reportFailed) return
        runCatching { worldReport?.let(action) }.onFailure {
            reportFailed = true
            log("[app] World report could not be updated: ${it.javaClass.simpleName}. Server control remains available.")
        }
    }

    @Synchronized fun requestStop(restart: Boolean) {
        restartRequested = restart
        stopRequested = true
        status("Stopping", if (restart) "Restart requested; waiting for Wurm shutdown." else "Stop requested.")
    }
    @Synchronized fun forceStop() {
        forced = true; restartRequested = false; stopRequested = true
        log("[app] Force Stop requested. World saving is not guaranteed; checkpoint retained.")
        child?.destroyForcibly()
    }
    private fun cancelled() { if (stopRequested) throw InterruptedException("Startup cancelled; no automatic restart.") }
    @Synchronized private fun beginRestart(cleanStop: Boolean): Boolean {
        if (!restartRequested || !cleanStop || forced) return false
        stopRequested = false; restartRequested = false; forced = false; pocReturned = false
        return true
    }

    fun run() {
        try {
            do {
                val cleanStop = runOnce()
                if (!beginRestart(cleanStop)) break
                log("[app] Previous child exited. Taking a new checkpoint before restart.")
            } while (true)
        } catch (failure: Exception) {
            report { it.state("Session failed: ${failure.javaClass.simpleName}; see session report") }
            throw failure
        } finally {
            // Never release session ownership while its child could still write a world.
            child?.let { process ->
                if (process.isAlive) process.destroyForcibly()
                while (process.isAlive) process.waitFor(1, TimeUnit.SECONDS)
            }
            child = null
        }
    }

    /** Same foreground ownership and native lock as Wurm; this child only reads runtime files. */
    fun audit(capture: Boolean) {
        var run: File? = null
        try {
            val runtime = workspace.exclusive {
                val imported = requireNotNull(workspace.imports.current()) { "Import your stopped runtime ZIP first." }
                config.validate(imported.worlds)
                check(!capture || !workspace.recoveryRequired.exists()) { "Export/recover the interrupted world before replacing its baseline." }
                if (!capture) check(File(workspace.auditDirectory, "baseline.bin").isFile) { "Capture a storage baseline first." }
                requireNotNull(workspace.working()) { "No working runtime exists." }
            }
            ProbeInputs.BASELINE.forEach { (name, expected) ->
                check(ProbeInputs.sha256(File(runtime, "poc-lib/$name")) == expected) { "SQLite input differs from the Thor baseline." }
            }
            val home = ProbeRuntime.install(context, ::log)
            cancelled()
            val native = File(context.applicationInfo.nativeLibraryDir)
            val session = File(context.filesDir, "session-${UUID.randomUUID()}")
            run = session
            check(session.mkdirs())
            val tmp = File(session, "tmp").apply { check(mkdirs()) }
            val helper = File(session, "runtime-probe.jar")
            context.assets.open("runtime-probe.jar").use { input -> helper.outputStream().use { input.copyTo(it) } }
            status("Checking storage", "Reading stopped runtime files and checking disposable database copies. Stop cancels this audit.")
            log("[audit] ${java.time.Instant.now()} — ${if (capture) "Capture baseline" else "Check stored data"}; world=${config.world}")
            val process = launch(config.auditArguments(native, home, tmp, runtime, helper, workspace.auditDirectory, capture), session, home, native, tmp)
            val complete = java.util.concurrent.atomic.AtomicBoolean(false)
            val expected = if (capture) "[audit] BASELINE_CAPTURED" else "[audit] STORAGE_CHECK_COMPLETE"
            val reader = read(process) { if (it == expected) complete.set(true) }
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15)
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                cancelled()
                check(System.nanoTime() < deadline) { "Storage audit timed out; source world was not opened by SQLite." }
            }
            reader.join(3000)
            child = null
            check(process.exitValue() == 0 && complete.get()) { "Storage audit failed (exit ${process.exitValue()}). Export storage and session reports." }
            status("Stopped", if (capture) "Storage baseline captured. Start/Stop, then Check stored data." else "Storage check complete. Open or export the storage report for results.")
        } catch (failure: Exception) {
            // Do not present an older successful report as this failed/cancelled attempt.
            child?.let { if (it.isAlive) it.destroyForcibly(); it.waitFor() }
            val old = workspace.storageReport().take(256 * 1024)
            workspace.auditDirectory.mkdirs()
            val pending = File(workspace.auditDirectory, "controller-report.pending")
            pending.writeText("Latest audit did not complete at ${java.time.Instant.now()}: ${failure.message}\n\nLast available audit report (may be from this attempt):\n$old")
            java.nio.file.Files.move(pending.toPath(), workspace.auditReport.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            throw failure
        } finally {
            child?.let { if (it.isAlive) it.destroyForcibly(); it.waitFor() }; child = null
            run?.deleteRecursively()
        }
    }

    private fun runOnce(): Boolean {
        status("Preparing", "Validating import and creating a recoverable working copy.")
        val runtime = workspace.exclusive {
            check(!workspace.recoveryRequired.exists()) {
                "Previous Wurm exit did not confirm shutdown. Export the working copy/report, then restore the checkpoint or original before starting again."
            }
            val imported = requireNotNull(workspace.imports.current()) { "Import your prepared server ZIP first." }
            config.validate(imported.worlds)
            cancelled()
            workspace.ensureWorking { cancelled(); status("Preparing", it); log("[app] $it") }
        }
        reportFailed = false
        worldReport = ManagedWorldReport(workspace.worldReport, runtime, config)
        report { it.prepare() }
        // Pin the exact device-proven inputs. In particular, never replace the
        // owner's patched server/common JARs with unpatched desktop files.
        val pins = linkedMapOf(
            "server.jar" to "9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06",
            "common.jar" to "066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633",
            "wurm-arm64-poc.jar" to ManagedRuntimeStore.POC_SHA256
        ) + ProbeInputs.BASELINE.mapKeys { "poc-lib/${it.key}" }
        pins.forEach { (path, expected) ->
            cancelled()
            check(ProbeInputs.sha256(File(runtime, path)) == expected) { "$path differs from the Thor baseline; startup blocked." }
            log("[input] $expected  $path")
        }
        val home = ProbeRuntime.install(context, ::log)
        cancelled()
        val native = File(context.applicationInfo.nativeLibraryDir)
        val run = File(context.filesDir, "session-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val tmp = File(run, "tmp").apply { check(mkdirs()) }
        val helper = File(run, "runtime-probe.jar")
        context.assets.open("runtime-probe.jar").use { input -> helper.outputStream().use { input.copyTo(it) } }
        log("[app] World=${config.world}; heap=${config.heapMiB} MiB; expected TCP=${config.port}")
        log("[app] UID=${android.os.Process.myUid()}; runtime is an independent working copy.")
        try {
            status("Preflight", "Testing packaged Java, SQLite and loopback networking before Wurm opens this world.")
            val markers = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            val preflight = launch(config.arguments(native, home, tmp, runtime, helper, true), run, home, native, tmp)
            val reader = read(preflight) { line ->
                when (line) {
                    "[probe] JAVA_OK" -> markers.add("JAVA_OK")
                    "[probe] SQLITE_OK: create/insert/update/commit/close/reopen" -> markers.add("SQLITE_OK")
                    "[probe] PROBE_OK" -> markers.add("PROBE_OK")
                    "[network] NETWORK_OK: localhost resolution and TCP loopback exchange" -> markers.add("NETWORK_OK")
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (!preflight.waitFor(200, TimeUnit.MILLISECONDS)) {
                cancelled()
                check(System.nanoTime() < deadline) { "Java/SQLite/network preflight timed out; world not opened." }
            }
            reader.join(3000)
            check(preflight.exitValue() == 0 && markers.containsAll(listOf("JAVA_OK", "SQLITE_OK", "NETWORK_OK", "PROBE_OK"))) {
                "Java/SQLite/network preflight failed (exit ${preflight.exitValue()}); world not opened. Export session report."
            }
            child = null
            cancelled()
            log("[app] PREFLIGHT_PASS")
            check(!portOpen()) { "TCP ${config.port} is already occupied. Stop the Termux/other server first." }
            workspace.exclusive { workspace.saveCheckpoint { cancelled(); status("Preparing", it); log("[app] $it") } }
            cancelled()
            status("Starting", "Wurm process starting; waiting for POC initialization and TCP ${config.port}.")
            workspace.recoveryRequired.writeText("Wurm may have opened this working world. Clear only after requested normal exit or successful restore.\n")
            report { it.state("Starting; waiting for initialization and TCP") }
            val server = launch(config.arguments(native, home, tmp, runtime, helper, false), runtime, home, native, tmp)
            val serverReader = read(server) { line ->
                if (line == "[WurmARM64] runServer() returned.") pocReturned = true
                report { it.observe(line) }
            }
            var wasReady = false
            var sentStop = false
            var cancelledStartup = false
            var stopTime = 0L
            while (!server.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (stopRequested && !sentStop) {
                    sentStop = true; stopTime = System.nanoTime()
                    report { it.state("Stop requested; waiting for child exit") }
                    if (wasReady && !forced) {
                        log("[app] Asking Wurm Server.shutDown() to save and stop.")
                        runCatching { server.outputStream.write("STOP\n".toByteArray()); server.outputStream.flush() }
                            .onFailure { log("[app] Could not send shutdown request: $it. Force Stop is available.") }
                    } else {
                        cancelledStartup = true; restartRequested = false
                        log("[app] Cancelling incomplete startup with SIGTERM; save is unverified. Checkpoint retained.")
                        server.destroy()
                    }
                }
                if (!stopRequested) {
                    val reachable = portOpen()
                    if (pocReturned && reachable) {
                        if (!wasReady) {
                            log("[app] TCP_READY port=${config.port}; POC returned. Wurm protocol/playability not yet tested.")
                            report {
                                it.event("LOOPBACK_READY 127.0.0.1:${config.port}; POC returned")
                                it.state("Running observed; requesting child file/listener snapshot")
                                it.event("INSPECT_REQUESTED")
                            }
                            runCatching { server.outputStream.write("INSPECT\n".toByteArray()); server.outputStream.flush() }
                                .onFailure { failure -> report { it.event("INSPECT_REQUEST_FAILED ${failure.javaClass.simpleName}") } }
                        }
                        wasReady = true
                        status("Running", "Process alive · TCP ${config.port} reachable · ${config.world}")
                    } else if (wasReady) status("Running", "Process alive; TCP ${config.port} is not currently reachable.")
                } else if (sentStop && System.nanoTime() - stopTime > TimeUnit.SECONDS.toNanos(60)) {
                    status("Stopping", "Still waiting for exit. Export logs; Force Stop is available if needed.")
                }
            }
            serverReader.join(3000)
            val exit = server.exitValue()
            child = null
            log("[app] SERVER_EXIT=$exit; stopRequested=$stopRequested; force=$forced; startupCancelled=$cancelledStartup")
            val clean = stopRequested && exit == 0 && !forced && !cancelledStartup
            report { it.state("Child exited $exit; requested=$stopRequested; normalStop=$clean; forced=$forced; startupCancelled=$cancelledStartup") }
            if (clean) workspace.recoveryRequired.delete()
            status(if (stopRequested) "Stopped" else "Error",
                "Child exited ($exit). " + if (clean) "Shutdown requested; verify world persistence on device." else "Save not confirmed; original and before-start checkpoint retained.")
            return clean
        } finally {
            // Preserve crash evidence in the report, then remove only disposable helper/tmp data.
            child?.let { process -> if (process.isAlive) process.destroyForcibly(); process.waitFor() }
            child = null
            run.listFiles().orEmpty().filter { it.name.startsWith("hs_err_pid") }.forEach { error ->
                log("[app] JVM crash report ${error.name}")
                error.useLines { it.take(100).forEach(::log) }
            }
            run.deleteRecursively()
        }
    }

    private fun launch(args: List<String>, directory: File, home: File, native: File, tmp: File): Process {
        cancelled()
        return ProcessBuilder(args).directory(directory).redirectErrorStream(true).apply {
            environment().clear()
            environment().putAll(ProbeEnvironment.create(home, native, tmp))
            environment()["WURM_WORLD_LOCK"] = workspace.lockFile.absolutePath
            environment()["WURM_HEAP_TAGGING"] = "off"
        }.start().also { child = it }
    }

    private fun read(process: Process, observe: (String) -> Unit): Thread = Thread({
        runCatching { RootServerController.consumeLines(process.inputStream) { observe(it); log(it) } }
            .onFailure { log("[app] Output stream closed: ${it.message}") }
    }, "wurm-managed-output").apply { isDaemon = true; start() }

    private fun portOpen(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", config.port), 200) }
        true
    }.getOrDefault(false)
}
