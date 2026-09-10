package wurm.graphics;

import client.DesktopInput;
import org.lwjgl.input.GLFWInputImplementation;

/** Owned game-thread input state. Coordinates are GLFW top-left pixels. */
public final class WindowInput {
    private final DesktopInput parser = new DesktopInput();
    private final boolean[] keys = new boolean[256], buttons = new boolean[8];
    private int width, height, applied;
    private double x, y;

    public WindowInput(int width, int height) {
        resize(width, height); cursor(width / 2.0, height / 2.0);
    }
    public void resize(int width, int height) { this.width = width; this.height = height; }
    public double x() { return x; }
    public double y() { return y; }
    public void cursor(double x, double y) { this.x = x; this.y = y; }
    public int applied() { return applied; }
    public boolean visible() { return !GLFWInputImplementation.singleton.grab; }
    public int displayX() { return (int)Math.max(0, Math.min(width - 1, x)); }
    public int displayY() { return (int)Math.max(0, Math.min(height - 1, y)); }

    public void apply(String line) {
        DesktopInput.Event e = parser.accept(line);
        GLFWInputImplementation sink = GLFWInputImplementation.singleton;
        long now = System.nanoTime();
        switch (e.kind()) {
            case "KEY" -> { keys[e.code()] = e.x() == 1; sink.putKeyboardEvent(e.code(), (byte)e.x(), 0, now, false); }
            case "BUTTON" -> {
                if (e.code() >= buttons.length) throw new IllegalArgumentException("Unsupported mouse button");
                buttons[e.code()] = e.x() == 1;
                sink.putMouseEventWithCoords((byte)e.code(), (byte)e.x(), (int)x, (int)y, 0, now);
            }
            case "MOVE" -> {
                x += e.x(); y -= e.y(); // Controller Y is LWJGL-up; GLFW Y is down.
                if (!sink.grab) { x = Math.max(0,Math.min(width-1,x)); y = Math.max(0,Math.min(height-1,y)); }
                move(sink, now);
            }
            case "POINT" -> {
                x = e.x() * (width - 1); y = e.y() * (height - 1);
                move(sink, now);
            }
            case "WHEEL" -> sink.putMouseEventWithCoords((byte)-1, (byte)0, (int)x, (int)y, (int)e.x(), now);
            case "RESET" -> release();
            default -> throw new IllegalArgumentException("Unknown event");
        }
        if (applied < Integer.MAX_VALUE) applied++;
        if (!e.kind().equals("MOVE") && !e.kind().equals("POINT"))
            System.out.println("[window] INPUT_APPLIED " + e + " sink=lwjgl2-queues");
    }
    private void move(GLFWInputImplementation sink, long now) {
        sink.putMouseEventWithCoords((byte)-1, (byte)0, (int)x, (int)y, 0, now);
    }
    public void release() {
        GLFWInputImplementation sink = GLFWInputImplementation.singleton;
        for (int k=0;k<keys.length;k++) if (keys[k]) { sink.putKeyboardEvent(k,(byte)0,0,System.nanoTime(),false); keys[k]=false; }
        for (int b=0;b<buttons.length;b++) if (buttons[b]) { sink.putMouseEventWithCoords((byte)b,(byte)0,(int)x,(int)y,0,System.nanoTime()); buttons[b]=false; }
    }
}
