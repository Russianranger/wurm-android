import java.lang.reflect.InvocationTargetException;
import org.lwjgl.opengl.Display;
import static org.lwjgl.opengl.GL11.*;

/** Optional actual imported-client window test. Run only in a disposable working directory. */
public final class ProbeClientWindow {
    public static void main(String[] args) throws Throwable {
        Class<?> profile = Class.forName("com.wurmonline.client.settings.Profile");
        Object p = profile.getMethod("getProfile").invoke(null);
        profile.getMethod("loadPlayer", String.class).invoke(p, "Thor");
        client.DirectClientLaunch.prepareDisplay();
        Class<?> type = Class.forName("com.wurmonline.client.LwjglClient");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object window = constructor.newInstance();
        var init = type.getDeclaredMethod("initWindow"); init.setAccessible(true);
        var destroy = type.getDeclaredMethod("destroyWindow"); destroy.setAccessible(true);
        // This is the cleanup path that used to hide a pre-window JavaFX error.
        Display.destroy();
        try {
            try { init.invoke(window); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
            if (!Display.isCreated() || Display.getWindow() == 0) throw new AssertionError("No real client window");
            glClearColor(.2f, .4f, .6f, 1f); glClear(GL_COLOR_BUFFER_BIT);
            Display.update(false);
            try (var frame = new java.io.DataInputStream(new java.io.FileInputStream(System.getProperty("wurm.graphics.frame")))) {
                if (frame.readInt()!=0x57554746 || frame.readInt()!=1 || frame.readInt()!=960 || frame.readInt()!=540 || frame.readInt()<1)
                    throw new AssertionError("Unexpected frame header");
                frame.skipNBytes(4L*(270*960+480));
                if ((frame.readInt() & 0xffffff)!=0x336699) throw new AssertionError("Sample clear did not reach frame transport");
            }
            System.out.println("[window-probe] WURM_WINDOW_INIT_PASS size="+Display.getDisplayMode()+"; real client icon decoding and initWindow; sample clear only, game rendering/login not tested");
        } finally {
            destroy.invoke(window);
            destroy.invoke(window);
        }
        if (Display.isCreated() || Display.getWindow() != 0) throw new AssertionError("Window state remained live");
        // Exercise Wurm's own error routing without a Swing dialog or hidden success.
        Throwable expected = new IllegalStateException("PRIVATE_PROBE_ERROR_ROUTE");
        Class.forName("com.wurmonline.client.WurmClientBase").getMethod("processError", Throwable.class, String.class)
            .invoke(null, expected, "window probe");
        Object reported = Class.forName("com.wurmonline.client.ErrorReporterPanel").getMethod("androidFailure").invoke(null);
        if (reported != expected) throw new AssertionError("Original Wurm error not preserved");
        System.out.println("[window-probe] WURM_WINDOW_PROBE_PASS cleanup=twice originalError=preserved");
        System.exit(0);
    }
}
