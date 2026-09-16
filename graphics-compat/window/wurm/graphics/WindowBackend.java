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
    private static FramePacer pacer;
    private static long statsStart, readbackNanos, publishNanos, workNanos, pacingNanos, setupNanos, restoreNanos, swapNanos;
    private static long previousEnd, maxWorkNanos, maxReadbackNanos;
    private static int workSamples;
    private static FrameFile.RawWriter writer;
    private static int swaps, published;
    private static ByteBuffer pixels;
    private static final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(512);
    private static volatile boolean stop;
    private static WindowInput pointer;
    private static final boolean VERBOSE = Boolean.getBoolean("wurm.diagnostics.verbose");

    public static long open(int w, int h) {
        if (owner != null) throw new IllegalStateException("Only one EGL window is supported");
        System.out.println("[window] WINDOW_CREATE requested=" + w + "x" + h + " backend=Pojav-Java-GLFW/EGL-pbuffer RGBA8 prefer-depth24; no desktop window manager");
        NativeEgl.open(System.getProperty("wurm.graphics.library"), w, h);
        owner = Thread.currentThread(); width = w; height = h;
        GL.create(System.getProperty("wurm.graphics.library"));
        GL.createCapabilities();
        pixels = ByteBuffer.allocateDirect(w*h*4);
        pointer = new WindowInput(w, h);
        writer = new FrameFile.RawWriter(Path.of(System.getProperty("wurm.graphics.frame")));
        resetTiming();
        pacer = new FramePacer(Integer.getInteger("wurm.graphics.fps", 30));
        reportFps();
        Thread input = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("STOP")) break;
                    if (line.length() > 520) { System.out.println("[window] INPUT_REJECTED oversized"); continue; }
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
    private static void resetTiming() {
        statsStart=System.nanoTime();previousEnd=0;swaps=published=workSamples=0;
        readbackNanos=publishNanos=workNanos=pacingNanos=setupNanos=restoreNanos=swapNanos=maxWorkNanos=maxReadbackNanos=0;
    }
    private static void reportFps() {
        resetTiming();
        System.out.println("[client-ui] FPS_APPLIED target="+pacer.fps()+" time="+java.time.Instant.now()+" pid="+ProcessHandle.current().pid());
    }
    public static long current() { return owner == Thread.currentThread() ? 1 : 0; }
    public static void makeCurrent(long window) {
        owned();
        if (window != 1) throw new UnsupportedOperationException("Context detachment / thread transfer is not yet qualified");
    }
    public static void resize(int w, int h) {
        owned(); NativeEgl.resize(w,h); width=w; height=h; pointer.resize(w,h);
        pixels = ByteBuffer.allocateDirect(w*h*4);
        resetTiming();
        System.out.println("[window] WINDOW_RESIZE " + w + "x" + h);
    }
    public static double x() { return pointer.x(); }
    public static double y() { return pointer.y(); }
    public static void cursor(double x, double y) { pointer.cursor(x,y); }
    private static void hud(String action) {
        if (System.getProperty("wurm.client.offscreenOverlay") == null) return;
        try { Class.forName("client.ClientHudVisibility").getMethod("command",String.class).invoke(null,action); }
        catch (ReflectiveOperationException failure) {
            System.out.println("[client-ui] HUD_UNAVAILABLE "+(failure.getCause() == null ? failure : failure.getCause()));
        }
    }
    public static void poll() {
        if (owner == null) return;
        owned();
        synchronized(events) {
            String line;
            while ((line = events.peek()) != null) {
                // A 240-character paste produces 480 LWJGL events. Do not silently
                // drop the tail (including Enter) into its 200-event destination.
                if (!pointer.canApply(line)) break;
                events.poll();
                try {
                    if (line.startsWith("FPS ")) {
                        pacer.setFps(Integer.parseInt(line.substring(4)));
                        reportFps();
                    }
                    else if (line.equals("MEMORY start") || line.equals("MEMORY stop")) {
                        try { Class.forName("client.ClientJobProfiler").getMethod("command",String.class).invoke(null,line.substring(7)); }
                        catch (ReflectiveOperationException failure) { System.out.println("[client-memory-test] UNAVAILABLE command-dispatch="+failure.getClass().getSimpleName()); }
                    }
                    else if (line.equals("HUD restore-focus") || line.equals("HUD restore-button")) hud(line.substring(4));
                    else if (line.startsWith("VISUAL ")) {
                        String preset=line.substring(7);
                        try { Class.forName("client.ClientVisualOptions").getMethod("apply", String.class).invoke(null,preset); }
                        catch (ReflectiveOperationException failure) { System.out.println("[client-ui] GRAPHICS_FAILED " + failure); }
                    } else if (line.startsWith("BINDS ")) {
                        try { Class.forName("client.ClientKeybindings").getMethod("command",String.class).invoke(null,line.substring(6)); }
                        catch (ReflectiveOperationException failure) { System.out.println("[client-ui] KEYBINDS_FAILED "+failure); }
                    } else pointer.apply(line);
                }
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
        long entered=System.nanoTime();
        if(previousEnd!=0){long work=entered-previousEnd;workNanos+=work;maxWorkNanos=Math.max(maxWorkNanos,work);workSamples++;}
        swaps++;
        long delay=pacer.delay(entered);
        if (delay > 0) try { Thread.sleep(delay/1_000_000L, (int)(delay%1_000_000L)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); stop=true; return; }
        long frameStart = System.nanoTime();
        pacingNanos+=frameStart-entered;
        {
            int before = glGetError();
            if (before != GL_NO_ERROR) throw new IllegalStateException("CLIENT_GL_ERROR before readback=0x"+Integer.toHexString(before)+
                " frame="+(sequence+1)+" time="+java.time.Instant.now()+"; pending before capture; see graphics-error origin/candidates");
            int alignment = glGetInteger(GL_PACK_ALIGNMENT);
            int rowLength = glGetInteger(GL_PACK_ROW_LENGTH), skipRows = glGetInteger(GL_PACK_SKIP_ROWS), skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
            try {
                glPixelStorei(GL_PACK_ALIGNMENT,1); pixels.clear();
                glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_ROWS,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0);
                long readStart = System.nanoTime();
                setupNanos+=readStart-frameStart;
                glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                long readElapsed=System.nanoTime()-readStart;
                readbackNanos+=readElapsed;maxReadbackNanos=Math.max(maxReadbackNanos,readElapsed);
                int error = glGetError(), driver = NativeEgl.error();
                if (error != 0 || driver != 0) throw new IllegalStateException("FRAME_READBACK_ERROR GL="+error+" GLES="+driver);
                long publishStart = System.nanoTime();
                setupNanos+=publishStart-readStart-readElapsed;
                writer.write(width,height,++sequence,pixels, pointer.displayX(), pointer.displayY(), pointer.visible(), pointer.applied());
                publishNanos += System.nanoTime() - publishStart; published++;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            finally {
                long restoreStart=System.nanoTime();
                glPixelStorei(GL_PACK_ALIGNMENT,alignment); glPixelStorei(GL_PACK_ROW_LENGTH,rowLength);
                glPixelStorei(GL_PACK_SKIP_ROWS,skipRows); glPixelStorei(GL_PACK_SKIP_PIXELS,skipPixels);
                restoreNanos+=System.nanoTime()-restoreStart;
            }
            pacer.presented(frameStart);
            if (sequence == 1 || (VERBOSE && sequence%25 == 0)) System.out.println("[window] WINDOW_FRAME sequence="+sequence+" size="+width+"x"+height);
        }
        long swapStart=System.nanoTime();
        NativeEgl.swap();
        long now = System.nanoTime();
        swapNanos+=now-swapStart;
        if (now - statsStart >= 5_000_000_000L) {
            double seconds = (now - statsStart) / 1e9;
            System.out.println(String.format(java.util.Locale.ROOT,
                "[window] FRAME_TIMING time=%s pid=%d renderFps=%.1f presentedFps=%.1f readbackMs=%.2f publishMs=%.2f targetFps=%d clientWorkMs=%.2f pacingMs=%.2f captureSetupMs=%.2f captureRestoreMs=%.2f eglSwapMs=%.2f maxClientWorkMs=%.2f maxReadbackMs=%.2f samples=%d workSamples=%d size=%dx%d",
                java.time.Instant.now(), ProcessHandle.current().pid(), swaps/seconds, published/seconds, readbackNanos/1e6/Math.max(1,published),
                publishNanos/1e6/Math.max(1,published), pacer.fps(),workNanos/1e6/Math.max(1,workSamples),
                pacingNanos/1e6/Math.max(1,published),setupNanos/1e6/Math.max(1,published),restoreNanos/1e6/Math.max(1,published),
                swapNanos/1e6/Math.max(1,published),maxWorkNanos/1e6,maxReadbackNanos/1e6,published,workSamples,width,height));
            hud("observe");
            resetTiming();
        }
        previousEnd=System.nanoTime();
    }
    public static void close() {
        if (owner == null) return;
        owned(); pointer.release(); glFlush(); glFinish(); NativeEgl.close(); GL.destroy(); writer.close();writer=null;owner=null;pixels=null;
        System.out.println("[window] WINDOW_CLOSED frames="+sequence);
    }
}
