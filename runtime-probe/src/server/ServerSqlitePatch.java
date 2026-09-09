package server;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.*;

/** One inspected SQL constant; generated in the disposable session, never in the imported JAR. */
public final class ServerSqlitePatch {
    static final String ENTRY = "com/wurmonline/server/creatures/CreaturePos.class";
    static final String ORIGINAL = "b188c29d94e83b8d5a9487267469695b9a52208c57a2f0eea04de789afde9a21";
    static final String INSERT = "INSERT INTO POSITION (POSX, POSY, POSZ, ROTATION, ZONEID, LAYER, ONBRIDGE, WURMID) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ";
    static final String MYSQL = INSERT + "ON DUPLICATE KEY UPDATE POSX=VALUES(POSX), POSY=VALUES(POSY), POSZ=VALUES(POSZ), ROTATION=VALUES(ROTATION), ZONEID=VALUES(ZONEID), LAYER=VALUES(LAYER), ONBRIDGE=VALUES(ONBRIDGE)";
    // Like MySQL's statement, handle a uniqueness conflict without deleting/reinserting the row.
    static final String SQLITE = INSERT + "ON CONFLICT DO UPDATE SET POSX=excluded.POSX, POSY=excluded.POSY, POSZ=excluded.POSZ, ROTATION=excluded.ROTATION, ZONEID=excluded.ZONEID, LAYER=excluded.LAYER, ONBRIDGE=excluded.ONBRIDGE";
    private static final int LIMIT = 4 * 1024 * 1024;
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            if (in == null) throw new IOException("SERVER_POSITION_CLASS_MISSING");
            byte[] bytes = in.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("Position class exceeds limit");
            return bytes;
        }
    }
    // Package-private for authored fixture tests. Production always supplies the
    // inspected position-class SHA above; it never accepts a user-configured hash or rule.
    static byte[] redirect(byte[] bytes, String expected, String from, String to) throws Exception {
        if (bytes.length > LIMIT || !sha(bytes).equals(expected))
            throw new IOException("SERVER_SQLITE_PATCH_UNSUPPORTED classSha256=" + sha(bytes));
        var input = new DataInputStream(new ByteArrayInputStream(bytes));
        var buffer = new ByteArrayOutputStream(bytes.length + 100);
        var output = new DataOutputStream(buffer);
        int magic = input.readInt();
        if (magic != 0xcafebabe) throw new IOException("Invalid class magic");
        output.writeInt(magic); output.writeShort(input.readUnsignedShort());
        int version = input.readUnsignedShort();
        if (version > 61) throw new IOException("Unsupported class version");
        output.writeShort(version);
        int count = input.readUnsignedShort(), changes = 0;
        output.writeShort(count);
        for (int i = 1; i < count; i++) {
            int tag = input.readUnsignedByte(); output.writeByte(tag);
            if (tag == 1) {
                int length = input.readUnsignedShort();
                byte[] utf = input.readNBytes(length);
                if (utf.length != length) throw new EOFException();
                if (java.util.Arrays.equals(utf, from.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    utf = to.getBytes(java.nio.charset.StandardCharsets.UTF_8); changes++;
                }
                output.writeShort(utf.length); output.write(utf);
            } else {
                int length = switch(tag) {
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> 4;
                    case 5, 6 -> 8;
                    case 7, 8, 16, 19, 20 -> 2;
                    case 15 -> 3;
                    default -> throw new IOException("Invalid constant pool tag " + tag);
                };
                byte[] data = input.readNBytes(length);
                if (data.length != length) throw new EOFException();
                output.write(data);
                if (tag == 5 || tag == 6) i++;
            }
        }
        if (changes != 1) throw new IOException("Expected one verified SQL constant, got " + changes);
        // Preserve every method body, stack map, branch, and remaining attribute.
        input.transferTo(output);
        return buffer.toByteArray();
    }
    public static void prepare(Path source, Path target) throws Exception {
        if (Files.exists(target)) throw new IOException("Server overlay already exists");
        byte[] original;
        try (var jar = new JarFile(source.toFile())) {
            JarEntry entry = jar.getJarEntry(ENTRY);
            if (entry == null) throw new IOException("SERVER_POSITION_CLASS_MISSING");
            original = read(jar.getInputStream(entry));
        }
        byte[] patched = redirect(original, ORIGINAL, MYSQL, SQLITE);
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW))) {
                JarEntry entry = new JarEntry(ENTRY); entry.setTime(0);
                out.putNextEntry(entry); out.write(patched); out.closeEntry();
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(pending); }
        System.out.println("[server-sqlite] POSITION_PATCH_READY originalSha256=" + sha(original) +
            " patchedSha256=" + sha(patched) + "; constants=1; bindings=8; item patches and imported JAR unchanged");
    }
    public static void verifySelected() throws Exception {
        String configured = System.getProperty("wurm.server.sqliteOverlay");
        if (configured == null) return; // Standalone control fixtures; Android always supplies this property.
        byte[] patched;
        try (var jar = new JarFile(configured)) {
            if (jar.size() != 1 || jar.getJarEntry(ENTRY) == null) throw new IOException("Invalid server overlay entries");
            patched = read(jar.getInputStream(jar.getJarEntry(ENTRY)));
        }
        byte[] restored = redirect(patched, sha(patched), SQLITE, MYSQL);
        if (!sha(restored).equals(ORIGINAL)) throw new IOException("SERVER_SQLITE_PATCH_INTEGRITY_FAILED");
        URL selected = ServerSqlitePatch.class.getClassLoader().getResource(ENTRY);
        if (selected == null || !sha(read(selected.openStream())).equals(sha(patched)))
            throw new IOException("SERVER_SQLITE_PATCH_NOT_SELECTED: classpath order mismatch");
        System.out.println("[server-sqlite] POSITION_PATCH_ACTIVE source=" + selected + " sha256=" + sha(patched));
    }
}
