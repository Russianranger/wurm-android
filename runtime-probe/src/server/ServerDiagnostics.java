package server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** Supported JUL configuration plus observational snapshots. Does not intercept System.exit. */
public final class ServerDiagnostics {
    private static final AtomicBoolean requestedStop = new AtomicBoolean();
    private static final String CONFIG = "handlers=server.ServerLogHandler\n.level=INFO\n" +
        "com.wurmonline.server.LoginHandler.level=FINE\n";
    private ServerDiagnostics() { }

    public static void install() throws Exception {
        // Installed before any Wurm class initialization. Suppresses its file-only fallback.
        Path config = Path.of(System.getProperty("java.io.tmpdir"), "wurm-server-logging.properties");
        Files.writeString(config, CONFIG, StandardCharsets.UTF_8);
        System.setProperty("java.util.logging.config.file", config.toString());
        LogManager.getLogManager().readConfiguration();
        Logger.getLogger("").getHandlers(); // Force lazy handler creation while startup can report failure.
        if (Arrays.stream(Logger.getLogger("").getHandlers()).noneMatch(h -> h instanceof ServerLogHandler))
            throw new IllegalStateException("Managed server console handler was not loaded");
        System.err.println("[serverdiag] " + Instant.now() + " LOG_CONFIG_READY handler=server.ServerLogHandler; world files unchanged");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[serverdiag] " + Instant.now() + " JVM_SHUTDOWN_BEGIN requestedStop=" + requestedStop.get() +
                "; hook execution alone does not prove a save or identify the exit cause");
            threads("shutdown");
            System.err.println("[serverdiag] " + Instant.now() + " JVM_SHUTDOWN_END");
        }, "wurm-shutdown-evidence"));
    }
    public static void stopRequested() { requestedStop.set(true); }

    public static void capture() {
        System.err.println("[serverdiag] " + Instant.now() + " SNAPSHOT_BEGIN");
        try {
            LogManager manager = LogManager.getLogManager();
            int count = 0;
            for (String name : Collections.list(manager.getLoggerNames()).stream().sorted().toList()) {
                if (!name.isEmpty() && !name.startsWith("com.wurmonline")) continue;
                Logger logger = manager.getLogger(name);
                if (logger == null) continue;
                if (++count > 24) { System.err.println("[serverdiag] LOGGER_LIMIT 24"); break; }
                System.err.println("[serverdiag] LOGGER name=" + ServerLogHandler.clean(name) + " level=" + logger.getLevel() +
                    " parent=" + logger.getUseParentHandlers() + " handlers=" +
                    Arrays.toString(Arrays.stream(logger.getHandlers()).map(h -> h.getClass().getName()).toArray()));
            }
            threads("login-wait");
        } catch (Exception failure) { System.err.println("[serverdiag] SNAPSHOT_FAILED " + failure.getClass().getName()); }
        System.err.println("[serverdiag] " + Instant.now() + " SNAPSHOT_END");
    }
    private static boolean exiting(StackTraceElement[] stack) {
        return Arrays.stream(stack).anyMatch(f ->
            (f.getClassName().equals("java.lang.Shutdown") || f.getClassName().equals("java.lang.Runtime") ||
             f.getClassName().equals("java.lang.System")) && f.getMethodName().equals("exit"));
    }
    private static boolean relevant(Map.Entry<Thread, StackTraceElement[]> entry) {
        return exiting(entry.getValue()) || Arrays.stream(entry.getValue()).anyMatch(f ->
            f.getClassName().startsWith("com.wurmonline.") || f.getClassName().equals("poc.AndroidServerMain"));
    }
    private static void threads(String reason) {
        try {
            var entries = Thread.getAllStackTraces().entrySet().stream().filter(ServerDiagnostics::relevant)
                .sorted(Comparator.<Map.Entry<Thread, StackTraceElement[]>>comparingInt(e -> exiting(e.getValue()) ? 0 : 1)
                    .thenComparing(e -> e.getKey().getName())).toList();
            System.err.println("[serverdiag] THREAD_SNAPSHOT reason=" + reason + " relevant=" + entries.size() + " limit=12");
            for (var entry : entries.stream().limit(12).toList()) {
                System.err.println("[serverdiag] THREAD name=" + ServerLogHandler.clean(entry.getKey().getName()) +
                    " state=" + entry.getKey().getState() + " exitCaller=" + exiting(entry.getValue()));
                for (var frame : Arrays.stream(entry.getValue()).limit(12).toList())
                    System.err.println("[serverdiag] AT " + ServerLogHandler.clean(frame.toString()));
            }
        } catch (Exception failure) { System.err.println("[serverdiag] THREAD_SNAPSHOT_FAILED " + failure.getClass().getName()); }
    }
}
