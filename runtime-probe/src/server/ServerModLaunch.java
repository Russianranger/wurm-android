package server;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Runs in Ago's transforming classloader. Keeps the existing Android server/STOP entry point. */
public final class ServerModLaunch {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader=ServerModLaunch.class.getClassLoader();
        stage("mods-initialize");
        Object modLoader=loader.loadClass("org.gotti.wurmunlimited.modloader.ModLoader").getConstructor().newInstance();
        List<?> mods=(List<?>)modLoader.getClass().getMethod("loadModsFromModDir",Path.class).invoke(modLoader,Path.of("mods"));
        Class<?> hooks=loader.loadClass("org.gotti.wurmunlimited.modloader.server.ServerHook");
        stage("hooks-install");
        // Class.getMethod/getDeclaredMethod enumerates method signatures. ServerHook's unrelated
        // public methods mention Communicator/Player, which would load and freeze game classes
        // before createServerHook has instrumented them. Resolve only each required descriptor.
        MethodHandles.Lookup lookup=MethodHandles.publicLookup();
        Object hook=lookup.findStatic(hooks,"createServerHook",MethodType.methodType(hooks)).invoke();
        lookup.findVirtual(hooks,"addMods",MethodType.methodType(void.class,List.class)).invoke(hook,mods);
        lookup.findVirtual(hooks,"addVersionHandler",MethodType.methodType(void.class,String.class,String.class,List.class)).invoke(hook,
            modLoader.getClass().getMethod("getVersion").invoke(modLoader),
            modLoader.getClass().getMethod("getGameVersion").invoke(modLoader),mods);
        stage("callbacks-initialize");
        Class<?> manager=loader.loadClass("org.gotti.wurmunlimited.modloader.classhooks.HookManager");
        manager.getMethod("initCallbacks").invoke(manager.getMethod("getInstance").invoke(null));
        Class<?> entry=loader.loadClass("org.gotti.wurmunlimited.modloader.interfaces.ModEntry");
        for(Object mod:mods) System.out.println("[mods] " + Instant.now() + " SERVER_MOD_READY " + entry.getMethod("getName").invoke(mod));
        System.out.println("[mods] " + Instant.now() + " SERVER_LOADER_READY count="+mods.size()+"; Android startup and shutdown retained");
        stage("android-entry");
        loader.loadClass("server.ManagedServerMain").getMethod("main",String[].class).invoke(null,(Object)args);
    }
    private static void stage(String name) {
        System.out.println("[mods] " + Instant.now() + " SERVER_LOADER_STAGE " + name);
    }
}
