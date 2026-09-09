package wurm.graphics;

final class NativeEgl {
    static { System.loadLibrary("wurm_graphics"); }
    static native void open(String backend, int width, int height);
    static native void resize(int width, int height);
    static native void swap();
    static native int error();
    static native void close();
}
