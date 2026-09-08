package com.wurmonline.client.launcherfx;

import com.wurmonline.client.console.ConsoleListenerClass;
import com.wurmonline.client.console.WurmConsoleOutputStream;
import wurm.android.compat.LocalSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/** Utility surface required by the engine, without JavaFX Application or desktop launcher UI. */
public final class WurmMain {
    private static final Properties flags = readFlags();
    public static final boolean IS_RELEASE = flag("release-mode");
    public static final boolean IS_TEST_CLIENT = flag("test-client");
    public static final boolean IS_TEST_UNSTABLE = flag("unstable-client");
    public static final boolean USES_TEST_SERVER = flag("uses-test-server");
    public static String serverIp = "127.0.0.1";
    public static int serverPort = 3724;
    public static final String imageResources = "/com/wurmonline/client/images";
    private static final StringBuilder recent = new StringBuilder();
    private static final WurmConsoleOutputStream console = new WurmConsoleOutputStream(System.out);
    private static File consoleFile;
    static {
        LocalSession.requireLocal();
        console.addCopy(new ConsoleListenerClass() {
            public void consoleOutput(String text) { capture(text); }
            public void consoleClosed() {}
        });
        PrintStream output = new PrintStream(console, true, StandardCharsets.UTF_8);
        System.setOut(output); System.setErr(output);
        System.out.println("[client] LAUNCHER_REPLACEMENT local-v1; JavaFX UI bypassed; imported console preserved");
    }
    private static Properties readFlags() {
        Properties p = new Properties();
        try (InputStream in = WurmMain.class.getResourceAsStream("/com/wurmonline/client/client.properties")) {
            if (in == null) throw new IOException("Missing client.properties in imported client");
            p.load(in); return p;
        } catch (IOException e) { throw new IllegalStateException("CLIENT_FLAGS_UNAVAILABLE", e); }
    }
    private static boolean flag(String name) { return Boolean.parseBoolean(flags.getProperty(name, "false")); }
    private static synchronized void capture(String text) {
        recent.append(text).append('\n');
        if (recent.length() > 65536) recent.delete(0, recent.length() - 65536);
        if (consoleFile != null) {
            try {
                if (consoleFile.length() > 2 * 1024 * 1024) Files.writeString(consoleFile.toPath(), recent);
                else Files.writeString(consoleFile.toPath(), text + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) { consoleFile = null; } // Parent Android log remains authoritative.
        }
    }
    public static String androidCompatibilityVersion() { return "local-v1"; }
    public static WurmConsoleOutputStream getConsoleOutputStream() { return console; }
    public static synchronized String getLogContents() { return recent.toString(); }
    public static synchronized void logToFile(File file) {
        try {
            Files.createDirectories(file.toPath().toAbsolutePath().getParent());
            Files.writeString(file.toPath(), recent); consoleFile = file;
        } catch (IOException e) { System.out.println("[client] CONSOLE_FILE_FAILED " + e); }
    }
    public static String getServerIp() { LocalSession.requireLocal(); return "127.0.0.1"; }
    public static int getServerPort() { LocalSession.requireLocal(); return 3724; }
    public static boolean isFirstLaunch() { return false; }
}
