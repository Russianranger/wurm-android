package wurm.graphics;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Correct the inspected engine's visibility capability before any scene cells exist. */
public final class WurmVisibility {
    private WurmVisibility() {}

    public static void configure(String version) throws ReflectiveOperationException {
        configure(version, Class.forName("com.wurmonline.client.util.GLHelper"),
            Class.forName("com.wurmonline.client.options.Options"));
    }

    static void configure(String version, Class<?> helper, Class<?> options) throws ReflectiveOperationException {
        if (version == null || !version.startsWith("2.1 ") || !version.contains("gl4es"))
            throw new IllegalStateException("VISIBILITY_BACKEND_UNQUALIFIED " + version);
        Field support = helper.getField("occlusionQueries");
        Object unavailable = support.getType().getField("NOT_SUPPORTED").get(null);
        Object option = options.getField("useOcclusionQueries").get(null);
        if (!Arrays.equals((String[])option.getClass().getField("options").get(option),
                new String[]{"Disabled", "Extension", "Core"}))
            throw new IllegalStateException("VISIBILITY_OPTION_ABI_CHANGED");
        Object previousSupport = support.get(null);
        Object previousOption = option.getClass().getMethod("value").invoke(option);
        // GLHelper chooses CORE just from OpenGL15. Changing the profile alone
        // cannot disable Cell.wantsOcclusionCulling(). Correct both surfaces.
        option.getClass().getMethod("setDisabled").invoke(option);
        support.set(null, unavailable);
        if (!Boolean.TRUE.equals(option.getClass().getMethod("disabled").invoke(option)) ||
                !Boolean.FALSE.equals(helper.getMethod("isOcclusionQueryAvailable").invoke(null)))
            throw new IllegalStateException("VISIBILITY_DISABLE_FAILED");
        System.out.println("[graphics] WURM_VISIBILITY_POLICY occlusion=disabled reason=GL4ES-placeholder-samples" +
            " previousSupport=" + previousSupport + " previousOption=" + previousOption +
            " distanceCulling=unchanged frustumCulling=unchanged");
    }
}
