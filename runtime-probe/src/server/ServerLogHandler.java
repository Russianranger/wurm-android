package server;

import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/** Console output belongs to the Android supervisor; never opens or changes world files. */
public final class ServerLogHandler extends Handler {
    private static final java.util.regex.Pattern SENSITIVE = java.util.regex.Pattern.compile("(?i)\\b(password|passwd|pwd|ticket|token|secret|sessionkey)\\b");
    private final SimpleFormatter formatter = new SimpleFormatter();
    public ServerLogHandler() { setLevel(Level.ALL); }

    static String clean(String text) {
        if (text == null) return "";
        // Omit potentially sensitive messages, including formatted parameters, before bounding.
        if (SENSITIVE.matcher(text).find())
            return "[credential/ticket message omitted]";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(1200, text.length()); i++) {
            char c = text.charAt(i); result.append(Character.isISOControl(c) ? ' ' : c);
        }
        return result.toString();
    }
    @Override public synchronized void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) return;
        try {
            System.err.println("[server-log] " + record.getInstant() + " " + record.getLevel() + " " +
                clean(record.getLoggerName()) + " " + clean(formatter.formatMessage(record)));
            Throwable cause = record.getThrown();
            for (int depth = 0; cause != null && depth < 3; depth++, cause = cause.getCause()) {
                System.err.println("[server-log] CAUSE " + cause.getClass().getName() + ": " + clean(cause.getMessage()));
                StackTraceElement[] stack = cause.getStackTrace();
                for (int i = 0; i < Math.min(8, stack.length); i++) System.err.println("[server-log] AT " + clean(stack[i].toString()));
            }
        } catch (Exception failure) {
            System.err.println("[server-log] " + Instant.now() + " FORMAT_FAILED " + failure.getClass().getName());
        }
    }
    @Override public void flush() { System.err.flush(); }
    @Override public void close() { flush(); } // LogManager shutdown must not close stderr.
}
