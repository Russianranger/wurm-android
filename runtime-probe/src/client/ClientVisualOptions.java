package client;

import java.lang.reflect.Method;
import java.util.*;

/** Inspected dynamic options, applied atomically on the game thread without saving the profile. */
public final class ClientVisualOptions {
    private record Spec(String field, int low, String... labels) {}
    // Stable wire order shared with Android GraphicsOptions; -1 inherits the base preset.
    // Empty labels denote boolean or an explicitly bounded integer range. Restart options are skipped live.
    private static final List<Spec> SPECS = List.of(
        new Spec("waterDetail", 0, "Low", "Medium", "High"),
        new Spec("reflections", 0, "Disabled", "Sky", "Sky & Terrain", "Sky, Terrain & Trees", "Almost Everything"),
        new Spec("treeRenderingDistance", 1, "Very Short", "Short", "Medium", "Far", "Extreme"),
        new Spec("structureRenderingDistance", 1, "Very Short", "Short", "Medium", "Far", "Extreme"),
        new Spec("itemCreatureRenderingDistance", 1, "Very Short", "Short", "Medium", "Far", "Extreme"),
        new Spec("prettyTrees", 0), new Spec("prettyWeather", 0), new Spec("renderSunGlare", 0),
        new Spec("caveDetail", -1, "Low", "Medium", "High"),
        new Spec("shadowLevel", -1, "Disabled", "Simple Objects", "Objects", "Objects & Structures", "Everything"),
        new Spec("shadowMapSize", -1, "Small", "Medium", "Large", "Huge"),
        new Spec("lod", -1, "Short", "Normal", "Far"),
        new Spec("useBloom", -1), new Spec("useVignette", -1), new Spec("useFXAA", -1),
        new Spec("limitDynamicLights", -1), new Spec("maxDynamicLights", -1),
        new Spec("anisotropicFilteringLevel", -1, "1", "2", "4", "8", "16"),
        new Spec("terrainDetail", -1, "Low", "Medium", "High"),
        new Spec("normalMapping", -1),
        new Spec("enableFontSmoothing", -1, "Off", "Dynamic", "On"),
        new Spec("modelLoaderThreadCount", -1, "1", "2", "3", "4", "8"),
        new Spec("maxTextureSize", -1, "Low", "Medium", "High", "Very High"),
        new Spec("playerTextureSize", -1, "256", "512", "1024", "2048"),
        new Spec("reflectionTextureSize", -1, "Low", "Medium", "High"),
        new Spec("offscreenTextureSize", -1, "Low", "Medium", "High", "Very High"),
        new Spec("megaTextureSize", -1, "256", "512", "1024", "2048", "4096", "8192", "No Limit"),
        new Spec("textureScalingHint", -1, "Nearest Neighbour (Fastest)", "Bilinear", "Bicubic (Nicest)"),
        new Spec("selfAnimationplayback", -1, "All", "Walking Only", "None"),
        new Spec("colladaAnimations", -1, "None", "Low", "Medium", "High", "Extreme"),
        new Spec("enableContributionCulling", -1),
        new Spec("contributionCullingStatic", -1),
        new Spec("enableLod", -1),
        new Spec("tileTransitions", -1),
        new Spec("useNonAlphaParticles", -1),
        new Spec("useAlphaParticles", -1),
        new Spec("fovHorizontal", -1),
        new Spec("highResBinoculars", -1),
        new Spec("gpuSkinning", -1),
        new Spec("maxShaderLights", -1),
        new Spec("resolutionScale", -1, "100%", "125%", "150%", "175%", "200%"),
        new Spec("tileDecorations", -1, "Very Sparse", "Sparse", "Medium", "Dense", "Extreme"),
        new Spec("skyDetail", -1, "Low", "Medium", "High"), new Spec("renderDistant", -1),
        new Spec("screenBrightness", -1), new Spec("useCompressedTexture", -1), new Spec("useCompressedTextureS3TC", -1)
    );
    private static final Set<String> RESTART = Set.of("anisotropicFilteringLevel", "terrainDetail", "normalMapping", "enableFontSmoothing", "modelLoaderThreadCount", "maxTextureSize", "playerTextureSize", "megaTextureSize", "textureScalingHint", "colladaAnimations", "fovHorizontal", "maxShaderLights", "tileDecorations", "skyDetail", "renderDistant", "useCompressedTexture", "useCompressedTextureS3TC");
    private static final Map<String,int[]> RANGES = Map.of("maxDynamicLights",new int[]{1,16},
        "contributionCullingStatic",new int[]{0,200}, "fovHorizontal",new int[]{60,110}, "maxShaderLights",new int[]{2,8}, "screenBrightness",new int[]{0,200});
    private record Setting(Object option, Method setter, Method getter, Object imported, Object low) {}
    private static List<Setting> settings;
    private static boolean bool(Spec s) { return s.labels.length == 0 && !RANGES.containsKey(s.field); }
    private static Object value(Spec s, int n) {
        if (s.field.equals("screenBrightness")) return (n-100)/100f;
        return bool(s) ? (Object)(n == 1) : n;
    }

    public static void apply(String command) throws Exception { apply(command,false); }
    public static void applyStartup(String command) throws Exception { apply(command,true); }
    private static void apply(String command, boolean startup) throws Exception {
        if (command == null || command.length() > 512) throw new IllegalArgumentException("Invalid graphics command");
        String[] parts = command.split(":", -1);
        String preset = parts[0];
        if (parts.length > 2 || !List.of("performance", "imported").contains(preset))
            throw new IllegalArgumentException("Unknown graphics preset");
        int[] overrides = new int[SPECS.size()]; Arrays.fill(overrides, -1);
        if (parts.length == 2) {
            String[] fields = parts[1].split(",", -1);
            if (fields.length != SPECS.size() && fields.length != 41 && fields.length != 17) throw new IllegalArgumentException("Invalid graphics option count");
            for (int i=0; i<fields.length; i++) {
                Spec s = SPECS.get(i);
                if (!fields[i].matches("-1|[0-9]{1,3}")) throw new IllegalArgumentException("Invalid graphics value");
                int n = Integer.parseInt(fields[i]);
                int min = RANGES.containsKey(s.field) ? RANGES.get(s.field)[0] : 0;
                int max = RANGES.containsKey(s.field) ? RANGES.get(s.field)[1] : bool(s) ? 1 : s.labels.length-1;
                if (n != -1 && (n < min || n > max)) throw new IllegalArgumentException("Invalid graphics value: " + s.field);
                overrides[i] = n;
            }
        }
        if (settings == null) {
            Class<?> options = Class.forName("com.wurmonline.client.options.Options");
            List<Setting> resolved = new ArrayList<>();
            for (Spec s : SPECS) {
                Object option = options.getField(s.field).get(null);
                if (s.labels.length != 0 && !Arrays.equals(s.labels, (String[])option.getClass().getField("options").get(option)))
                    throw new IllegalStateException("GRAPHICS_OPTION_ABI_CHANGED " + s.field);
                Method get = option.getClass().getMethod("value");
                Object imported = get.invoke(option);
                resolved.add(new Setting(option, option.getClass().getMethod("set", s.field.equals("screenBrightness") ? float.class : bool(s) ? boolean.class : int.class),
                    get, imported, s.low == -1 ? imported : value(s, s.low)));
            }
            settings = List.copyOf(resolved);
        }
        List<Object> before = new ArrayList<>();
        for (Setting s : settings) before.add(s.getter.invoke(s.option));
        try {
            for (int i=0; i<settings.size(); i++) {
                Setting s = settings.get(i);
                Object target = overrides[i] == -1 ? (preset.equals("performance") ? s.low : s.imported) : value(SPECS.get(i), overrides[i]);
                if ((!RESTART.contains(SPECS.get(i).field) || startup) && !Objects.equals(before.get(i),target))
                    s.setter.invoke(s.option, target);
            }
        } catch (ReflectiveOperationException failure) {
            for (int i=0; i<settings.size(); i++) {
                Setting s = settings.get(i);
                try { s.setter.invoke(s.option, before.get(i)); } catch (ReflectiveOperationException restore) { failure.addSuppressed(restore); }
            }
            throw failure;
        }
        System.out.println("[client-ui] GRAPHICS_APPLIED phase=" + (startup ? "startup" : "live") + " preset=" + preset + " overrides=" + Arrays.toString(overrides) + " values=" +
            settings.stream().map(s -> { try { return s.getter.invoke(s.option).toString(); } catch (Exception e) { return "unavailable"; } }).toList());
    }
}
