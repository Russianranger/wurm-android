package io.github.russianranger.wurmlauncher

/** One attempt only. An external listener never becomes this app's owned server. */
class ClientServerWatch {
    private var observedOwner = false
    private var lastDiagnostic = Long.MIN_VALUE
    fun observe(owned: Boolean, busy: Boolean, phase: String, detail: String): String? {
        if (owned) observedOwner = true
        if (observedOwner && !busy && phase in listOf("Error", "Stopped"))
            return "Local server stopped: ${detail.take(500)}. Export Client Report and Server Session Report."
        return null
    }
    fun requestDiagnostic(owned: Boolean, phase: String?, elapsedSeconds: Long): Boolean {
        if (!owned || phase !in listOf("Waiting for local authentication", "Waiting for login response", "Waiting to retry connection") || elapsedSeconds < 10) return false
        if (lastDiagnostic != Long.MIN_VALUE && elapsedSeconds - lastDiagnostic < 15) return false
        lastDiagnostic = elapsedSeconds
        return true
    }
}
