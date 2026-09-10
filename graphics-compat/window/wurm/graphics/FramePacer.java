package wurm.graphics;

/** One rendered/published frame per deadline; missed deadlines never trigger catch-up bursts. */
public final class FramePacer {
    private long interval, next;
    private int fps;
    public FramePacer(int fps) { setFps(fps); }
    public void setFps(int value) {
        if (value != 15 && value != 30) throw new IllegalArgumentException("Frame target must be 15 or 30");
        fps=value; interval=1_000_000_000L/value; next=0;
    }
    public int fps() { return fps; }
    public long delay(long now) { return next == 0 ? 0 : Math.max(0, next-now); }
    public void presented(long now) {
        next = next == 0 || now-next >= interval ? now+interval : next+interval;
    }
}
