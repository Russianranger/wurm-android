package io.github.russianranger.wurmlauncher

enum class Phase { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class ServerSnapshot(
    val phase: Phase,
    val detail: String,
    val log: String,
    val revision: Long
)

/** Process-local state survives Activity recreation. Never persist a possibly stale RUNNING flag. */
object ServerState {
    private var phase = Phase.STOPPED
    private var detail = "No server managed by this launcher."
    private val lines = ArrayDeque<String>()
    private var revision = 0L

    @Synchronized fun status(next: Phase, message: String) {
        phase = next
        detail = message
        revision++
    }

    @Synchronized fun append(line: String) {
        lines.addLast(line.take(4096))
        while (lines.size > 500) lines.removeFirst()
        revision++
    }

    @Synchronized fun snapshot() = ServerSnapshot(phase, detail, lines.joinToString("\n"), revision)
}
