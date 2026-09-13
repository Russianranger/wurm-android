package client;

import java.lang.reflect.*;

/** Only the inspected engine's integer queries use this extension guard. */
public final class ClientGlCapabilities {
    private static final int NVIDIA_MEMORY=0x9048, ATI_MEMORY=0x87fb;
    static byte[] patch(byte[] bytes, boolean reverse) throws Exception {
        return ClientMethodOwner.redirect(bytes,ClientGraphicsPatch.sha(bytes),
            "org/lwjgl/opengl/GL11","client/ClientGlCapabilities","glGetInteger","(I)I",reverse);
    }
    public static int glGetInteger(int name) {
        try {
            if(name==NVIDIA_MEMORY || name==ATI_MEMORY) {
                Object caps=Class.forName("org.lwjgl.opengl.GL").getMethod("getCapabilities").invoke(null);
                String extension=name==NVIDIA_MEMORY?"GL_NVX_gpu_memory_info":"GL_ATI_meminfo";
                boolean available;
                try { available=caps.getClass().getField(extension).getBoolean(caps); }
                catch(NoSuchFieldException absent) { available=false; }
                if(!available) {
                    System.out.println("[graphics-capability] "+java.time.Instant.now()+" GPU_MEMORY_QUERY_UNAVAILABLE extension="+extension+"; retaining game's unknown-memory fallback");
                    return 0;
                }
            }
            return (Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetInteger",int.class).invoke(null,name);
        } catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof RuntimeException e) throw e;
            if(failure.getCause() instanceof Error e) throw e;
            throw new IllegalStateException("GPU capability query failed",failure.getCause());
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("GPU capability ABI unavailable",failure); }
    }
}
