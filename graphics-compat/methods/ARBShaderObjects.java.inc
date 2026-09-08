    // Wurm Android: LWJGL2 stores size/type at the current position and following slot.
    public static String glGetActiveUniformARB(int program, int index, int maxLength, java.nio.IntBuffer sizeType) {
        if (!sizeType.isDirect() || sizeType.isReadOnly() || sizeType.remaining() < 2)
            throw new IllegalArgumentException("sizeType requires two writable direct integers");
        java.nio.IntBuffer type = sizeType.duplicate();
        type.position(type.position() + 1);
        return glGetActiveUniformARB(program, index, maxLength, sizeType.duplicate(), type);
    }
