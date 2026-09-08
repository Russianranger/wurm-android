package client;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Direct client bootstrap and independent linkage probes; never starts launcherfx.WurmMain. */
public final class ClientBootstrap {
    private static final String ENGINE = "com.wurmonline.client.WurmClientBase";
    private static void log(String s) { System.out.println("[client] " + s); }
    public static void main(String[] args) {
        String mode = args.length == 1 ? args[0] : "invalid";
        log("JVM_STARTED java=" + System.getProperty("java.version") + " arch=" + System.getProperty("os.arch"));
        log("MODE " + mode + " target=127.0.0.1:3724; Wurm protocol connection NOT established");
        int exit = 0;
        try {
            switch (mode) {
                case "inventory" -> inventory();
                case "graphics" -> graphics();
                case "entry" -> entry();
                case "input" -> DesktopInput.diagnostic();
                default -> throw new IllegalArgumentException("Unknown bootstrap mode");
            }
        } catch (Throwable failure) {
            if (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
            log("BOOTSTRAP_FAILED stage=" + mode + " type=" + failure.getClass().getName());
            failure.printStackTrace(System.out); exit = 42;
        }
        log("BOOTSTRAP_EXIT stage=" + mode + " code=" + exit);
        System.exit(exit); // Do not leave partial client startup threads behind.
    }
    private static void inventory() throws Exception {
        log("ENTRY_CANDIDATE " + ENGINE + ".launch; descriptor must be verified from imported bytes");
        log("STEAM_SHIM not-installed; collecting actual client ABI before implementing a compatible local shim");
        int classes = 0;
        Set<String> wanted = new LinkedHashSet<>(List.of(ENGINE.replace('.', '/') + ".class",
            "com/wurmonline/client/launcherfx/WurmMain.class", "org/lwjgl/opengl/Display.class",
            "org/lwjgl/input/Keyboard.class", "org/lwjgl/input/Mouse.class", "org/lwjgl/openal/AL.class"));
        Set<String> emitted = new HashSet<>();
        for (String item : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path jar = Path.of(item);
            if (!Files.isRegularFile(jar) || !item.endsWith(".jar")) continue;
            try (JarFile z = new JarFile(jar.toFile())) {
                var entries = z.entries();
                while (entries.hasMoreElements()) {
                    var e = entries.nextElement(); String name = e.getName();
                    boolean steam = name.startsWith("SteamJni/") && name.endsWith(".class");
                    if (!(wanted.contains(name) || steam) || !emitted.add(name)) continue;
                    if (++classes > 64) { log("ABI_LIMIT 64 classes"); return; }
                    ClassInventory.Info info;
                    try (InputStream in = z.getInputStream(e)) { info = ClassInventory.read(in); }
                    log("ABI_CLASS " + info.name() + " jar=" + jar.getFileName());
                    if (steam) for (var f : info.fields()) log("ABI_FIELD flags=" + f.flags() + " " + f.name() + " " + f.descriptor());
                    int shown = 0;
                    for (var m : info.methods()) {
                        if ((steam || m.name().equals("launch") || m.name().equals("main") || m.name().equals("<init>") || (m.flags() & 0x100) != 0) && shown++ < 160)
                            log("ABI_METHOD flags=" + m.flags() + " " + m.name() + m.descriptor());
                    }
                    for (String ref : info.references())
                        if (ref.startsWith("javafx/") || ref.startsWith("SteamJni/") || ref.startsWith("org/lwjgl/") || ref.startsWith("net/java/games/input/")) log("ABI_DEPENDENCY " + ref);
                }
            }
        }
        if (!emitted.contains(ENGINE.replace('.', '/') + ".class")) throw new ClassNotFoundException(ENGINE);
        log("INVENTORY_COMPLETE classes=" + classes);
    }
    private static void graphics() throws Exception {
        log("GRAPHICS_ATTEMPT imported LWJGL2 Display.create; Android window/GL bridge is not installed yet");
        log("NATIVE_PATH " + System.getProperty("java.library.path"));
        Class<?> display = Class.forName("org.lwjgl.opengl.Display");
        try {
            display.getMethod("create").invoke(null);
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11");
            log("OPENGL_VERSION " + gl.getMethod("glGetString", int.class).invoke(null, 0x1f02));
            log("DISPLAY_CREATED; no Wurm world rendered by this probe");
        } finally { try { display.getMethod("destroy").invoke(null); } catch (Throwable ignored) {} }
    }
    private static void entry() throws Exception {
        log("ENTRY_INITIALIZE " + ENGINE + " (desktop JavaFX launcher bypassed)");
        Class<?> cls = Class.forName(ENGINE, true, ClientBootstrap.class.getClassLoader());
        log("ENTRY_INITIALIZED " + ENGINE);
        // Only a verified zero-argument static launch has an unambiguous call contract.
        // Never invent false/null/login/password arguments for another descriptor.
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals("launch")) log("ENTRY_SIGNATURE " + m.toGenericString());
            if (m.getName().equals("launch") && Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0) {
                log("ENTRY_INVOKE " + ENGINE + ".launch()"); m.invoke(null); return;
            }
        }
        throw new UnsupportedOperationException("ENTRY_ABI_REQUIRED: no public static launch(); export ABI_METHOD descriptors to implement the exact launch adapter. No guessed arguments were supplied.");
    }
}
