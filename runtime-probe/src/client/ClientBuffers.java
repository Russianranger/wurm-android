package client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.util.Map;

/** Java 8 -> 17 Cleaner ABI relocation for two inspected, privately imported classes. */
public final class ClientBuffers {
    static final Map<String, String> ORIGINALS = Map.of(
        "com/wurmonline/client/util/BufferUtil.class", "037c786dd552cf0b3a6a22c1c9761296528564ebca80171605e2446bac563076",
        "com/wurmonline/client/util/BufferUtil$Cleaner.class", "bba71961402b766edddde5b164e8e170b3b2091af632532d83dd8605f6573d8d");
    private static final String OLD = "sun/misc/Cleaner", CURRENT = "jdk/internal/ref/Cleaner";
    private static void log(String s) { System.out.println("[client] " + s); }

    // Exact UTF8 constants only: one owner and one return descriptor per pinned
    // class. All instructions, counters, branches and attachment traversal stay intact.
    static byte[] relocate(byte[] bytes, String expected, boolean reverse) throws Exception {
        String from = reverse ? CURRENT : OLD, to = reverse ? OLD : CURRENT;
        byte[] owner = ClientGraphicsPatch.redirect(bytes, expected, from, to);
        return ClientGraphicsPatch.redirect(owner, ClientGraphicsPatch.sha(owner), "()L" + from + ";", "()L" + to + ";");
    }
    static byte[] prepare(String name, byte[] original) throws Exception {
        String expected = ORIGINALS.get(name);
        if (expected == null || !expected.equals(ClientGraphicsPatch.sha(original)))
            throw new IOException("CLIENT_BUFFER_PATCH_UNSUPPORTED class=" + name + " sha256=" + ClientGraphicsPatch.sha(original));
        byte[] changed = relocate(original, expected, false);
        log("BUFFER_PATCH_VERIFIED class=" + name + " originalSha256=" + expected + " overlayClassSha256=" + ClientGraphicsPatch.sha(changed));
        return changed;
    }
    static void verify(String name, byte[] changed) throws Exception {
        byte[] restored = relocate(changed, ClientGraphicsPatch.sha(changed), true);
        if (!ClientGraphicsPatch.sha(restored).equals(ORIGINALS.get(name)))
            throw new IOException("CLIENT_BUFFER_PATCH_INTEGRITY_FAILED class=" + name);
    }
    public static void verifyRuntime() throws Exception {
        Module base = Object.class.getModule(), caller = ClientBuffers.class.getModule();
        for (String pkg : new String[]{"sun.nio.ch", "jdk.internal.ref"}) {
            boolean exported = base.isExported(pkg, caller);
            log("BUFFER_MODULE package=" + pkg + " exported=" + exported);
            if (!exported) throw new IllegalStateException("CLIENT_BUFFER_EXPORT_REQUIRED --add-exports=java.base/" + pkg + "=ALL-UNNAMED");
        }
        Class<?> direct = Class.forName("sun.nio.ch.DirectBuffer");
        String cleaner = direct.getMethod("cleaner").getReturnType().getName();
        if (!cleaner.equals("jdk.internal.ref.Cleaner")) throw new IllegalStateException("CLIENT_BUFFER_RUNTIME_ABI_UNSUPPORTED " + cleaner);
        Class.forName(cleaner).getMethod("clean");
        log("BUFFER_RUNTIME_ABI cleaner=" + cleaner + "; scoped exports; no blanket opens");
    }
    public static void preflight() throws Exception {
        verifyRuntime();
        Class<?> buffers = Class.forName("com.wurmonline.client.util.BufferUtil");
        Method allocated = buffers.getMethod("getAllocatedMemory"), free = buffers.getMethod("deallocate", Buffer.class);
        int baseline = (Integer) allocated.invoke(null);
        String[] kinds = {"Byte", "Float", "Int", "Double"};
        int[] widths = {1, 4, 4, 8};
        for (int i = 0; i < kinds.length; i++) {
            int bytes = 256 * widths[i];
            Buffer buffer = (Buffer) buffers.getMethod("new" + kinds[i] + "Buffer", int.class).invoke(null, 256);
            boolean allocationValid = buffer.isDirect() && buffer.capacity() == 256 && (Integer) allocated.invoke(null) == baseline + bytes;
            free.invoke(null, buffer); // Never access this buffer's memory after cleanup.
            if (!allocationValid || (Integer) allocated.invoke(null) != baseline)
                throw new IllegalStateException("CLIENT_BUFFER_ACCOUNTING_FAILED kind=" + kinds[i]);
            log("BUFFER_RELEASE_PASS kind=" + kinds[i] + " bytes=" + bytes + " allocationCounterRestored=true");
        }
        log("BUFFER_PREFLIGHT_PASS kinds=4; real imported allocation/cleanup; game textures still need startup");
    }
}
