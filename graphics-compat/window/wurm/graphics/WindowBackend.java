package wurm.graphics;

import client.DesktopInput;
import java.io.*;
import java.nio.*;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import org.lwjgl.input.GLFWInputImplementation;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL11.*;

/** Single owned game thread, one real EGL context, bounded diagnostic readback. */
public final class WindowBackend {
    private static Thread owner;
    private static int width, height, sequence;
    private static long lastFrame;
    private static ByteBuffer pixels;
    private static final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(512);
    private static final DesktopInput parser = new DesktopInput();
    private static volatile boolean stop;
    private static final boolean[] keys = new boolean[256], buttons = new boolean[8];
    private static double cursorX, cursorY;

    public static long open(int w, int h) {
        if (owner != null) throw new IllegalStateException("Only one EGL window is supported");
        System.out.println("[window] WINDOW_CREATE requested=" + w + "x" + h + " backend=Pojav-Java-GLFW/EGL-pbuffer RGBA8 depth16; no desktop window manager");
        NativeEgl.open(System.getProperty("wurm.graphics.library"), w, h);
        owner = Thread.currentThread(); width = w; height = h;
        GL.create(System.getProperty("wurm.graphics.library"));
        GL.createCapabilities();
        pixels = ByteBuffer.allocateDirect(w*h*4);
        cursorX = w/2.0; cursorY = h/2.0;
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
        owned(); NativeEgl.resize(w,h); width=w; height=h;
        pixels = ByteBuffer.allocateDirect(w*h*4);
        System.out.println("[window] WINDOW_RESIZE " + w + "x" + h);
    }
    public static double x() { return cursorX; }
    public static double y() { return cursorY; }
    public static void cursor(double x, double y) { cursorX=x; cursorY=y; }
    public static void poll() {
        if (owner == null) return;
        owned();
        synchronized(events) {
            String line;
            while ((line = events.poll()) != null) {
                try { apply(parser.accept(line)); }
                catch (IllegalArgumentException failure) { System.out.println("[window] INPUT_REJECTED " + failure.getMessage()); }
            }
        }
        if (stop) {
            release();
            org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(1, true);
        }
    }
    private static void apply(DesktopInput.Event e) {
        GLFWInputImplementation sink = GLFWInputImplementation.singleton;
        long now = System.nanoTime();
        switch (e.kind()) {
            case "KEY" -> { keys[e.code()] = e.x() == 1; sink.putKeyboardEvent(e.code(), (byte)e.x(), 0, now, false); }
            case "BUTTON" -> { buttons[e.code()] = e.x() == 1; sink.putMouseEventWithCoords((byte)e.code(), (byte)e.x(), -1,-1,0,now); }
            case "MOVE" -> {
                // Controller protocol Y is desktop/LWJGL-up; GLFW's coordinates are down.
                cursorX += e.x(); cursorY -= e.y();
                if (!sink.grab) { cursorX=Math.max(0,Math.min(width-1,cursorX)); cursorY=Math.max(0,Math.min(height-1,cursorY)); }
                sink.putMouseEventWithCoords((byte)-1,(byte)0,(int)cursorX,(int)cursorY,0,now);
            }
            case "WHEEL" -> sink.putMouseEventWithCoords((byte)-1,(byte)0,-1,-1,(int)e.x(),now);
            case "RESET" -> release();
            default -> throw new IllegalArgumentException("Unknown event");
        }
        if (!e.kind().equals("MOVE")) System.out.println("[window] INPUT_APPLIED " + e + " sink=lwjgl2-queues");
    }
    private static void release() {
        GLFWInputImplementation sink = GLFWInputImplementation.singleton;
        for (int k=0;k<keys.length;k++) if (keys[k]) { sink.putKeyboardEvent(k,(byte)0,0,System.nanoTime(),false); keys[k]=false; }
        for (int b=0;b<buttons.length;b++) if (buttons[b]) { sink.putMouseEventWithCoords((byte)b,(byte)0,-1,-1,0,System.nanoTime()); buttons[b]=false; }
    }
    public static void swap() {
        owned();
        if (System.nanoTime()-lastFrame >= 200_000_000L) {
            int before = glGetError();
            if (before != GL_NO_ERROR) throw new IllegalStateException("CLIENT_GL_ERROR before readback=0x"+Integer.toHexString(before));
            int alignment = glGetInteger(GL_PACK_ALIGNMENT);
            int rowLength = glGetInteger(GL_PACK_ROW_LENGTH), skipRows = glGetInteger(GL_PACK_SKIP_ROWS), skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
            try {
                glPixelStorei(GL_PACK_ALIGNMENT,1); pixels.clear();
                glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_ROWS,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0);
                glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                int error = glGetError(), driver = NativeEgl.error();
                if (error != 0 || driver != 0) throw new IllegalStateException("FRAME_READBACK_ERROR GL="+error+" GLES="+driver);
                FrameFile.write(Path.of(System.getProperty("wurm.graphics.frame")),width,height,++sequence,pixels);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            finally {
                glPixelStorei(GL_PACK_ALIGNMENT,alignment); glPixelStorei(GL_PACK_ROW_LENGTH,rowLength);
                glPixelStorei(GL_PACK_SKIP_ROWS,skipRows); glPixelStorei(GL_PACK_SKIP_PIXELS,skipPixels);
            }
            lastFrame=System.nanoTime();
            if (sequence == 1 || sequence%25 == 0) System.out.println("[window] WINDOW_FRAME sequence="+sequence+" size="+width+"x"+height);
        }
        NativeEgl.swap();
        try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); stop=true; }
    }
    public static void close() {
        if (owner == null) return;
        owned(); release(); glFlush(); glFinish(); NativeEgl.close(); GL.destroy(); owner=null; pixels=null;
        System.out.println("[window] WINDOW_CLOSED frames="+sequence);
    }
}
