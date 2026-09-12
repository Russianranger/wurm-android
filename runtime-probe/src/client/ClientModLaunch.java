package client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Runs in the transforming loader, on top of the already prepared Android overlay classpath. */
public final class ClientModLaunch {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader = ClientModLaunch.class.getClassLoader();
        stage("android-overlay-check");
        ClientGraphicsPatch.verifySelected();
        Files.createDirectories(Path.of("mods"));
        stage("mods-initialize");
        Class<?> api = loader.loadClass("org.gotti.wurmunlimited.modloader.ModLoader");
        Object modLoader = api.getConstructor().newInstance();
        // Exact descriptors avoid resolving unrelated public signatures before instrumentation.
        var lookup = MethodHandles.publicLookup();
        List<?> mods = (List<?>) lookup.findVirtual(api, "loadModsFromModDir",
            MethodType.methodType(List.class, Path.class)).invoke(modLoader, Path.of("mods"));
        stage("callbacks-initialize");
        Class<?> manager = loader.loadClass("org.gotti.wurmunlimited.modloader.classhooks.HookManager");
        manager.getMethod("initCallbacks").invoke(manager.getMethod("getInstance").invoke(null));
        Class<?> entry = loader.loadClass("org.gotti.wurmunlimited.modloader.interfaces.ModEntry");
        var name = lookup.findVirtual(entry, "getName", MethodType.methodType(String.class));
        for (Object mod : mods) System.out.println("[mods] " + Instant.now() + " CLIENT_MOD_READY " + name.invoke(mod));
        System.out.println("[mods] " + Instant.now() + " CLIENT_LOADER_READY count=" + mods.size() + "; Android entry retained");
        stage("android-entry");
        ClientBootstrap.main(args);
    }
    private static void stage(String name) {
        System.out.println("[mods] " + Instant.now() + " CLIENT_LOADER_STAGE " + name);
    }
}
