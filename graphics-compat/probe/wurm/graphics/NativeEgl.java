package wurm.graphics;

final class NativeEgl {
    static { System.loadLibrary("wurm_graphics"); }
    static native void open(String backend, int width, int height);
    static native void resize(int width, int height);
    static native void swap();
    static native int error();
    static native boolean readbackOpen();
    static native void readbackIssue();
    static native void readbackCollect(java.nio.ByteBuffer pixels);
    static native void readbackClose();
    static native void close();
}
