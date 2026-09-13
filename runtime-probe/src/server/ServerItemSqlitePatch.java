package server;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Use the inspected game's own personal-server UPDATE statements, without rewriting its JAR. */
public final class ServerItemSqlitePatch {
    record Rule(String name, String table, String stock, String prepared, String patched) {
        String entry() { return "com/wurmonline/server/items/" + name + ".class"; }
        String mysql(boolean damage) {
            return "INSERT INTO " + table + (damage ? " (DAMAGE, LASTMAINTAINED, WURMID) VALUES (?, ?, ?) " :
                " (LASTMAINTAINED, WURMID) VALUES (?, ?) ") + "ON DUPLICATE KEY UPDATE " +
                (damage && table.equals("ITEMS") ? "DAMAGE=VALUES(DAMAGE), " : "") + "LASTMAINTAINED=VALUES(LASTMAINTAINED)";
        }
        String sqlite(boolean damage) {
            return "UPDATE " + table + " SET " + (damage ? (table.equals("FROZENITEMS") ? "DAMAGE=?,LASTMAINTAINED=?" : "DAMAGE=?, LASTMAINTAINED=?") : "LASTMAINTAINED=?") + " WHERE WURMID=?";
        }
    }
    static final List<Rule> RULES = List.of(
        new Rule("ItemDbStrings", "ITEMS", "5acf230fdb9f27c91ea46315888ebc2f7de40876e01a253997d2a98509b7b9d4",
            "3b03740b39bfbd01c03a38f9410ff2751bfccff47a7f391ceda4886212167c9c", "b06881c3fc4237db5789480b7079542010eb0b0d1e8d3e905b42d1dde036c3db"),
        new Rule("BodyDbStrings", "BODYPARTS", "e900fd8bf8d1075a6a0b420d5056f45fad26a3b91c15806d8b97515a5356d9dd",
            "6800930b8281adb829278fd8302792ea3de73e9bc0233ceaca210d1e589dcb25", "80924ae5fb4b3ae3b9619f30e447948198635c8d31488975cd0c06f07e0d8f92"),
        new Rule("CoinDbStrings", "COINS", "a94b4046dbf4e17907d5e9f7a7e4c5dd6e8f026e2a993c18c64d5639ce5dcbb0",
            "699c0f063537fbbc85fa2554a85bf78723d3c6e186e24e974e0e1dfd3a26766a", "081bd675e89f9412775ab6308960b09d888e37f483baf6b268992c5cc9eb12fb"),
        new Rule("FrozenItemDbStrings", "FROZENITEMS", "242c9f8c54e17b484e0bf926f00320b9c7db0140d7e32a19aa16aa377606d4cd",
            "cfd8401f8fd1d967c5c96ed973db172416295c5ab761dfc647d8ae5721f24375", "5cfa9315cd2e212497a5ec0480510aeb6fabde98fec96b0ce001e79f9754cc2f"));

    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            byte[] bytes = in.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("Item class exceeds limit");
            return bytes;
        }
    }
    private static byte[] entry(JarFile jar, Rule rule) throws IOException {
        JarEntry entry = jar.getJarEntry(rule.entry());
        if (entry == null) throw new IOException("SERVER_ITEM_CLASS_MISSING " + rule.name());
        return read(jar.getInputStream(entry));
    }
    public static void prepare(Path source, Path target) throws Exception {
        if (Files.exists(target)) throw new IOException("Item overlay already exists");
        Map<Rule, byte[]> inputs = new LinkedHashMap<>();
        try (var jar = new JarFile(source.toFile())) {
            for (Rule rule : RULES) inputs.put(rule, entry(jar, rule));
        }
        boolean stock = true, prepared = true;
        for (var input : inputs.entrySet()) {
            String hash = ServerSqlitePatch.sha(input.getValue());
            stock &= hash.equals(input.getKey().stock());
            prepared &= hash.equals(input.getKey().prepared());
        }
        if (!stock && !prepared) throw new IOException("SERVER_ITEM_PATCH_UNSUPPORTED: unknown or mixed item classes");
        Map<Rule, byte[]> overlay = new LinkedHashMap<>();
        if (stock) for (var input : inputs.entrySet()) {
            Rule rule = input.getKey();
            byte[] patched = ServerSqlitePatch.redirect(input.getValue(), rule.stock(), rule.mysql(false), rule.sqlite(false));
            patched = ServerSqlitePatch.redirect(patched, ServerSqlitePatch.sha(patched), rule.mysql(true), rule.sqlite(true));
            if (!ServerSqlitePatch.sha(patched).equals(rule.patched())) throw new IOException("SERVER_ITEM_PATCH_OUTPUT_MISMATCH");
            overlay.put(rule, patched);
        }
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW))) {
                for (var item : overlay.entrySet()) {
                    JarEntry entry = new JarEntry(item.getKey().entry()); entry.setTime(0);
                    out.putNextEntry(entry); out.write(item.getValue()); out.closeEntry();
                }
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(pending); }
        System.out.println("[server-items] ITEM_PATCH_READY mode=" + (stock ? "stock-personal-updates" : "prepared-preserved") +
            "; classes=" + overlay.size() + "; imported JAR unchanged");
    }

    public static void verifySelected() throws Exception {
        String configured = System.getProperty("wurm.server.itemsOverlay");
        if (configured == null) return; // Standalone control fixtures; Android always sets it.
        try (var jar = new JarFile(configured)) {
            boolean stock = jar.size() == RULES.size();
            if (!stock && jar.size() != 0) throw new IOException("Invalid item overlay entries");
            for (Rule rule : RULES) {
                String expected = stock ? rule.patched() : rule.prepared();
                if (stock && !ServerSqlitePatch.sha(entry(jar,rule)).equals(expected))
                    throw new IOException("SERVER_ITEM_PATCH_INTEGRITY_FAILED " + rule.name());
                // Resource inspection does not initialize/freeze game classes before mod hooks.
                URL selected = ServerItemSqlitePatch.class.getClassLoader().getResource(rule.entry());
                if (selected == null || !ServerSqlitePatch.sha(read(selected.openStream())).equals(expected))
                    throw new IOException("SERVER_ITEM_PATCH_NOT_SELECTED " + rule.name());
                System.out.println("[server-items] ITEM_PATCH_ACTIVE class=" + rule.name() + " sha256=" + expected);
            }
        }
    }
}
