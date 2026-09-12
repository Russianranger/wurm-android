package server;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Runs in Ago's transforming classloader. Keeps the existing Android server/STOP entry point. */
public final class ServerModLaunch {
    public static void main(String[] args) throws Exception {
        ClassLoader loader=ServerModLaunch.class.getClassLoader();
        Object modLoader=loader.loadClass("org.gotti.wurmunlimited.modloader.ModLoader").getConstructor().newInstance();
        List<?> mods=(List<?>)modLoader.getClass().getMethod("loadModsFromModDir",Path.class).invoke(modLoader,Path.of("mods"));
        Class<?> hooks=loader.loadClass("org.gotti.wurmunlimited.modloader.server.ServerHook");
        Object hook=hooks.getMethod("createServerHook").invoke(null);
        hooks.getMethod("addMods",List.class).invoke(hook,mods);
        hooks.getMethod("addVersionHandler",String.class,String.class,List.class).invoke(hook,
            modLoader.getClass().getMethod("getVersion").invoke(modLoader),
            modLoader.getClass().getMethod("getGameVersion").invoke(modLoader),mods);
        Class<?> manager=loader.loadClass("org.gotti.wurmunlimited.modloader.classhooks.HookManager");
        manager.getMethod("initCallbacks").invoke(manager.getMethod("getInstance").invoke(null));
        Class<?> entry=loader.loadClass("org.gotti.wurmunlimited.modloader.interfaces.ModEntry");
        for(Object mod:mods) System.out.println("[mods] " + Instant.now() + " SERVER_MOD_READY " + entry.getMethod("getName").invoke(mod));
        System.out.println("[mods] " + Instant.now() + " SERVER_LOADER_READY count="+mods.size()+"; Android startup and shutdown retained");
        loader.loadClass("server.ManagedServerMain").getMethod("main",String[].class).invoke(null,(Object)args);
    }
}
