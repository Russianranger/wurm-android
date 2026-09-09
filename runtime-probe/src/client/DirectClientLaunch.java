package client;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

/** Adapter for the launch(PlayerProfile, Resources, boolean) ABI verified from the user's client. */
public final class DirectClientLaunch {
    private static void log(String text) { System.out.println("[client] " + text); }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Object call(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
    private static void requireCompatibility() throws Exception {
        for (String name : List.of("SteamJni.Steam_api", "com.wurmonline.client.launcherfx.WurmMain")) {
            Class<?> cls = type(name);
            if (!"local-v1".equals(cls.getMethod("androidCompatibilityVersion").invoke(null)))
                throw new IllegalStateException("CLIENT_COMPATIBILITY_NOT_ACTIVE " + name);
            log("COMPAT_CLASS " + name + " source=" + cls.getProtectionDomain().getCodeSource().getLocation());
        }
    }
    private static Object initializeSteam() throws Exception {
        requireCompatibility();
        return type("wurm.android.compat.ClientHooks").getMethod("initializeSteam").invoke(null);
    }
    public static void compatibilityProbe() throws Exception {
        Object handler = initializeSteam();
        type("wurm.android.compat.ClientHooks").getMethod("testTicket", Object.class).invoke(null, handler);
    }
    static List<String> selectPacks(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) throw new IllegalStateException("CLIENT_PACKS_MISSING: import the complete client packs/ directory");
        List<String> names;
        try (var files = Files.list(directory)) {
            names = new ArrayList<>(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".jar") && !n.startsWith("test_")).sorted().toList());
        }
        if (names.isEmpty()) throw new IllegalStateException("CLIENT_PACKS_MISSING: no local resource JARs in packs/");
        // Match the engine's default resource precedence, then include other locally supplied packs.
        List<String> ordered = new ArrayList<>();
        for (String name : List.of("sound.jar", "pmk.jar", "graphics.jar")) if (names.remove(name)) ordered.add(name);
        ordered.addAll(names);
        for (String name : ordered) {
            Path path = directory.resolve(name);
            try (JarFile jar = new JarFile(path.toFile())) {
                if (jar.size() == 0) throw new IllegalStateException("CLIENT_PACK_EMPTY " + name);
                log("RESOURCE_PACK " + name + " bytes=" + Files.size(path) + " entries=" + jar.size());
            }
        }
        return ordered;
    }
    public static void launch(Class<?> engine, Method launch) throws Exception {
        String player = System.getProperty("wurm.client.player", "Thor");
        if (!player.matches("[A-Za-z][A-Za-z0-9]{2,19}")) throw new IllegalArgumentException("Invalid local player name");
        log("ENTRY_ADAPTER profile-resources-v1; boolean=false matches desktop call; it is not an offline flag");
        Object steam = initializeSteam();
        engine.getField("steamHandler").set(null, steam);
        File packs = (File) type("com.wurmonline.client.settings.GlobalData").getMethod("getPackDirectory").invoke(null);
        List<String> packNames = selectPacks(packs.toPath());
        log("RESOURCE_PACKS_VALIDATED " + packNames + "; content compatibility still needs the engine");
        log("PROFILE_PREPARE player=" + player + " cwd=" + Path.of("").toAbsolutePath() + "; passwords blank in this preview");
        Class<?> settings = type("com.wurmonline.client.launcherfx.WurmSettingsFX");
        if (!"headless-keybinds-v1".equals(settings.getMethod("androidCompatibilityVersion").invoke(null)))
            throw new IllegalStateException("HEADLESS_SETTINGS_NOT_ACTIVE");
        log("SETTINGS_ADAPTER headless-keybinds-v1 source=" + settings.getProtectionDomain().getCodeSource().getLocation());
        Class<?> profileClass = type("com.wurmonline.client.settings.Profile");
        Object profile = profileClass.getMethod("getProfile").invoke(null);
        profileClass.getMethod("loadPlayer", String.class).invoke(profile, player);
        call(profile, "associateConfig");
        call(profile, "storeConfig");
        type("com.wurmonline.client.options.Options").getMethod("checkOptionsVersion").invoke(null);
        Object playerProfile = call(profile, "launchProfile");
        log("PROFILE_READY type=" + playerProfile.getClass().getName());
        Object resources = launch.getParameterTypes()[1].getConstructor(File.class, List.class).newInstance(packs, packNames);
        log("RESOURCES_READY packs=" + packNames + "; local assets; no updater/network download");
        engine.getMethod("setUsername", String.class).invoke(null, player);
        engine.getMethod("setPassword", String.class).invoke(null, "");
        engine.getMethod("setServerPassword", String.class).invoke(null, "");
        engine.getMethod("setWindowDirty", boolean.class).invoke(null, false);
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            uncaught.compareAndSet(null, failure);
            log("CLIENT_THREAD_FAILED thread=" + thread.getName() + " type=" + failure.getClass().getName());
            failure.printStackTrace(System.out);
        });
        log("ENTRY_INVOKE WurmClientBase.launch(PlayerProfile,Resources,false); connection target=127.0.0.1:3724");
        launch.invoke(null, playerProfile, resources, false);
        // launch returns after starting a thread. Do not exit/kill it as the old no-arg probe did.
        Field field = engine.getDeclaredField("gameThread"); field.setAccessible(true);
        Thread thread = (Thread) field.get(null);
        if (thread == null) throw new IllegalStateException("CLIENT_GAME_THREAD_MISSING");
        log("CLIENT_GAME_THREAD name=" + thread.getName() + "; awaiting graphics/window and connection logs");
        thread.join();
        log("CLIENT_GAME_THREAD_EXIT; Wurm login/world entry requires separate evidence");
        if (uncaught.get() != null) throw new IllegalStateException("CLIENT_ASYNC_STARTUP_FAILED", uncaught.get());
    }
}
