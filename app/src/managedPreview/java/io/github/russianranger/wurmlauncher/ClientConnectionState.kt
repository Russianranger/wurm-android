package io.github.russianranger.wurmlauncher

/** Current child only. Splash/TCP/auth success alone never removes the startup deadline. */
class ClientConnectionState {
    data class Update(val phase: String, val detail: String)
    @Volatile var latest: Update? = null
        private set
    @Volatile var gameLoopReached = false
        private set
    fun observe(line: String): Update? {
        if (line.startsWith("[connection] MONITOR_UNAVAILABLE "))
            return Update("Connection details unavailable", line.removePrefix("[connection] MONITOR_UNAVAILABLE ").take(500)).also { latest = it }
        val match = Regex("^\\[connection] STATE phase=([A-Z_]+) elapsedMs=([0-9]+) (.*)$").matchEntire(line) ?: return null
        val phase = match.groupValues[1]
        val label = when (phase) {
            "INITIALIZING" -> "Starting client"
            "SOCKET_CONNECTING" -> "Connecting to local server"
            "AUTH_WAIT" -> "Waiting for local authentication"
            "AUTH_DENIED" -> "Authentication rejected"
            "LOGIN_WAIT" -> "Waiting for login response"
            "LOGIN_ACCEPTED" -> "Login accepted; preparing world"
            "LOGIN_DENIED" -> "Login rejected"
            "RETRY_WAIT" -> "Waiting to retry connection"
            "DISCONNECTED" -> "Client disconnected"
            "GAME_LOOP" -> "Client game loop"
            else -> return null
        }
        val detail = match.groupValues[3]
        if (phase == "GAME_LOOP") {
            if (!detail.startsWith("connecting=false authenticated=true loggedIn=true splash=false disconnected=false transport=true ")) return null
            gameLoopReached = true
        }
        val payload = " " + detail
        fun message(name: String, next: String): String = payload.substringAfter(" $name=", "").substringBefore(" $next=").take(240)
        val startup = message("startup", "authMessage")
        val auth = message("authMessage", "loginMessage")
        val login = message("loginMessage", "disconnectMessage")
        val disconnect = message("disconnectMessage", "end")
        val reason = when (phase) {
            "AUTH_DENIED" -> auth
            "LOGIN_DENIED", "LOGIN_ACCEPTED" -> login
            "DISCONNECTED" -> disconnect
            "GAME_LOOP" -> "Client reports login accepted and startup screen closed. Confirm the visible world; Stop Client ends this session."
            else -> startup
        }.ifBlank { label }
        return Update(label, "${match.groupValues[2].toLongOrNull()?.div(1000) ?: 0}s: $reason").also { latest = it }
    }
    fun timedOut(stage: String, elapsedNanos: Long): Boolean {
        if (stage == "entry" && gameLoopReached) return false
        val minutes = when (stage) { "input" -> 10; "entry" -> 5; else -> 2 }
        return elapsedNanos >= java.util.concurrent.TimeUnit.MINUTES.toNanos(minutes.toLong())
    }
}
