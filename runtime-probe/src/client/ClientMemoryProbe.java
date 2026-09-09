package client;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/** Bounded, game-free JVM exercise. Uses normal Java allocation/GC; no Unsafe or native graphics. */
public final class ClientMemoryProbe {
    private static volatile long consumed;
    public static final class Kernel {
        private static final int[] values = {3, 17, 91, 23, 51, 7, 19, 101};
        public static int mix(int seed) {
            int result = seed;
            for (int i = 0; i < 24; i++) result = Integer.rotateLeft(result ^ values[i & 7], 5) + i;
            return result;
        }
    }
    private static final class Loader extends ClassLoader {
        Class<?> kernel(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    private ClientMemoryProbe() { }
    public static void run() throws Exception {
        if (ClientMemoryProbe.class.getClassLoader().getResource("com/wurmonline/client/WurmClientBase.class") != null ||
            ClientMemoryProbe.class.getClassLoader().getResource("org/lwjgl/opengl/GL20.class") != null)
            throw new IllegalStateException("MEMORY_PROBE_CLASSPATH_NOT_ISOLATED");
        long before = ClientJvmDiagnostics.collections(), started = System.nanoTime();
        long unloaded = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
        byte[] kernel;
        try (var in = ClientMemoryProbe.class.getResourceAsStream("ClientMemoryProbe$Kernel.class")) {
            if (in == null) throw new IllegalStateException("Memory kernel missing");
            kernel = in.readNBytes(16 * 1024);
        }
        System.out.println("[memory] BEGIN rounds=16; heapChurnMiB=256; directChurnMiB=32; disposableLoaders=64; Wurm/graphics absent");
        for (int round = 0; round < 16; round++) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() - started > TimeUnit.SECONDS.toNanos(75))
                throw new IllegalStateException("MEMORY_PROBE_BUDGET_EXCEEDED");
            exercise(round, kernel);
            System.gc(); // Exercise reclamation/class unloading before reporting success.
            if (round == 0 || (round + 1) % 4 == 0)
                System.out.println("[memory] PROGRESS round=" + (round + 1) + " collections=" + ClientJvmDiagnostics.collections());
        }
        long collected = ClientJvmDiagnostics.collections() - before;
        if (collected <= 0) throw new IllegalStateException("MEMORY_PROBE_NO_COLLECTION_OBSERVED");
        var compiler = ManagementFactory.getCompilationMXBean();
        System.out.println("[memory] MEMORY_PROBE_PASS collector=" + System.getProperty("wurm.client.expectedGc") +
            " collections=" + collected + " compilerMs=" +
            (compiler != null && compiler.isCompilationTimeMonitoringSupported() ? compiler.getTotalCompilationTime() : -1) +
            " unloadedClasses=" + (ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount() - unloaded) +
            " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) +
            "; bounded JVM test only; native graphics and Wurm login not tested");
    }
    private static void exercise(int round, byte[] kernel) throws Exception {
        byte[][] ring = new byte[8][];
        for (int i = 0; i < 256; i++) {
            byte[] bytes = new byte[64 * 1024];
            bytes[0] = (byte) i; bytes[bytes.length - 1] = (byte) ~i;
            ring[i & 7] = bytes;
        }
        for (int i = 248; i < 256; i++) {
            byte[] bytes = ring[i & 7];
            if (bytes[0] != (byte) i || bytes[bytes.length - 1] != (byte) ~i) throw new AssertionError("Heap sentinel changed");
        }
        for (int i = 0; i < 16; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(128 * 1024);
            long marker = 0x1122334455667788L ^ round ^ i;
            buffer.putLong(0, marker); buffer.putLong(buffer.capacity() - 8, ~marker);
            if (buffer.getLong(0) != marker || buffer.getLong(buffer.capacity() - 8) != ~marker)
                throw new AssertionError("Direct buffer sentinel changed");
        }
        for (int i = 0; i < 4; i++) {
            Method method = new Loader().kernel(kernel).getMethod("mix", int.class);
            for (int value = 0; value < 20_000; value++) {
                int result = (Integer) method.invoke(null, value);
                if (result != Kernel.mix(value)) throw new AssertionError("Compiled result changed");
                consumed += result;
            }
        }
    }
}
