    // Wurm Android: legacy LWJGL2 name-only overload.
    public static String glGetActiveAttribARB(int program, int index, int maxLength) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            return glGetActiveAttribARB(program, index, maxLength, stack.mallocInt(1), stack.mallocInt(1));
        }
    }
