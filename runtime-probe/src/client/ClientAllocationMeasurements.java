package client;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.*;

/** Approximate heap allocation by thread, every 30 seconds. No stack/heap walks,
 * GC requests or references to game objects. Missing counters never block play. */
public final class ClientAllocationMeasurements {
    static final int LIMIT = 256, TOP = 4;
    private ClientAllocationMeasurements() { }

    public static void start() {
        try {
            if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean) ||
                    !bean.isThreadAllocatedMemorySupported()) {
                unavailable("unsupported"); return;
            }
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            Thread sampler = new Thread(() -> {
                Window window = new Window();
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        System.out.println(sample(bean, window));
                        System.out.println(ClientTextBuffers.sample());
                        Thread.sleep(30_000);
                    }
                } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                catch (RuntimeException | LinkageError failure) { unavailable(failure.getClass().getSimpleName()); }
            }, "wurm-client-allocations");
            sampler.setDaemon(true);
            sampler.start();
        } catch (RuntimeException | LinkageError failure) { unavailable(failure.getClass().getSimpleName()); }
    }

    private static void unavailable(String reason) {
        System.out.println("[client-allocation] " + Instant.now() + " UNAVAILABLE reason=" + reason + "; gameplay continues");
    }

    record Allocation(long id, long bytes) { }
    record Sample(long elapsedMs, long bytes, int matched, int fresh, int missing,
                  int departed, int omitted, List<Allocation> top) { }

    static final class Window {
        private Map<Long, Long> previous = Map.of();
        private long previousNanos;
        private boolean started;

        Sample observe(long now, long[] ids, long[] allocated, int omitted) {
            if (ids.length != allocated.length || ids.length > LIMIT) throw new IllegalArgumentException("allocation sample size");
            Map<Long, Long> next = new HashMap<>();
            List<Allocation> changes = new ArrayList<>();
            int matched = 0, fresh = 0, missing = 0;
            long total = 0;
            for (int i = 0; i < ids.length; i++) {
                if (allocated[i] < 0) { missing++; continue; }
                next.put(ids[i], allocated[i]);
                Long before = previous.get(ids[i]);
                if (before == null || allocated[i] < before) { fresh++; continue; }
                long bytes = allocated[i] - before;
                matched++; total += bytes;
                if (bytes > 0) changes.add(new Allocation(ids[i], bytes));
            }
            int departed = 0;
            for (long id : previous.keySet()) if (!next.containsKey(id)) departed++;
            changes.sort(Comparator.comparingLong(Allocation::bytes).reversed().thenComparingLong(Allocation::id));
            long elapsed = started ? Math.max(0, (now - previousNanos) / 1_000_000) : 0;
            previous = next; previousNanos = now; started = true;
            return new Sample(elapsed, total, matched, fresh, missing, departed, omitted,
                List.copyOf(changes.subList(0, Math.min(TOP, changes.size()))));
        }
        int retainedThreads() { return previous.size(); }
    }

    static String sample(ThreadMXBean bean, Window window) {
        if (!bean.isThreadAllocatedMemoryEnabled()) throw new UnsupportedOperationException("allocation counters disabled");
        long[] ids = bean.getAllThreadIds();
        int omitted = Math.max(0, ids.length - LIMIT);
        if (omitted > 0) ids = Arrays.copyOf(ids, LIMIT);
        Sample sample = window.observe(System.nanoTime(), ids, bean.getThreadAllocatedBytes(ids), omitted);
        StringBuilder line = new StringBuilder("[client-allocation] ").append(Instant.now())
            .append(" pid=").append(ProcessHandle.current().pid())
            .append(" windowMs=").append(sample.elapsedMs()).append(" observedAllocatedBytes=").append(sample.bytes())
            .append(" matchedThreads=").append(sample.matched()).append(" newOrResetThreads=").append(sample.fresh())
            .append(" unavailableThreads=").append(sample.missing()).append(" departedOrUnavailableThreads=").append(sample.departed())
            .append(" omittedThreads=").append(sample.omitted());
        for (Allocation allocation : sample.top()) {
            // Zero stack depth; fetch names only for the four largest contributors.
            ThreadInfo info = bean.getThreadInfo(allocation.id(), 0);
            line.append(" thread[").append(allocation.id()).append(',')
                .append(info == null ? "ended" : safeName(info.getThreadName()))
                .append("]=").append(allocation.bytes());
        }
        return line.append(" units=heap-bytes/window approximate=true coverage=matched-live-threads-only; not retained memory or allocation stacks").toString();
    }

    static String safeName(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(48, name.length()); i++) {
            char c = name.charAt(i);
            out.append(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '.' ? c : '_');
        }
        return out.toString();
    }
}
