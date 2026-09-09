package wurm.graphics;

import java.nio.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.Pbuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.EXTFramebufferObject.*;

/** Tested FBO requirement for one verified legacy Wurm check, not a Pbuffer implementation. */
public final class OffscreenSupport {
    private static void log(String s) { System.out.println("[graphics] " + s); }
    public static int getCapabilities() {
        // Only the SHA-verified WurmClientBase reference is redirected here. The
        // public LWJGL Pbuffer capability and all its constructors are untouched.
        try {
            Object option = Class.forName("com.wurmonline.client.options.Options").getField("useFBO").get(null);
            if ((Boolean) option.getClass().getMethod("disabled").invoke(option))
                throw new IllegalStateException("OFFSCREEN_FBO_DISABLED in imported client settings");
            verifyFramebuffer();
            log("OFFSCREEN_REQUIREMENT_PASS backend=FBO caller=verified-WurmClientBase legacyPbufferCaps=" + Pbuffer.getCapabilities() + "; not advertising LWJGL Pbuffer construction");
            return 1; // Satisfies this engine's obsolete bit test only, after a real FBO check.
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("OFFSCREEN_OPTION_ABI_FAILED", error);
        }
    }
    private static void check(String stage) {
        int gl = glGetError(), gles = NativeEgl.error();
        if (gl != 0 || gles != 0)
            throw new IllegalStateException("OFFSCREEN_GL_FAILED stage=" + stage + " GL=0x" + Integer.toHexString(gl) + " GLES=0x" + Integer.toHexString(gles));
    }
    public static void verifyFramebuffer() {
        if (WindowBackend.current() == 0) throw new IllegalStateException("OFFSCREEN_NO_OWNED_CONTEXT");
        if (!GL.getCapabilities().GL_EXT_framebuffer_object)
            throw new IllegalStateException("OFFSCREEN_EXT_FBO_UNAVAILABLE");
        // Wurm deliberately queries unsupported optional memory/texture enums
        // before this check. Attribute those errors, then isolate our operations.
        for (int i = 0; i < 16; i++) {
            int gl = glGetError(), gles = NativeEgl.error();
            if (gl == 0 && gles == 0) break;
            log("OFFSCREEN_PRIOR_ERROR GL=0x" + Integer.toHexString(gl) + " GLES=0x" + Integer.toHexString(gles));
            if (i == 15) throw new IllegalStateException("OFFSCREEN_PRIOR_ERRORS_PERSIST");
        }
        int previousFbo = glGetInteger(GL_FRAMEBUFFER_BINDING_EXT);
        int previousRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING_EXT);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        FloatBuffer clear = BufferUtils.createFloatBuffer(4); glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        ByteBuffer mask = BufferUtils.createByteBuffer(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST), depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        check("save-state");
        int fbo = 0, texture = 0, depth = 0;
        Throwable failure = null;
        log("OFFSCREEN_FBO_BEGIN size=64x64 color=RGBA8 depth=16 previousFbo=" + previousFbo + " previousRenderbuffer=" + previousRenderbuffer + " previousTexture=" + previousTexture);
        try {
            fbo = glGenFramebuffersEXT(); texture = glGenTextures(); depth = glGenRenderbuffersEXT();
            glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, fbo);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 64, 64, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
            glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT, GL_TEXTURE_2D, texture, 0);
            glBindRenderbufferEXT(GL_RENDERBUFFER_EXT, depth);
            glRenderbufferStorageEXT(GL_RENDERBUFFER_EXT, GL_DEPTH_COMPONENT16, 64, 64);
            glFramebufferRenderbufferEXT(GL_FRAMEBUFFER_EXT, GL_DEPTH_ATTACHMENT_EXT, GL_RENDERBUFFER_EXT, depth);
            check("attachments");
            int status = glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT);
            check("completeness");
            if (status != GL_FRAMEBUFFER_COMPLETE_EXT)
                throw new IllegalStateException("OFFSCREEN_FBO_INCOMPLETE status=0x" + Integer.toHexString(status));
            glDisable(GL_SCISSOR_TEST); glColorMask(true, true, true, true); glDepthMask(true);
            glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            check("clear");
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels(32, 32, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            check("readback");
            if ((pixel.get(0)&255)!=255 || pixel.get(1)!=0 || (pixel.get(2)&255)!=255 || (pixel.get(3)&255)!=255)
                throw new IllegalStateException("OFFSCREEN_PIXEL_MISMATCH rgba=" + (pixel.get(0)&255) + "," + (pixel.get(1)&255) + "," + (pixel.get(2)&255) + "," + (pixel.get(3)&255));
        } catch (RuntimeException | Error error) { failure = error; throw error; }
        finally {
            try {
                glBindFramebufferEXT(GL_FRAMEBUFFER_EXT, previousFbo);
                check("restore-framebuffer");
                // Deleting our bound renderbuffer legally unbinds it. The pinned
                // GL4ES rejects a direct nonzero->zero renderbuffer bind; object
                // deletion resets both its bookkeeping and the GLES binding.
                if (depth != 0) { glDeleteRenderbuffersEXT(depth); depth = 0; }
                check("delete-renderbuffer");
                glBindRenderbufferEXT(GL_RENDERBUFFER_EXT, previousRenderbuffer);
                check("restore-renderbuffer");
                glBindTexture(GL_TEXTURE_2D, previousTexture);
                check("restore-texture");
                glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
                glColorMask(mask.get(0)!=0, mask.get(1)!=0, mask.get(2)!=0, mask.get(3)!=0);
                glDepthMask(depthWrite);
                if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
                check("restore-clear-state");
                if (fbo != 0) glDeleteFramebuffersEXT(fbo);
                check("delete-framebuffer");
                if (texture != 0) glDeleteTextures(texture);
                check("restore-delete");
            } catch (RuntimeException | Error cleanup) {
                if (failure != null) failure.addSuppressed(cleanup); else throw cleanup;
            }
        }
        log("OFFSCREEN_FBO_PASS pixel=255,0,255,255 restored=true; sample target only, game scene not tested");
    }
}
