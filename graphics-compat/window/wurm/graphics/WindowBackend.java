package wurm.graphics;

import java.io.*;
import java.nio.*;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL11.*;

/** Single owned game thread, one real EGL context, bounded diagnostic readback. */
public final class WindowBackend {
    private static Thread owner;
    private static int width, height, sequence;
    private static long lastFrame;
    private static long statsStart, readbackNanos, publishNanos;
    private static int swaps, published;
    private static ByteBuffer pixels;
    private static final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(512);
    private static volatile boolean stop;
    private static WindowInput pointer;

    public static long open(int w, int h) {
        if (owner != null) throw new IllegalStateException("Only one EGL window is supported");
        System.out.println("[window] WINDOW_CREATE requested=" + w + "x" + h + " backend=Pojav-Java-GLFW/EGL-pbuffer RGBA8 depth16; no desktop window manager");
        NativeEgl.open(System.getProperty("wurm.graphics.library"), w, h);
        owner = Thread.currentThread(); width = w; height = h;
        GL.create(System.getProperty("wurm.graphics.library"));
        GL.createCapabilities();
        pixels = ByteBuffer.allocateDirect(w*h*4);
        pointer = new WindowInput(w, h);
        statsStart = System.nanoTime();
        Thread input = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("STOP")) break;
                    if (line.length() > 160) { System.out.println("[window] INPUT_REJECTED oversized"); continue; }
                    synchronized(events) {
                        if (!events.offer(line)) { events.clear(); events.offer("RESET"); System.out.println("[window] INPUT_QUEUE_RESET overflow"); }
                    }
                }
            } catch (IOException failure) { System.out.println("[window] INPUT_PIPE_CLOSED " + failure); }
            finally { stop = true; }
        }, "wurm-window-input");
        input.setDaemon(true); input.start();
        System.out.println("[client] INPUT_READY protocol=1 sink=lwjgl2-queues");
        System.out.println("[window] WINDOW_READY GL=" + glGetString(GL_VERSION) + " renderer=" + glGetString(GL_RENDERER));
        return 1;
    }
    private static void owned() {
        if (owner != Thread.currentThread()) throw new IllegalStateException("EGL window must be used on its owning game thread");
    }
    public static long current() { return owner == Thread.currentThread() ? 1 : 0; }
    public static void makeCurrent(long window) {
        owned();
        if (window != 1) throw new UnsupportedOperationException("Context detachment / thread transfer is not yet qualified");
    }
    public static void resize(int w, int h) {
        owned(); NativeEgl.resize(w,h); width=w; height=h; pointer.resize(w,h);
        pixels = ByteBuffer.allocateDirect(w*h*4);
        System.out.println("[window] WINDOW_RESIZE " + w + "x" + h);
    }
    public static double x() { return pointer.x(); }
    public static double y() { return pointer.y(); }
    public static void cursor(double x, double y) { pointer.cursor(x,y); }
    public static void poll() {
        if (owner == null) return;
        owned();
        synchronized(events) {
            String line;
            while ((line = events.poll()) != null) {
                try { pointer.apply(line); }
                catch (IllegalArgumentException failure) { System.out.println("[window] INPUT_REJECTED " + failure.getMessage()); }
            }
        }
        if (stop) {
            pointer.release();
            org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(1, true);
        }
    }
    public static void swap() {
        owned();
        swaps++;
        long frameStart = System.nanoTime();
        if (frameStart-lastFrame >= 66_666_667L) {
            int before = glGetError();
            if (before != GL_NO_ERROR) throw new IllegalStateException("CLIENT_GL_ERROR before readback=0x"+Integer.toHexString(before));
            int alignment = glGetInteger(GL_PACK_ALIGNMENT);
            int rowLength = glGetInteger(GL_PACK_ROW_LENGTH), skipRows = glGetInteger(GL_PACK_SKIP_ROWS), skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
            try {
                glPixelStorei(GL_PACK_ALIGNMENT,1); pixels.clear();
                glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_ROWS,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0);
                long readStart = System.nanoTime();
                glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                readbackNanos += System.nanoTime() - readStart;
                int error = glGetError(), driver = NativeEgl.error();
                if (error != 0 || driver != 0) throw new IllegalStateException("FRAME_READBACK_ERROR GL="+error+" GLES="+driver);
                long publishStart = System.nanoTime();
                FrameFile.write(Path.of(System.getProperty("wurm.graphics.frame")),width,height,++sequence,pixels, pointer.displayX(), pointer.displayY(), pointer.visible(), pointer.applied());
                publishNanos += System.nanoTime() - publishStart; published++;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            finally {
                glPixelStorei(GL_PACK_ALIGNMENT,alignment); glPixelStorei(GL_PACK_ROW_LENGTH,rowLength);
                glPixelStorei(GL_PACK_SKIP_ROWS,skipRows); glPixelStorei(GL_PACK_SKIP_PIXELS,skipPixels);
            }
            // Readback/publication time is part of the interval, not an extra delay.
            lastFrame=frameStart;
            if (sequence == 1 || sequence%25 == 0) System.out.println("[window] WINDOW_FRAME sequence="+sequence+" size="+width+"x"+height);
        }
        long now = System.nanoTime();
        if (now - statsStart >= 5_000_000_000L) {
            double seconds = (now - statsStart) / 1e9;
            System.out.println(String.format(java.util.Locale.ROOT,
                "[window] FRAME_TIMING renderFps=%.1f presentedFps=%.1f readbackMs=%.2f publishMs=%.2f targetFps=15",
                swaps/seconds, published/seconds, readbackNanos/1e6/Math.max(1,published),
                publishNanos/1e6/Math.max(1,published)));
            statsStart=now; swaps=0; published=0; readbackNanos=0; publishNanos=0;
        }
        NativeEgl.swap();
        try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); stop=true; }
    }
    public static void close() {
        if (owner == null) return;
        owned(); pointer.release(); glFlush(); glFinish(); NativeEgl.close(); GL.destroy(); owner=null; pixels=null;
        System.out.println("[window] WINDOW_CLOSED frames="+sequence);
    }
}
