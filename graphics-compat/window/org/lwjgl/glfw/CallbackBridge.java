package org.lwjgl.glfw;

/** Wurm-owned platform hooks for the adapted Pojav Java window layer. */
public final class CallbackBridge {
    public static final int CLIPBOARD_COPY = 2000, CLIPBOARD_PASTE = 2001;
    private static String clipboard = "";
    public static void nativeSetGrabbing(boolean grabbed) {
        System.out.println("[window] CURSOR_GRAB " + grabbed);
    }
    public static void enableGamepadDirectInput() {
        throw new UnsupportedOperationException("Use the keyboard/mouse controller profile");
    }
    public static String nativeClipboard(int action, byte[] data) {
        if (action == CLIPBOARD_COPY) clipboard = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        return clipboard; // JVM-local clipboard; no Android clipboard claims.
    }
}
