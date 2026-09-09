package client;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.*;

/** Verified engine/buffer adapters, generated privately for each client attempt. */
public final class ClientGraphicsPatch {
    static final String ENGINE = "com/wurmonline/client/WurmClientBase.class";
    static final String ORIGINAL = "db689422e7195271d7e395adfe87ab63cb26805ace86add1f936eb71c2fab1e6";
    static final String FROM = "org/lwjgl/opengl/Pbuffer";
    static final String TO = "wurm/graphics/OffscreenSupport";
    private static final int LIMIT = 4 * 1024 * 1024;
    private static void log(String s) { System.out.println("[client] " + s); }
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            if (in == null) throw new IOException("CLIENT_ENGINE_CLASS_MISSING");
            byte[] bytes = in.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("Engine class exceeds limit");
            return bytes;
        }
    }
    // Package-private for authored fixture tests. Production always supplies the
    // inspected engine SHA above; it never accepts a user-configured hash or rule.
    static byte[] redirect(byte[] bytes, String expected, String from, String to) throws Exception {
        if (bytes.length > LIMIT || !sha(bytes).equals(expected))
            throw new IOException("CLIENT_GRAPHICS_PATCH_UNSUPPORTED engineSha256=" + sha(bytes));
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
        if (changes != 1) throw new IOException("Expected one verified class reference, got " + changes);
        // Preserve every method body, stack map, branch, and remaining attribute.
        input.transferTo(output);
        return buffer.toByteArray();
    }
    public static void prepare() throws Exception {
        String configured = System.getProperty("wurm.client.offscreenOverlay");
        if (configured == null) throw new IllegalArgumentException("CLIENT_GRAPHICS_OVERLAY_PATH_REQUIRED");
        Path target = Path.of(configured);
        if (Files.exists(target)) throw new IOException("Overlay already exists");
        if (!Files.isDirectory(target.toAbsolutePath().getParent())) throw new IOException("Overlay directory missing");
        URL source = ClientGraphicsPatch.class.getClassLoader().getResource(ENGINE);
        if (source == null) throw new IOException("CLIENT_ENGINE_CLASS_MISSING");
        byte[] original = read(source.openStream());
        byte[] changed = redirect(original, ORIGINAL, FROM, TO);
        log("OFFSCREEN_PATCH_VERIFIED source=" + source + " originalSha256=" + sha(original) + " overlayClassSha256=" + sha(changed));
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ENGINE, changed);
        for (String name : new java.util.TreeSet<>(ClientBuffers.ORIGINALS.keySet())) {
            URL bufferSource = ClientGraphicsPatch.class.getClassLoader().getResource(name);
            if (bufferSource == null) throw new IOException("CLIENT_BUFFER_CLASS_MISSING " + name);
            classes.put(name, ClientBuffers.prepare(name, read(bufferSource.openStream())));
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".pending");
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW))) {
                for (var item : classes.entrySet()) {
                    var entry = new JarEntry(item.getKey()); entry.setTime(0);
                    out.putNextEntry(entry); out.write(item.getValue()); out.closeEntry();
                }
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
        log("OFFSCREEN_PATCH_READY calls=1 scope=verified-engine-only; imported JAR unchanged; legacy Pbuffer API unchanged");
        log("BUFFER_PATCH_READY classes=2; Cleaner owner/return ABI only; imported JAR unchanged");
    }
    public static void verifySelected() throws Exception {
        String path = System.getProperty("wurm.client.offscreenOverlay");
        if (path == null) return; // Standalone original-client/fixture diagnostics.
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (var jar = new JarFile(path)) {
            var names = new java.util.TreeSet<>(ClientBuffers.ORIGINALS.keySet()); names.add(ENGINE);
            if (jar.size() != names.size()) throw new IOException("Invalid client overlay size");
            for (String name : names) {
                if (jar.getJarEntry(name) == null) throw new IOException("Missing client overlay class " + name);
                classes.put(name, read(jar.getInputStream(jar.getJarEntry(name))));
            }
        }
        byte[] expected = classes.get(ENGINE);
        // Reverse the single relocation and prove the complete engine still
        // matches the inspected original, including every executable method.
        byte[] restored = redirect(expected, sha(expected), TO, FROM);
        if (!sha(restored).equals(ORIGINAL)) throw new IOException("CLIENT_GRAPHICS_PATCH_INTEGRITY_FAILED");
        for (var item : classes.entrySet()) {
            String name = item.getKey(); byte[] bytes = item.getValue();
            if (!name.equals(ENGINE)) ClientBuffers.verify(name, bytes);
            URL selected = ClientGraphicsPatch.class.getClassLoader().getResource(name);
            if (selected == null || !sha(read(selected.openStream())).equals(sha(bytes)))
                throw new IOException("CLIENT_GRAPHICS_PATCH_NOT_SELECTED: classpath order mismatch " + name);
            log((name.equals(ENGINE) ? "OFFSCREEN_PATCH_ACTIVE" : "BUFFER_PATCH_ACTIVE") + " source=" + selected + " overlayClassSha256=" + sha(bytes));
        }
    }
}
