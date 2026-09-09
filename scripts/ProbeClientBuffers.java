import client.ClientBuffers;
import client.ClientGraphicsPatch;
import java.lang.management.*;

/** Optional private probe with the real client; no game files required by public CI. */
public final class ProbeClientBuffers {
    public static void main(String[] args) throws Exception {
        ClientGraphicsPatch.verifySelected();
        ClientBuffers.preflight(); // Initialize options, static buffers and reflection first.
        BufferPoolMXBean pool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
            .filter(p -> p.getName().equals("direct")).findFirst().orElseThrow();
        long count = pool.getCount(), bytes = pool.getMemoryUsed();
        for (int i = 0; i < 16; i++) ClientBuffers.preflight();
        if (pool.getCount() != count || pool.getMemoryUsed() != bytes)
            throw new AssertionError("Direct allocations leaked: count=" + pool.getCount() + "/" + count + " bytes=" + pool.getMemoryUsed() + "/" + bytes);
        System.out.println("WURM_BUFFER_NATIVE_RELEASE_PASS rounds=16 kinds=4 directCount=" + count + " directBytes=" + bytes + " restored=true");
    }
}
