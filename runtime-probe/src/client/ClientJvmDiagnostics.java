package client;

import java.lang.management.ManagementFactory;
import java.util.List;

/** Observe the real child VM, never infer collector selection from a requested flag alone. */
public final class ClientJvmDiagnostics {
    private ClientJvmDiagnostics() { }
    public static void describe() {
        String expected = System.getProperty("wurm.client.expectedGc", "default");
        List<String> names = ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> b.getName()).toList();
        System.out.println("[client-jvm] COLLECTOR expected=" + expected + " actual=" + names +
            " pid=" + ProcessHandle.current().pid() + " vm=" + System.getProperty("java.vm.info") +
            " maxHeap=" + Runtime.getRuntime().maxMemory());
        boolean matches = switch (expected) {
            case "g1" -> names.stream().anyMatch(n -> n.startsWith("G1 "));
            case "serial" -> names.contains("Copy") && names.contains("MarkSweepCompact");
            case "default" -> true;
            default -> false;
        };
        if (!matches) throw new IllegalStateException("CLIENT_COLLECTOR_MISMATCH expected=" + expected + " actual=" + names);
    }
    static long collections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
    }
}
