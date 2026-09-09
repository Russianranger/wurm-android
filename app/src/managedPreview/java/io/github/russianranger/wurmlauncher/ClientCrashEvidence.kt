package io.github.russianranger.wurmlauncher

/** Per-attempt evidence only. An exit code alone does not prove a particular signal. */
class ClientCrashEvidence(val uid: Int, val startedAt: Long) {
    @Volatile var pid: Int = 0
        private set
    private val pending = linkedMapOf<Long, String>()
    private var last = "No graphics trace received"
    @Synchronized fun observe(line: String) {
        val identity = Regex("^\\[native] uid=(\\d+) euid=(\\d+) pid=(\\d+);.*").matchEntire(line)
        if (identity != null && identity.groupValues[1].toIntOrNull() == uid && identity.groupValues[2].toIntOrNull() == uid)
            identity.groupValues[3].toIntOrNull()?.takeIf { it > 0 }?.let { pid = it }
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
        val call = pending.values.lastOrNull()
        return "Client child exited $exit" + (if (exit == 134) " (possible SIGABRT; crash evidence required)" else "") +
            "; " + (if (call != null) "unfinished graphics call: $call" else "last graphics event: $last")
    }
}
