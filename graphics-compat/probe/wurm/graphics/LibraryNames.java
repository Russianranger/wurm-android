package wurm.graphics;

import java.util.function.Function;

/** Upstream's supported library-name mapper keeps LWJGL3 separate from imported LWJGL2. */
public final class LibraryNames implements Function<String, String> {
    public String apply(String name) {
        if (name.equals("lwjgl")) return "wurm_lwjgl3";
        if (name.equals("lwjgl_opengl")) return "wurm_lwjgl3_opengl";
        return name;
    }
}
