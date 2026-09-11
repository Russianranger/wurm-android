package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Small control adapter around the byte-identical, source-backed POC JAR. */
public final class ManagedServerMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One world name required");
        ServerDiagnostics.install();
        ServerSqlitePatch.verifySelected();
        ServerLoginPatch.verifySelected();
        probe.RuntimeMeasurements.start("server");
        // Resolve the API without initializing Wurm or opening its databases.
        // The public mod launcher hooks this exact ()V signature; imported
        // versions without it fail here instead of starting an unmanageable world.
        Class<?> server = Class.forName("com.wurmonline.server.Server", false,
                                       ManagedServerMain.class.getClassLoader());
        Method shutdown = server.getDeclaredMethod("shutDown");
        if (shutdown.getReturnType() != void.class) throw new NoSuchMethodException("Server.shutDown()V");
        shutdown.setAccessible(true);
        Method instance = server.getMethod("getInstance");
        System.out.println("[managed] Shutdown API resolved: Server.shutDown()V; saves require device verification.");
        java.util.concurrent.atomic.AtomicBoolean inspecting = new java.util.concurrent.atomic.AtomicBoolean();
        Thread controls = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if ("INSPECT".equals(command) || "DIAGNOSE".equals(command)) {
                        boolean world = "INSPECT".equals(command);
                        if (inspecting.compareAndSet(false, true)) {
                            Thread observation = new Thread(() -> {
                                try {
                                    ServerDiagnostics.capture();
                                    if (world) WorldProbe.capture();
                                } finally { inspecting.set(false); }
                            }, "wurm-world-observation");
                            observation.setDaemon(true); // Inspection must never delay STOP or JVM exit.
                            observation.start();
                        }
                        continue;
                    }
                    if (!"STOP".equals(command)) continue;
                    ServerDiagnostics.stopRequested();
                    System.out.println("[managed] SHUTDOWN_REQUESTED");
                    shutdown.invoke(instance.invoke(null));
                    System.out.println("[managed] SHUTDOWN_RETURNED");
                    System.exit(0); // POC otherwise intentionally keeps its main thread alive.
                }
            } catch (Throwable failure) {
                System.err.println("[managed] SHUTDOWN_FAILED; use Force Stop only if necessary.");
                failure.printStackTrace();
            }
        }, "wurm-app-control");
        controls.setDaemon(true);
        controls.start();
        try {
            Class.forName("poc.AndroidServerMain").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            failure.getCause().printStackTrace();
            System.exit(1); // Do not leave partial startup threads orphaned.
        }
    }
}
