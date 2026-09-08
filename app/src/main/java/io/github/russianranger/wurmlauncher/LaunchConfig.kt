package io.github.russianranger.wurmlauncher

data class LaunchConfig(val runtimePath: String, val javaPath: String) {
    fun validate(): String? = when {
        !validPath(runtimePath) -> "Runtime must be an absolute path without control characters."
        runtimePath == "/" -> "Choose the Wurm runtime directory, not /."
        !validPath(javaPath) -> "Java must be an absolute executable path without control characters."
        else -> null
    }

    companion object {
        const val DEFAULT_RUNTIME = "/data/data/com.termux/files/home/wurm-arm64-poc/runtime"
        const val DEFAULT_JAVA = "/data/data/com.termux/files/usr/bin/java"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        fun validPath(value: String) = value.startsWith("/") && value.none { it.isISOControl() }
        fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    }

    fun command(script: String, token: String): String {
        require(validate() == null)
        return listOf(
            "${TERMUX_PREFIX}/bin/bash", "--noprofile", "--norc", "-c", script,
            "wurm-launcher", runtimePath, javaPath, token
        ).joinToString(" ") { quote(it) }
    }
}
