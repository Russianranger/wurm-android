package io.github.russianranger.wurmlauncher

import android.content.Context
import java.io.File
import java.time.Instant

/** One process-local owner for import/export and foreground server operations. */
object ManagedSession {
    data class Snapshot(val busy: Boolean, val phase: String, val detail: String, val log: String)
    private var busy = false
    private var phase = "Stopped"
    private var detail = "No server process owned by this app."
    private val lines = ArrayDeque<String>()
    private var logFile: File? = null
    private var observations: RuntimeObservationLog? = null
    private var controller: ManagedServerController? = null

    fun workspace(context: Context) = ManagedWorkspace(File(context.filesDir, "managed-preview"))
    @Synchronized fun snapshot(includeLog: Boolean = true) = Snapshot(busy, phase, detail, if (includeLog) lines.joinToString("\n") else "")
    @Synchronized fun status(next: String, message: String) { phase = next; detail = message }
    @Synchronized fun log(line: String) {
        val bounded = line.take(4000)
        lines.addLast(bounded)
        while (lines.size > 500) lines.removeFirst()
        runCatching { observations?.observe(bounded) }
        runCatching {
            logFile?.let { file ->
                if (file.length() > 1024 * 1024) file.writeText(lines.joinToString("\n") + "\n")
                else file.appendText(bounded + "\n")
            }
        }
    }
    @Synchronized private fun claim(context: Context, next: String): Boolean {
        if (busy) return false
        busy = true; phase = next; detail = next
        if (logFile == null) {
            logFile = File(context.filesDir, "managed-session.txt")
            observations = RuntimeObservationLog(File(context.filesDir, "server-runtime-observations.txt"))
            logFile?.takeIf { it.isFile }?.readLines()?.takeLast(100)?.forEach { lines.addLast(it.take(4000)) }
        }
        log("\n[app] ${Instant.now()} — $next")
        return true
    }

    fun mutate(context: Context, title: String, action: (ManagedWorkspace) -> Unit): Boolean {
        val app = context.applicationContext
        if (!claim(app, title)) return false
        Thread({
            try {
                val workspace = workspace(app)
                workspace.exclusive { action(workspace) }
                status("Stopped", "$title complete.")
                log("[app] $title complete.")
            } catch (failure: Exception) {
                status("Error", failure.message ?: title)
                log("[app] $title failed: $failure")
            } finally { synchronized(this) { busy = false } }
        }, "wurm-storage").start()
        return true
    }

    fun start(context: Context, config: ManagedLaunch, auditCapture: Boolean? = null, finished: () -> Unit): Boolean {
        if (!claim(context, if (auditCapture == null) "Preparing" else "Checking storage")) return false
        val owner = ManagedServerController(context.applicationContext, config)
        synchronized(this) { controller = owner }
        Thread({
            try { if (auditCapture == null) owner.run() else owner.audit(auditCapture) }
            catch (failure: Exception) {
                status(if (auditCapture != null && failure is InterruptedException) "Stopped" else "Error",
                    if (auditCapture != null && failure is InterruptedException) "Storage audit cancelled. No server was started." else failure.message ?: "Operation failed")
                log("[app] ${if (auditCapture == null) "Server launch" else "Storage audit"} failed: $failure")
            } finally {
                synchronized(this) { controller = null; busy = false }
                finished()
            }
        }, "wurm-managed-server").start()
        return true
    }

    @Synchronized fun stop(restart: Boolean = false) { controller?.requestStop(restart) }
    @Synchronized fun forceStop() { controller?.forceStop() }
    @Synchronized fun ownsServer() = controller != null
    @Synchronized fun requestDiagnostics() { controller?.requestDiagnostics() }
    fun report(context: Context): String {
        val state = snapshot()
        val saved = File(context.filesDir, "managed-session.txt")
        val firstErrors = File(context.filesDir, "managed-first-errors.txt")
        val evidence = runCatching { firstErrors.readText().take(64 * 1024) }.getOrDefault("No separate first-error capture available.")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return "Wurm Server $version\nPackage: ${context.packageName}\nExported: ${Instant.now()}\nAndroid ${android.os.Build.VERSION.RELEASE}; API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Status: ${state.phase} — ${state.detail}\nFile persistence passed on Thor 0.5.0; specific gameplay saves remain unverified.\n\n" +
            "Retained first errors (separate from rotating console):\n$evidence\n\n" +
            "Runtime observations (history; entries may also appear in console; compare timestamps/PIDs):\n" +
            runCatching { (observations ?: RuntimeObservationLog(File(context.filesDir, "server-runtime-observations.txt"))).read() }.getOrDefault("Unavailable\n") +
            "\nServer mod manifest:\n" + runCatching { workspace(context).working()?.let { ModStore(it,"server").report() } ?: "No runtime" }.getOrElse { "Unavailable: ${it.message}" } +
            "\nRecent session console:\n" +
            (if (saved.isFile) saved.readText() else state.log)
    }
}
