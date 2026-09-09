package server;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.jar.*;

/** Replace one removed encoder owner in the user's inspected login class. */
public final class ServerLoginPatch {
    static final String ENTRY = "com/wurmonline/server/LoginHandler.class";
    static final String ORIGINAL = "00fb374c584cf66311057967c6e0c9fe7bd17932e5aee218100a520fdfd4a73e";
    static final String OLD = "sun/misc/BASE64Encoder";
    static final String NEW = "server/LegacyBase64Encoder";
    private static final int LIMIT = 4 * 1024 * 1024;

    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            if (in == null) throw new IOException("SERVER_LOGIN_CLASS_MISSING");
            byte[] bytes = in.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("Login class exceeds limit");
            return bytes;
        }
    }

    // Reuse the tested constant-pool writer; production always requires ORIGINAL.
    // Only the owner UTF8 entry changes. Method bodies, hashes and login checks remain.
    static byte[] redirect(byte[] bytes, String expected, boolean reverse) throws Exception {
        try {
            return ServerSqlitePatch.redirect(bytes, expected, reverse ? NEW : OLD, reverse ? OLD : NEW);
        } catch (IOException failure) {
            throw new IOException("SERVER_LOGIN_PATCH_UNSUPPORTED classSha256=" + ServerSqlitePatch.sha(bytes), failure);
        }
    }

    public static void prepare(Path source, Path target) throws Exception {
        if (Files.exists(target)) throw new IOException("Login overlay already exists");
        byte[] original;
        try (var jar = new JarFile(source.toFile())) {
            JarEntry entry = jar.getJarEntry(ENTRY);
            if (entry == null) throw new IOException("SERVER_LOGIN_CLASS_MISSING");
            original = read(jar.getInputStream(entry));
        }
        byte[] patched = redirect(original, ORIGINAL, false);
        // Exercise Java 17's encoding using a public test vector, never a user's password.
        byte[] digest = MessageDigest.getInstance("SHA").digest("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!LegacyBase64Encoder.encodeValue(digest).equals("qZk+NkcGgWq6PiVxeFDCbJzQ2J0="))
            throw new IOException("SERVER_LOGIN_BASE64_SELF_TEST_FAILED");
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW))) {
                JarEntry entry = new JarEntry(ENTRY); entry.setTime(0);
                out.putNextEntry(entry); out.write(patched); out.closeEntry();
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(pending); }
        System.out.println("[server-login] LOGIN_PATCH_READY originalSha256=" + ServerSqlitePatch.sha(original) +
            " patchedSha256=" + ServerSqlitePatch.sha(patched) + "; encoderOwners=1; SHA/UTF-8/login checks unchanged");
        System.out.println("[server-login] BASE64_SELF_TEST_OK; public SHA-1 vector; no world opened");
    }

    public static void verifySelected() throws Exception {
        String configured = System.getProperty("wurm.server.loginOverlay");
        if (configured == null) return; // Standalone fixtures; Android always supplies this property.
        byte[] patched;
        try (var jar = new JarFile(configured)) {
            if (jar.size() != 1 || jar.getJarEntry(ENTRY) == null) throw new IOException("Invalid login overlay entries");
            patched = read(jar.getInputStream(jar.getJarEntry(ENTRY)));
        }
        byte[] restored = redirect(patched, ServerSqlitePatch.sha(patched), true);
        if (!ServerSqlitePatch.sha(restored).equals(ORIGINAL)) throw new IOException("SERVER_LOGIN_PATCH_INTEGRITY_FAILED");
        URL selected = ServerLoginPatch.class.getClassLoader().getResource(ENTRY);
        if (selected == null || !ServerSqlitePatch.sha(read(selected.openStream())).equals(ServerSqlitePatch.sha(patched)))
            throw new IOException("SERVER_LOGIN_PATCH_NOT_SELECTED: classpath order mismatch");
        System.out.println("[server-login] LOGIN_PATCH_ACTIVE source=" + selected + " sha256=" + ServerSqlitePatch.sha(patched));
    }
}
