package wurm.graphics;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLContext;
import static org.lwjgl.opengl.GL11.*;

/** Diagnose the actual desktop API exposed by our GL4ES 2.1 window backend. */
public final class CapabilityChecks {
    public static void verify() {
        verifyLookupContract();
        String version = glGetString(GL_VERSION);
        var core = GL.getCapabilities();
        var legacy = GLContext.getCapabilities();
        System.out.println("[graphics] CAPABILITY_CHECK version=" + version +
            " core21=" + core.OpenGL21 + " legacy21=" + legacy.OpenGL21 +
            " core33=" + core.OpenGL33 + " legacy33=" + legacy.OpenGL33);
        if (version == null || !version.startsWith("2.1 ") || !version.contains("gl4es"))
            throw new IllegalStateException("CAPABILITY_BACKEND_UNQUALIFIED " + version);
        if (!core.OpenGL21 || !legacy.OpenGL21)
            throw new IllegalStateException("CAPABILITY_GL21_REQUIRED; inspect missing function logs");
        for (String name : new String[]{"OpenGL30", "OpenGL31", "OpenGL32", "OpenGL33", "OpenGL40", "OpenGL41", "OpenGL42", "OpenGL43", "OpenGL44"}) {
            try {
                if (core.getClass().getField(name).getBoolean(core) || legacy.getClass().getField(name).getBoolean(legacy))
                    throw new IllegalStateException("CAPABILITY_FALSE_POSITIVE " + name + " on " + version);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("CAPABILITY_ABI_FAILED " + name, error);
            }
        }
        if (core.OpenGL45 || core.OpenGL46)
            throw new IllegalStateException("CAPABILITY_FALSE_POSITIVE OpenGL45/46");
        System.out.println("[graphics] CAPABILITY_CHECK_PASS GL21=true GL30plus=false; native addresses retained; no GL3.3 claim");
    }

    private static void verifyLookupContract() {
        // Isolated function tables; these addresses are never called. Prove a
        // missing slot does not hide failure or prevent later slots being cached.
        int[] calls = {0};
        org.lwjgl.system.FunctionProvider provider = name -> {
            calls[0]++;
            return org.lwjgl.system.MemoryUtil.memASCII(org.lwjgl.system.MemoryUtil.memAddress(name)).equals("present") ? 123L : 0L;
        };
        int[] indices = {-1, 0, 1, 2};
        String[] names = {"ignored", "cached", "absent", "present"};
        long[] array = {456L, 0L, 0L};
        boolean result = org.lwjgl.system.Checks.checkFunctions(provider, array, indices, names);
        if (result || calls[0] != 2 || array[0] != 456L || array[1] != 0L || array[2] != 123L)
            throw new IllegalStateException("CAPABILITY_ARRAY_LOOKUP_CONTRACT_FAILED");
        var pointers = org.lwjgl.BufferUtils.createPointerBuffer(3); pointers.put(0, 456L);
        calls[0] = 0;
        result = org.lwjgl.system.Checks.checkFunctions(provider, pointers, indices, names);
        if (result || calls[0] != 2 || pointers.get(0) != 456L || pointers.get(1) != 0L || pointers.get(2) != 123L)
            throw new IllegalStateException("CAPABILITY_POINTER_LOOKUP_CONTRACT_FAILED");
        calls[0] = 0;
        array[1] = 789L; pointers.put(1, 789L);
        if (!org.lwjgl.system.Checks.checkFunctions(provider, array, indices, names) ||
            !org.lwjgl.system.Checks.checkFunctions(provider, pointers, indices, names) || calls[0] != 0)
            throw new IllegalStateException("CAPABILITY_CACHED_LOOKUP_CONTRACT_FAILED");
        if (org.lwjgl.system.Checks.reportMissing("wurm-fixture", "deliberately-absent"))
            throw new IllegalStateException("CAPABILITY_MISSING_REPORTED_SUPPORTED");
        System.out.println("[graphics] CAPABILITY_LOOKUP_PASS missing=false cached=true laterAddressesPreserved=true");
    }

    /** Runs only after the real game's GLHelper.initialize(), in its startup check. */
    public static void verifyWurmRenderer() throws ReflectiveOperationException {
        WurmVisibility.configure(glGetString(GL_VERSION));
        Class<?> helper = Class.forName("com.wurmonline.client.util.GLHelper");
        boolean deferred = (Boolean) helper.getMethod("useDeferredShading").invoke(null);
        boolean instancing = (Boolean) helper.getMethod("useInstancing").invoke(null);
        System.out.println("[graphics] WURM_RENDERER_SELECTION deferred=" + deferred + " instancing=" + instancing);
        if (deferred || instancing)
            throw new IllegalStateException("WURM_RENDERER_CAPABILITY_MISMATCH: GL3.3 renderer selected on GL4ES 2.1");
        System.out.println("[graphics] WURM_RENDERER_SELECTION_PASS path=existing-legacy-renderer/basic-water; scene/login not verified");
    }
}
