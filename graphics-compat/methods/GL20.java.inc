    // Wurm Android: legacy LWJGL2 name-only overload; keep the upstream GL implementation.
    public static String glGetActiveAttrib(int program, int index, int maxLength) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            return glGetActiveAttrib(program, index, maxLength, stack.mallocInt(1), stack.mallocInt(1));
        }
    }
