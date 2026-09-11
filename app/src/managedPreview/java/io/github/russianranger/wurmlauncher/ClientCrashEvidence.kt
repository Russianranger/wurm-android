package io.github.russianranger.wurmlauncher

/** Per-attempt evidence only. An exit code alone does not prove a particular signal. */
class ClientCrashEvidence(val uid: Int, val startedAt: Long) {
    @Volatile var pid: Int = 0
        private set
    private val pending = linkedMapOf<Long, String>()
    private var sanitizer: String? = null
    private var last = "No graphics trace received"
    /** Android 13 UNIXProcess.toString keeps the PID even after a pre-main crash.
     * Use the Process returned by our own start(); no hidden API reflection. */
    @Synchronized fun observeProcess(description: String) {
        val identity = Regex("^Process\\[pid=(\\d+)(?:, hasExited=false| ,hasExited=true, exitcode=-?\\d+)\\]$")
            .matchEntire(description) ?: return
        identity.groupValues[1].toIntOrNull()?.takeIf { it > 0 && pid == 0 }?.let { pid = it }
    }
    @Synchronized fun observe(line: String) {
        val identity = Regex("^\\[native] uid=(\\d+) euid=(\\d+) pid=(\\d+);.*").matchEntire(line)
        if (identity != null && identity.groupValues[1].toIntOrNull() == uid && identity.groupValues[2].toIntOrNull() == uid)
            identity.groupValues[3].toIntOrNull()?.takeIf { it > 0 && pid == 0 }?.let { pid = it }
        if (line.contains("ERROR: AddressSanitizer:")) sanitizer = line.substringAfter("ERROR: AddressSanitizer:").trim().take(220)
        if (!line.startsWith("[graphics-trace] ")) return
        last = line.take(400)
        val event = Regex("^\\[graphics-trace] (BEGIN|END|THREW) seq=(\\d+)(?: .*)?$").matchEntire(line) ?: return
        val id = event.groupValues[2].toLongOrNull() ?: return
        if (event.groupValues[1] == "BEGIN") {
            if (pending.size >= 16) pending.remove(pending.keys.first())
            pending[id] = last
        } else pending.remove(id)
    }
    @Synchronized fun summary(exit: Int): String {
        sanitizer?.let { return "Client child exited $exit; AddressSanitizer: $it; export the full client report" }
        val call = pending.values.lastOrNull()
        return "Client child exited $exit" + (if (exit == 134) " (possible SIGABRT; crash evidence required)" else "") +
            "; " + (if (call != null) "unfinished graphics call: $call" else "last graphics event: $last")
    }
}
