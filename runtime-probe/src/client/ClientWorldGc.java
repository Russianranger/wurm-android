package client;

import java.io.IOException;

/** Redirect only World.tick's explicit GC request; all other GC paths are retained. */
public final class ClientWorldGc {
    static final String WORLD="com/wurmonline/client/game/World.class";
    static final String ORIGINAL="af81f62dc45950de1c053951823754e6b90f05fae81a961ab176966b8589dedf";
    static byte[] prepare(byte[] original) throws Exception {
        return ClientGraphicsPatch.redirect(original,ORIGINAL,"java/lang/System","client/ClientWorldGc");
    }
    static void verify(byte[] changed) throws Exception {
        byte[] restored=ClientGraphicsPatch.redirect(changed,ClientGraphicsPatch.sha(changed),"client/ClientWorldGc","java/lang/System");
        if(!ClientGraphicsPatch.sha(restored).equals(ORIGINAL)) throw new IOException("WORLD_GC_PATCH_INTEGRITY_FAILED");
    }
    public static void reportPolicy() {
        System.out.println("[client-gc] "+java.time.Instant.now()+" WORLD_GC_POLICY skipPeriodic="+Boolean.getBoolean("wurm.client.skipPeriodicGc")+"; allocation/direct-memory pressure, other callers and shutdown GC retained");
    }
    public static void gc() {
        boolean skip=Boolean.getBoolean("wurm.client.skipPeriodicGc");
        System.out.println("[client-gc] "+java.time.Instant.now()+" WORLD_GC_REQUEST action="+(skip?"skipped":"collect")+" caller=World.tick");
        if(!skip) System.gc();
    }
}
