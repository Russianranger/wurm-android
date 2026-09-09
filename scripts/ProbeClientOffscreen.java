package wurm.graphics;

import client.ClientGraphicsPatch;
import client.DirectClientLaunch;
import java.lang.reflect.*;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.EXTFramebufferObject.*;

/** Optional private engine capability/FBO probe; no packs/login or full game startup. */
public final class ProbeClientOffscreen {
    public static void main(String[] args) throws Throwable {
        ClientGraphicsPatch.verifySelected();
        Class<?> profile = Class.forName("com.wurmonline.client.settings.Profile");
        Object p = profile.getMethod("getProfile").invoke(null);
        profile.getMethod("loadPlayer", String.class).invoke(p, "Thor");
        DirectClientLaunch.prepareDisplay();
        Class<?> window = Class.forName("com.wurmonline.client.LwjglClient");
        var ctor = window.getDeclaredConstructor(); ctor.setAccessible(true);
        Object w = ctor.newInstance();
        var init = window.getDeclaredMethod("initWindow"); init.setAccessible(true); init.invoke(w);
        try {
            Class<?> engine = Class.forName("com.wurmonline.client.WurmClientBase");
            // The inspected check uses no instance fields. Avoid starting the
            // full game's jobs/world/HUD just to exercise that real method.
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe"); field.setAccessible(true);
            Object instance = unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), engine);
            var check = engine.getDeclaredMethod("checkSupportLevels"); check.setAccessible(true);
            try { check.invoke(instance); }
            catch (InvocationTargetException error) { throw error.getCause(); }
            System.out.println("[offscreen-probe] WURM_SUPPORT_LEVELS_PASS legacyPbufferCaps=" + Pbuffer.getCapabilities());
            Class<?> fboClass = Class.forName("com.wurmonline.client.renderer.backend.FBO");
            for (boolean depth : new boolean[]{false,true}) {
                Object fbo = fboClass.getConstructor(int.class,int.class,boolean.class,boolean.class,boolean.class,
                    boolean.class,boolean.class,int.class,int[].class)
                    .newInstance(64,64,depth,true,false,false,false,GL_RGBA,new int[]{GL_RGBA8});
                try {
                    if (!(Boolean)fboClass.getMethod("init").invoke(fbo)) throw new AssertionError("Real Wurm FBO.init failed");
                    glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,(Integer)fboClass.getMethod("getId").invoke(fbo));
                    glClearColor(0,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                    var pixel=org.lwjgl.BufferUtils.createByteBuffer(4);
                    glReadPixels(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                    if (pixel.get(0)!=0 || (pixel.get(1)&255)!=255 || pixel.get(2)!=0 || (pixel.get(3)&255)!=255 || glGetError()!=0 || wurm.graphics.NativeEgl.error()!=0)
                        throw new AssertionError("Real Wurm FBO readback failed");
                    System.out.println("[offscreen-probe] WURM_FBO_PASS depthTexture="+depth+" pixel=0,255,0,255");
                } finally {
                    glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,0);
                    fboClass.getMethod("delete").invoke(fbo);
                    fboClass.getMethod("deleteDeferred").invoke(null);
                }
            }
            System.out.println("[offscreen-probe] WURM_OFFSCREEN_PROBE_PASS; real support check/FBO only, scene/login not tested");
        } finally { Display.destroy(); }
        System.exit(0);
    }
}
