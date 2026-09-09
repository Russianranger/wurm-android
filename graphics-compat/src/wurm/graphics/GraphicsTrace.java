package wurm.graphics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in, bounded breadcrumbs around the real core GL20 calls. No shader text is logged. */
public final class GraphicsTrace {
    private static final boolean ENABLED = Boolean.getBoolean("wurm.graphics.trace");
    private static final int LIMIT = 4096;
    private static final AtomicLong sequence = new AtomicLong();
    private static void log(String line) {
        System.out.println("[graphics-trace] " + line);
        System.out.flush(); // A native abort cannot run Java finally/shutdown hooks.
    }
    public static long begin(String operation, int object, int detail) {
        if (!ENABLED) return 0;
        long id = sequence.incrementAndGet();
        if (id > LIMIT) {
            if (id == LIMIT + 1) log("TRACE_LIMIT calls=" + LIMIT);
            return 0;
        }
        log("BEGIN seq=" + id + " op=" + operation + " object=" + object + " detail=" + detail +
            " thread=" + Thread.currentThread().getId());
        return id;
    }
    public static void end(long id) { if (id != 0) log("END seq=" + id); }
    public static void failed(long id, Throwable error) {
        if (id != 0) log("THREW seq=" + id + " type=" + error.getClass().getName());
    }
    public static void source(int shader, CharSequence source) {
        if (!ENABLED || sequence.get() >= LIMIT || source == null) return;
        if (source.length() > 1024 * 1024) { log("SOURCE shader=" + shader + " hash=omitted-size-limit"); return; }
        try {
            byte[] bytes = source.toString().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            log("SOURCE shader=" + shader + " bytes=" + bytes.length + " sha256=" + hex);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
