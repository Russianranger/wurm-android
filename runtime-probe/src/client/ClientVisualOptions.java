package client;

import java.lang.reflect.Method;
import java.util.*;

/** Small inspected dynamic-option preset; profile files are not overwritten. */
public final class ClientVisualOptions {
    private record Setting(Object option, Method setter, Method getter, Object imported, Object low) {}
    private static List<Setting> settings;
    private static final String[] MULTI = {"waterDetail", "reflections", "treeRenderingDistance", "structureRenderingDistance", "itemCreatureRenderingDistance"};
    private static final String[] LABELS = {"Low", "Disabled", "Short", "Short", "Short"};
    private static final int[] LOW = {0, 0, 1, 1, 1};
    private static final String[] BOOL = {"prettyTrees", "prettyWeather", "renderSunGlare"};
    public static void apply(String preset) throws Exception {
        if (!List.of("performance", "imported").contains(preset)) throw new IllegalArgumentException("Unknown graphics preset");
        if (settings == null) {
            Class<?> options = Class.forName("com.wurmonline.client.options.Options");
            List<Setting> resolved = new ArrayList<>();
            for (int i=0; i<MULTI.length; i++) {
                Object option = options.getField(MULTI[i]).get(null);
                String[] labels = (String[]) option.getClass().getField("options").get(option);
                if (labels.length <= LOW[i] || !LABELS[i].equals(labels[LOW[i]]))
                    throw new IllegalStateException("GRAPHICS_OPTION_ABI_CHANGED " + MULTI[i]);
                resolved.add(setting(option, int.class, LOW[i]));
            }
            for (String field : BOOL) resolved.add(setting(options.getField(field).get(null), boolean.class, false));
            settings = List.copyOf(resolved); // Resolve every field before changing any value.
        }
        List<Object> before = new ArrayList<>();
        for (Setting s : settings) before.add(s.getter.invoke(s.option));
        try {
            for (Setting s : settings) s.setter.invoke(s.option, preset.equals("performance") ? s.low : s.imported);
        } catch (ReflectiveOperationException failure) {
            for (int i=0; i<settings.size(); i++) {
                Setting s = settings.get(i);
                try { s.setter.invoke(s.option, before.get(i)); } catch (ReflectiveOperationException restore) { failure.addSuppressed(restore); }
            }
            throw failure;
        }
        System.out.println("[client-ui] GRAPHICS_APPLIED preset=" + preset + " values=" +
            settings.stream().map(s -> { try { return s.getter.invoke(s.option).toString(); } catch (Exception e) { return "unavailable"; } }).toList());
    }
    private static Setting setting(Object option, Class<?> type, Object low) throws Exception {
        Method get = option.getClass().getMethod("value");
        return new Setting(option, option.getClass().getMethod("set", type), get, get.invoke(option), low);
    }
}
