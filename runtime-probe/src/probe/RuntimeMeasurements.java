package probe;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Self-observation only, once per 30 seconds; never requests GC or walks heap objects. */
public final class RuntimeMeasurements {
    private RuntimeMeasurements() { }
    public static void start(String role) {
        try {
            Thread sampler = new Thread(() -> {
                boolean warned = false;
                while (!Thread.currentThread().isInterrupted()) {
                    try { System.out.println(sample(role, Path.of("/proc/self"))); }
                    catch (RuntimeException failure) {
                        if (!warned) System.out.println("[runtime-memory] " + Instant.now() + " role=" + role +
                            " SAMPLE_UNAVAILABLE type=" + failure.getClass().getSimpleName());
                        warned = true;
                    }
                    try { Thread.sleep(30_000); }
                    catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                }
            }, "wurm-memory-" + role);
            sampler.setDaemon(true); // Observations cannot hold up shutdown or own the game lifetime.
            sampler.start();
        } catch (RuntimeException failure) {
            System.out.println("[runtime-memory] " + Instant.now() + " role=" + role +
                " SAMPLER_UNAVAILABLE type=" + failure.getClass().getSimpleName());
        }
    }

    static String sample(String role, Path proc) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage(), nonHeap = memory.getNonHeapMemoryUsage();
        StringBuilder line = new StringBuilder("[runtime-memory] ").append(Instant.now())
            .append(" role=").append(role).append(" pid=").append(ProcessHandle.current().pid())
            .append(" uptimeMs=").append(ManagementFactory.getRuntimeMXBean().getUptime())
            .append(" heapUsedBytes=").append(heap.getUsed()).append(" heapCommittedBytes=").append(heap.getCommitted())
            .append(" heapMaxBytes=").append(heap.getMax()).append(" nonHeapUsedBytes=").append(nonHeap.getUsed())
            .append(" javaThreads=").append(ManagementFactory.getThreadMXBean().getThreadCount());
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (!pool.getName().equals("direct") && !pool.getName().equals("mapped")) continue;
            line.append(' ').append(pool.getName()).append("Count=").append(pool.getCount())
                .append(' ').append(pool.getName()).append("UsedBytes=").append(pool.getMemoryUsed());
        }
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            line.append(" gc[").append(gc.getName().replace(' ', '_')).append("]=")
                .append(gc.getCollectionCount()).append('/').append(gc.getCollectionTime());
        }
        Map<String, Long> status = readMetrics(proc.resolve("status"));
        for (String name : List.of("VmRSS", "VmHWM", "RssAnon", "VmSwap", "Threads"))
            line.append(' ').append(name).append(name.equals("Threads") ? "=" : "KiB=").append(status.getOrDefault(name, -1L));
        line.append(" PssKiB=").append(readMetrics(proc.resolve("smaps_rollup")).getOrDefault("Pss", -1L))
            .append(" fdCount=").append(countFiles(proc.resolve("fd")))
            .append(" gcUnits=count/ms unavailable=-1");
        return line.toString();
    }

    static Map<String, Long> readMetrics(Path path) {
        Map<String, Long> values = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(path)) {
            String line;
            for (int n = 0; n < 128 && (line = in.readLine()) != null; n++) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2 || !parts[0].endsWith(":")) continue;
                try { values.put(parts[0].substring(0, parts[0].length() - 1), Long.parseLong(parts[1])); }
                catch (NumberFormatException ignored) { /* Text fields, such as process name. */ }
            }
        } catch (IOException | SecurityException unavailable) { /* Android may restrict individual proc files. */ }
        return values;
    }

    static long countFiles(Path path) {
        long count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
            for (Path ignored : files) if (++count > 65536) return -1;
            return count;
        } catch (IOException | SecurityException | DirectoryIteratorException unavailable) { return -1; }
    }
}
