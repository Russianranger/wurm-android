package client;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/** Enters Ago before game, graphics bridge or JNI owner classes resolve. Never runs its desktop launcher. */
public final class ClientModBootstrap {
    public static void main(String[] args) {
        try {
            if (args.length != 1 || !args[0].equals("entry"))
                throw new IllegalArgumentException("Client mods are only supported in the game entry process");
            System.out.println("[mods] " + Instant.now() + " CLIENT_LOADER_BEGIN ago=0.15 javassist=3.30.2-GA");
            Class<?> managerClass = Class.forName("org.gotti.wurmunlimited.modloader.classhooks.HookManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            ClassLoader loader = (ClassLoader) managerClass.getMethod("getLoader").invoke(manager);
            Class<?> api = Class.forName("javassist.Loader");
            for (String prefix : new String[]{"javafx.", "com.sun.", "sun.", "jdk.", "javax.",
                    "org.w3c.", "org.xml.", "org.sqlite.", "com.mysql.", "javassist.",
                    "org.gotti.wurmunlimited.modloader.classhooks."})
                api.getMethod("delegateLoadingOf", String.class).invoke(loader, prefix);
            // Keep client.*, wurm.graphics.*, org.lwjgl.* and Wurm in ONE transforming loader.
            // The graphics bridge resolves game helpers with Class.forName; parent-delegating it
            // would create a second game/GL state and bypass the installed HUD hooks.
            Thread.currentThread().setContextClassLoader(loader);
            loader.loadClass("client.ClientModLaunch").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Throwable failure) {
            while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
            System.err.println("[mods] " + Instant.now() + " CLIENT_LOADER_FAILED " + failure);
            failure.printStackTrace(System.err);
            System.exit(42); // Match the client bootstrap failure code; never silently skip selected mods.
        }
    }
}
