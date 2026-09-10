package client;

import java.lang.reflect.Method;
import java.util.*;

/** Inspected dynamic options, applied atomically on the game thread without saving the profile. */
public final class ClientVisualOptions {
    private record Spec(String field, int low, String... labels) {}
    // Stable wire order shared with Android GraphicsOptions; -1 inherits the base preset.
    // Empty labels: boolean (0/1); maxDynamicLights alone is an integer range (1..16).
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
        new Spec("limitDynamicLights", -1), new Spec("maxDynamicLights", -1)
    );
    private record Setting(Object option, Method setter, Method getter, Object imported, Object low) {}
    private static List<Setting> settings;
    private static boolean bool(Spec s) { return s.labels.length == 0 && !s.field.equals("maxDynamicLights"); }
    private static Object value(Spec s, int n) { return bool(s) ? (Object)(n == 1) : n; }

    public static void apply(String command) throws Exception {
        if (command == null || command.length() > 150) throw new IllegalArgumentException("Invalid graphics command");
        String[] parts = command.split(":", -1);
        String preset = parts[0];
        if (parts.length > 2 || !List.of("performance", "imported").contains(preset))
            throw new IllegalArgumentException("Unknown graphics preset");
        int[] overrides = new int[SPECS.size()]; Arrays.fill(overrides, -1);
        if (parts.length == 2) {
            String[] fields = parts[1].split(",", -1);
            if (fields.length != SPECS.size()) throw new IllegalArgumentException("Invalid graphics option count");
            for (int i=0; i<fields.length; i++) {
                Spec s = SPECS.get(i);
                if (!fields[i].matches("-1|[0-9]{1,2}")) throw new IllegalArgumentException("Invalid graphics value");
                int n = Integer.parseInt(fields[i]);
                int min = s.field.equals("maxDynamicLights") ? 1 : 0;
                int max = s.field.equals("maxDynamicLights") ? 16 : bool(s) ? 1 : s.labels.length-1;
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
                resolved.add(new Setting(option, option.getClass().getMethod("set", bool(s) ? boolean.class : int.class),
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
                s.setter.invoke(s.option, target);
            }
        } catch (ReflectiveOperationException failure) {
            for (int i=0; i<settings.size(); i++) {
                Setting s = settings.get(i);
                try { s.setter.invoke(s.option, before.get(i)); } catch (ReflectiveOperationException restore) { failure.addSuppressed(restore); }
            }
            throw failure;
        }
        System.out.println("[client-ui] GRAPHICS_APPLIED preset=" + preset + " overrides=" + Arrays.toString(overrides) + " values=" +
            settings.stream().map(s -> { try { return s.getter.invoke(s.option).toString(); } catch (Exception e) { return "unavailable"; } }).toList());
    }
}
