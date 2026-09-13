package client;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Read-only observations of the inspected engine ABI. Never sends packets or changes auth state. */
public final class ClientConnectionMonitor implements AutoCloseable {
    private final Class<?> engine;
    private final Thread game;
    private final Consumer<String> log;
    private final Map<Class<?>, Map<String, Field>> fields = new HashMap<>();
    private final long started = System.nanoTime();
    private volatile boolean closed;
    private Thread observer;

    ClientConnectionMonitor(Class<?> engine, Thread game, Consumer<String> log) {
        this.engine = engine; this.game = game; this.log = log;
    }
    public static ClientConnectionMonitor start(Class<?> engine, Thread game) {
        ClientConnectionMonitor monitor = new ClientConnectionMonitor(engine, game, System.out::println);
        monitor.observer = new Thread(monitor::observe, "wurm-connection-observer");
        monitor.observer.setDaemon(true); monitor.observer.start();
        return monitor;
    }
    private Object read(Class<?> type, Object object, String name) throws ReflectiveOperationException {
        Map<String, Field> cache = fields.computeIfAbsent(type, ignored -> new HashMap<>());
        Field field = cache.get(name);
        if (field == null) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                try { field = parent.getDeclaredField(name); break; }
                catch (NoSuchFieldException absent) { }
            }
            if (field == null) throw new NoSuchFieldException(type.getName() + "." + name);
            field.setAccessible(true); cache.put(name, field);
        }
        return field.get(object);
    }
    private Object read(Object object, String name) throws ReflectiveOperationException { return read(object.getClass(), object, name); }
    private boolean flag(Object object, String name) throws ReflectiveOperationException { return (Boolean) read(object, name); }
    private String message(Object object, String name) throws ReflectiveOperationException { return clean(read(object, name)); }
    static String clean(Object value) {
        String text = value == null ? "" : value.toString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(240, text.length()); i++) {
            char c = text.charAt(i); out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
    record Sample(String phase, String detail) {
        String line(long elapsed) { return "[connection] STATE phase=" + phase + " elapsedMs=" + elapsed + " " + detail; }
    }
    /** Traffic counters are periodic telemetry, not connection state changes. */
    static final class LogGate {
        private static final java.util.regex.Pattern COUNTERS = java.util.regex.Pattern.compile(
            "\\b(payloadQueued|bytesRead|pendingBytes)=[0-9]+|\\bwriting=(true|false)");
        private static final java.util.regex.Pattern COUNTDOWN = java.util.regex.Pattern.compile("Trying again in [0-9]+ seconds");
        private static final java.util.regex.Pattern DOTS = java.util.regex.Pattern.compile("[.]+(?= |$)");
        private final boolean verbose;
        private String previous;
        private long last;
        LogGate(boolean verbose) { this.verbose = verbose; }
        boolean emit(Sample sample, long now) {
            String detail = DOTS.matcher(sample.detail()).replaceAll(".");
            detail = COUNTDOWN.matcher(detail).replaceAll("Retry countdown");
            int messages = detail.indexOf(" startup=");
            if (!verbose && messages >= 0) detail = COUNTERS.matcher(detail.substring(0, messages)).replaceAll("") + detail.substring(messages);
            String key = sample.phase() + " " + detail;
            if (!key.equals(previous) || now - last >= 5_000_000_000L) { previous = key; last = now; return true; }
            return false;
        }
    }
    Sample sample() throws ReflectiveOperationException {
        Object client = read(engine, null, "clientObject");
        if (client == null) return new Sample("INITIALIZING", "engineInstance=false");
        Object splash = read(client, "startupRenderer");
        String startup = splash == null ? "" : message(splash, "startupMessage");
        Object connection = read(client, "serverConnection");
        if (connection == null) return new Sample("INITIALIZING", "engineInstance=true startup=" + startup);
        boolean connecting = flag(connection, "isConnecting"), auth = flag(connection, "steamAuthenticateSucces");
        boolean loggedIn = flag(connection, "loggedIn"), disconnected = flag(connection, "disconnected");
        String authMessage = message(connection, "steamAuthenticateFailedMessage");
        String loginMessage = message(connection, "loginStatus"), disconnectMessage = message(connection, "disconnectReason");
        Object transport = read(connection, "connection");
        boolean connected = transport != null && flag(transport, "connected");
        String phase;
        if (disconnected) phase = "DISCONNECTED";
        else if (splash != null && flag(splash, "isReconnect")) phase = "RETRY_WAIT";
        else if (auth && loggedIn && !connecting && connected) phase = splash == null ? "GAME_LOOP" : "LOGIN_ACCEPTED";
        else if (connecting) phase = auth ? "LOGIN_WAIT" : "AUTH_WAIT";
        else if (!loginMessage.isEmpty() && !loggedIn) phase = "LOGIN_DENIED";
        else if (!authMessage.isEmpty() && !auth) phase = "AUTH_DENIED";
        else phase = "SOCKET_CONNECTING";
        String detail = "connecting=" + connecting + " authenticated=" + auth + " loggedIn=" + loggedIn +
            " splash=" + (splash != null) + " disconnected=" + disconnected + " transport=" + connected;
        if (transport != null) {
            // These counters are payloads queued/read, not proof of wire delivery or login.
            ByteBuffer write = (ByteBuffer) read(transport, "writeBuffer_w"), pending = (ByteBuffer) read(transport, "writeBuffer_r");
            int queued = (write == null ? 0 : write.position()) + (pending == null ? 0 : pending.remaining());
            detail += " payloadQueued=" + read(transport, "totalBytesWritten") + " bytesRead=" + read(transport, "bytesRead") +
                " pendingBytes=" + queued + " writing=" + flag(transport, "writing");
        }
        detail += " startup=" + startup + " authMessage=" + authMessage + " loginMessage=" + loginMessage + " disconnectMessage=" + disconnectMessage;
        return new Sample(phase, detail);
    }
    private void observe() {
        log.accept("[connection] MONITOR_READY target=127.0.0.1:3724 mode=read-only; counters are snapshots, not wire/login proof");
        LogGate gate = new LogGate(Boolean.getBoolean("wurm.diagnostics.verbose"));
        long stack = 0;
        try {
            while (!closed && game.isAlive()) {
                Sample value = sample();
                long now = System.nanoTime(), elapsed = (now - started) / 1_000_000;
                if (gate.emit(value, now)) log.accept(value.line(elapsed));
                if (!value.phase().equals("GAME_LOOP") && now - stack >= 15_000_000_000L) {
                    stack = now;
                    log.accept("[connection] GAME_THREAD state=" + game.getState() + " elapsedMs=" + elapsed);
                    StackTraceElement[] frames = game.getStackTrace();
                    for (int i = 0; i < Math.min(12, frames.length); i++) log.accept("[connection] GAME_STACK " + frames[i]);
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
        catch (Exception | LinkageError failure) {
            log.accept("[connection] MONITOR_UNAVAILABLE " + failure.getClass().getName() + ": " + clean(failure.getMessage()));
        } finally { log.accept("[connection] MONITOR_STOPPED; no auth/login state was modified"); }
    }
    @Override public void close() {
        closed = true;
        if (observer != null) {
            observer.interrupt();
            try { observer.join(1500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
