package server;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/** Optional entry before any Wurm class is resolved. Loader code stays in the child JVM, never D8. */
public final class ServerModBootstrap {
    public static void main(String[] args) {
        try {
            ServerDiagnostics.install();
            System.out.println("[mods] " + Instant.now() + " SERVER_LOADER_BEGIN ago=0.47 javassist=3.30.2-GA");
            Class<?> managerClass=Class.forName("org.gotti.wurmunlimited.modloader.classhooks.HookManager");
            Object manager=managerClass.getMethod("getInstance").invoke(null);
            ClassLoader loader=(ClassLoader)managerClass.getMethod("getLoader").invoke(manager);
            Class<?> api=Class.forName("javassist.Loader");
            // One copy of JDK/native driver and console evidence classes. Game classes and the POC
            // must stay in the transforming loader; delegating them would silently skip mod hooks.
            for(String prefix:new String[]{"javafx.","com.sun.","sun.","jdk.","javax.","org.w3c.","org.xml.",
                    "org.sqlite.","com.mysql.","javassist.","org.gotti.wurmunlimited.modloader.classhooks.",
                    "server.ServerDiagnostics","server.ServerLogHandler"})
                api.getMethod("delegateLoadingOf",String.class).invoke(loader,prefix);
            Thread.currentThread().setContextClassLoader(loader);
            loader.loadClass("server.ServerModLaunch").getMethod("main",String[].class).invoke(null,(Object)args);
        } catch(Throwable failure) {
            while(failure instanceof InvocationTargetException && failure.getCause()!=null) failure=failure.getCause();
            System.err.println("[mods] " + Instant.now() + " SERVER_LOADER_FAILED " + failure);
            java.util.logging.Logger.getLogger("server.mods").log(java.util.logging.Level.SEVERE, "Mod startup failed", failure);
            System.exit(1); // Never fall back to opening a modded world without its selected mods.
        }
    }
}
