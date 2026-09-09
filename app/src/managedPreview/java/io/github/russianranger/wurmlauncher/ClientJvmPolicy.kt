package io.github.russianranger.wurmlauncher

/** Client-only compatibility experiment. Server launch arguments never use this policy. */
object ClientJvmPolicy {
    fun arguments(stage: String): List<String> {
        val collector = when (stage) {
            "entry", "memory-serial" -> "serial"
            "memory-g1" -> "g1"
            else -> return emptyList()
        }
        return listOf(if (collector == "serial") "-XX:+UseSerialGC" else "-XX:+UseG1GC",
            "-Dwurm.client.expectedGc=$collector", "-Xlog:gc=info,gc+init=info,safepoint=info:stdout:utctime,pid,tid,tags")
    }
    fun stages(mode: String): List<String> = when (mode) {
        "memory" -> listOf("memory-g1", "memory-serial")
        "input" -> listOf("input")
        "render" -> listOf("render")
        "window" -> listOf("window")
        "start", "local" -> listOf("inventory", "compat", "prepare-graphics", "entry")
        else -> throw IllegalArgumentException("Unknown client mode")
    }
}
