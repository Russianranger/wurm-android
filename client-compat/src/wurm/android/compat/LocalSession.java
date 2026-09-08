package wurm.android.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;

/** Local personal-server identity. This is not a Steam credential or Steamworks implementation. */
public final class LocalSession {
    private LocalSession() {}
    public static void requireLocal() {
        if (!Boolean.getBoolean("wurm.client.offline") ||
                !"127.0.0.1".equals(System.getProperty("wurm.client.host")) ||
                !"3724".equals(System.getProperty("wurm.client.port")))
            throw new IllegalStateException("LOCAL_SHIM_REQUIRES explicit offline mode and 127.0.0.1:3724");
    }
    public static synchronized String identity() {
        requireLocal();
        Path file = Path.of(System.getProperty("user.home"), "wurm-local-identity.txt");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                long id = 76561198000000000L + new SecureRandom().nextInt(1_000_000_000);
                Files.writeString(file, Long.toString(id), StandardOpenOption.CREATE_NEW);
            }
            String id = Files.readString(file).trim();
            if (!id.matches("7656119[0-9]{10}")) throw new IOException("Invalid persisted local identity; refusing to silently replace it");
            return id;
        } catch (IOException e) { throw new IllegalStateException("LOCAL_IDENTITY_FAILED", e); }
    }
    public static byte[] ticket() {
        return ("WURM_ANDROID_LOCAL_V1:" + identity()).getBytes(StandardCharsets.US_ASCII);
    }
}
