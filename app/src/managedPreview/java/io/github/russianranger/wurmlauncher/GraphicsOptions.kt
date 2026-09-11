package io.github.russianranger.wurmlauncher

/** Stable, bounded settings protocol; order is checked against the JVM adapter. */
object GraphicsOptions {
    data class Option(val field: String, val label: String, val choices: List<String>, val minimum: Int = 0, val restart: Boolean = false) {
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
        Option("maxDynamicLights", "Maximum dynamic lights (when limited)", (1..16).map(Int::toString), 1),
        Option("anisotropicFilteringLevel", "Texture filtering", listOf("1","2","4","8","16"), 0, true),
        Option("terrainDetail", "Terrain detail", listOf("Low","Medium","High"), 0, true),
        Option("normalMapping", "Normal maps", onOff, 0, true),
        Option("enableFontSmoothing", "Game font smoothing", listOf("Off","Dynamic","On"), 0, true),
        Option("modelLoaderThreadCount", "Model loading threads", listOf("1","2","3","4","8"), 0, true),
        Option("maxTextureSize", "Maximum texture quality", listOf("Low","Medium","High","Very High"), 0, true),
        Option("playerTextureSize", "Player texture size", listOf("256","512","1024","2048"), 0, true),
        Option("reflectionTextureSize", "Reflection texture quality", listOf("Low","Medium","High"), 0, false),
        Option("offscreenTextureSize", "Offscreen texture quality", listOf("Low","Medium","High","Very High"), 0, false),
        Option("megaTextureSize", "Terrain texture size", listOf("256","512","1024","2048","4096","8192","No Limit"), 0, true),
        Option("textureScalingHint", "Texture scaling filter", listOf("Nearest Neighbour (Fastest)","Bilinear","Bicubic (Nicest)"), 0, true),
        Option("selfAnimationplayback", "Own character animations", listOf("All","Walking Only","None"), 0, false),
        Option("colladaAnimations", "Model animation detail", listOf("None","Low","Medium","High","Extreme"), 0, true),
        Option("enableContributionCulling", "Cull very small objects", onOff, 0, false),
        Option("contributionCullingStatic", "Small-object culling threshold", (0..200).map(Int::toString), 0, false),
        Option("enableLod", "Use model level of detail", onOff, 0, false),
        Option("tileTransitions", "Tile transitions", onOff, 0, false),
        Option("useNonAlphaParticles", "Opaque particles", onOff, 0, false),
        Option("useAlphaParticles", "Transparent particles", onOff, 0, false),
        Option("fovHorizontal", "Horizontal field of view", (60..110).map(Int::toString), 60, true),
        Option("highResBinoculars", "High resolution binoculars", onOff, 0, false),
        Option("gpuSkinning", "GPU character animation", onOff, 0, false),
        Option("maxShaderLights", "Maximum shader lights", (2..8).map(Int::toString), 2, true),
        Option("resolutionScale", "Supersampling", listOf("100%","125%","150%","175%","200%"), 0, false),
        Option("tileDecorations", "Ground decoration density", listOf("Very Sparse","Sparse","Medium","Dense","Extreme"), 0, true),
        Option("skyDetail", "Sky detail", quality, 0, true),
        Option("renderDistant", "Distant terrain", onOff, 0, true),
        Option("screenBrightness", "Game brightness", (-100..100).map { "$it%" }, 0, false),
        Option("useCompressedTexture", "Texture compression (if supported)", onOff, 0, true),
        Option("useCompressedTextureS3TC", "S3TC compression (if supported)", onOff, 0, true)
    )
    val resolutions = listOf("800x480", "960x540", "1280x720")
    fun command(preset: String, values: List<Int>): String {
        require(preset in listOf("performance", "imported") && values.size == options.size)
        require(values.indices.all { options[it].valid(values[it]) })
        return preset + ":" + values.joinToString(",")
    }
}
