package io.github.russianranger.wurmlauncher

/** Stable, bounded settings protocol; order is checked against the JVM adapter. */
object GraphicsOptions {
    data class Option(val field: String, val label: String, val choices: List<String>, val minimum: Int = 0) {
        fun valid(value: Int) = value == -1 || value in minimum until minimum + choices.size
    }
    private val quality = listOf("Low", "Medium", "High")
    private val distance = listOf("Very Short", "Short", "Medium", "Far", "Extreme")
    private val onOff = listOf("Off", "On")
    val options = listOf(
        Option("waterDetail", "Water detail", quality),
        Option("reflections", "Reflections", listOf("Disabled", "Sky", "Sky & Terrain", "Sky, Terrain & Trees", "Almost Everything")),
        Option("treeRenderingDistance", "Tree distance", distance),
        Option("structureRenderingDistance", "Building distance", distance),
        Option("itemCreatureRenderingDistance", "Item and creature distance", distance),
        Option("prettyTrees", "Detailed tree models", onOff),
        Option("prettyWeather", "Weather particles", onOff),
        Option("renderSunGlare", "Sun glare", onOff),
        Option("caveDetail", "Cave detail", quality),
        Option("shadowLevel", "Shadows", listOf("Disabled", "Simple Objects", "Objects", "Objects & Structures", "Everything")),
        Option("shadowMapSize", "Shadow resolution", listOf("Small", "Medium", "Large", "Huge")),
        Option("lod", "Model detail distance", listOf("Short", "Normal", "Far")),
        Option("useBloom", "Bloom", onOff),
        Option("useVignette", "Vignette", onOff),
        Option("useFXAA", "Anti-aliasing (FXAA)", onOff),
        Option("limitDynamicLights", "Limit dynamic lights", onOff),
        Option("maxDynamicLights", "Maximum dynamic lights (when limited)", (1..16).map(Int::toString), 1)
    )
    val resolutions = listOf("800x480", "960x540", "1280x720")
    fun command(preset: String, values: List<Int>): String {
        require(preset in listOf("performance", "imported") && values.size == options.size)
        require(values.indices.all { options[it].valid(values[it]) })
        return preset + ":" + values.joinToString(",")
    }
}
