package io.github.russianranger.wurmlauncher

/** Monotonic deadlines; a late tick never creates catch-up connection bursts. */
class ServerProbeSchedule {
    private var lastProbe: Long? = null
    private var intervalNanos = 500_000_000L

    fun due(now: Long): Boolean {
        val previous = lastProbe
        if (previous != null && now - previous < intervalNanos) return false
        lastProbe = now
        return true
    }

    // Keep readiness history even after a failed probe; child exit is watched separately.
    fun ready() { intervalNanos = 15_000_000_000L }
}
