package server;

import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/** Console output belongs to the Android supervisor; never opens or changes world files. */
public final class ServerLogHandler extends Handler {
    private static final java.util.regex.Pattern SENSITIVE = java.util.regex.Pattern.compile("(?i)\\b(password|passwd|pwd|ticket|token|secret|sessionkey)\\b");
    private static java.nio.file.Path evidenceFile;
    private static final StringBuilder evidence = new StringBuilder();
    private static int warnings, severe;
    private static boolean writeFailed;
    static synchronized void initializeEvidence() throws Exception {
        String configured = System.getProperty("wurm.server.firstErrors");
        if (configured == null) return;
        evidenceFile = java.nio.file.Path.of(configured);
        warnings = severe = 0; writeFailed = false; evidence.setLength(0);
        evidence.append("First server errors captured ").append(Instant.now()).append("\n")
            .append("Session: ").append(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).getParent().getFileName())
            .append("\nFirst four WARNING and four SEVERE records, independently retained; each bounded to 6000 characters.\n");
        saveEvidence();
    }
    private static void saveEvidence() throws Exception {
        java.nio.file.Path pending = evidenceFile.resolveSibling(evidenceFile.getFileName() + ".pending");
        try {
            java.nio.file.Files.writeString(pending, evidence.toString());
            java.nio.file.Files.move(pending, evidenceFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { java.nio.file.Files.deleteIfExists(pending); }
    }
    private static synchronized void retain(LogRecord record, String formatted) {
        if (evidenceFile == null || record.getLevel().intValue() < Level.WARNING.intValue()) return;
        boolean high = record.getLevel().intValue() >= Level.SEVERE.intValue();
        if (high ? severe >= 4 : warnings >= 4) return;
        if (high) severe++; else warnings++;
        evidence.append("\n").append(formatted, 0, Math.min(6000, formatted.length())).append("\n");
        try { saveEvidence(); }
        catch (Exception failure) {
            if (!writeFailed) System.err.println("[serverdiag] FIRST_ERRORS_WRITE_FAILED " + failure.getClass().getName());
            writeFailed = true;
        }
    }
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
            StringBuilder text = new StringBuilder("[server-log] " + record.getInstant() + " " + record.getLevel() + " " +
                clean(record.getLoggerName()) + " " + clean(formatter.formatMessage(record)) + "\n");
            Throwable cause = record.getThrown();
            for (int depth = 0; cause != null && depth < 3; depth++, cause = cause.getCause()) {
                text.append("[server-log] CAUSE ").append(cause.getClass().getName()).append(": ").append(clean(cause.getMessage())).append("\n");
                StackTraceElement[] stack = cause.getStackTrace();
                for (int i = 0; i < Math.min(8, stack.length); i++) text.append("[server-log] AT ").append(clean(stack[i].toString())).append("\n");
            }
            String formatted = text.toString();
            retain(record, formatted); // Separate file survives console rotation and LogManager reloads.
            System.err.print(formatted);
        } catch (Exception failure) {
            System.err.println("[server-log] " + Instant.now() + " FORMAT_FAILED " + failure.getClass().getName());
        }
    }
    @Override public void flush() { System.err.flush(); }
    @Override public void close() { flush(); } // LogManager shutdown must not close stderr.
}
