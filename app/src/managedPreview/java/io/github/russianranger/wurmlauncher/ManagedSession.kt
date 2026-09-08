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
    private var controller: ManagedServerController? = null

    fun workspace(context: Context) = ManagedWorkspace(File(context.filesDir, "managed-preview"))
    @Synchronized fun snapshot() = Snapshot(busy, phase, detail, lines.joinToString("\n"))
    @Synchronized fun status(next: String, message: String) { phase = next; detail = message }
    @Synchronized fun log(line: String) {
        val bounded = line.take(4000)
        lines.addLast(bounded)
        while (lines.size > 500) lines.removeFirst()
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

    fun start(context: Context, config: ManagedLaunch, finished: () -> Unit): Boolean {
        if (!claim(context, "Preparing")) return false
        val owner = ManagedServerController(context.applicationContext, config)
        synchronized(this) { controller = owner }
        Thread({
            try { owner.run() }
            catch (failure: Exception) {
                status("Error", failure.message ?: "Server launch failed")
                log("[app] Server launch failed: $failure")
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
    fun report(context: Context): String {
        val state = snapshot()
        val saved = File(context.filesDir, "managed-session.txt")
        return "Wurm Server managed preview 0.4.1\nAndroid ${android.os.Build.VERSION.RELEASE}; API ${android.os.Build.VERSION.SDK_INT}\n" +
            "Status: ${state.phase} — ${state.detail}\nWorld saving is not yet physically qualified.\n\n" +
            if (saved.isFile) saved.readText() else state.log
    }
}
