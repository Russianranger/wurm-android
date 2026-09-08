package persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Invoked only by the locked, stopped-runtime child. Never opens a source DB through SQLite. */
public final class StorageAudit {
    private static final int LIMIT = 100_000;
    private static final long RESERVE = 128L * 1024 * 1024;
    private static final byte[] SQLITE = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private record Stamp(long bytes, String sha) {}
    private record Snapshot(String scope, String time, TreeMap<String, Stamp> files) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("runtime, audit directory, world, baseline/check required");
        String mode = args[3];
        if (!Set.of("baseline", "check").contains(mode)) throw new IllegalArgumentException("Unknown audit mode");
        Path root = Path.of(args[0]).toRealPath();
        Path store = Path.of(args[1]).toAbsolutePath().normalize();
        if (store.startsWith(root) || root.startsWith(store)) throw new IOException("Audit storage must be outside runtime");
        Files.createDirectories(store);
        if (Files.isSymbolicLink(store)) throw new IOException("Audit storage cannot be a symbolic link");
        // Disposable copies left by a killed audit are outside the game tree.
        try (DirectoryStream<Path> old = Files.newDirectoryStream(store, "db-check-*")) {
            for (Path path : old) if (path.getFileName().toString().matches("db-check-[0-9]+")) removeScratch(path);
        }
        String world = args[2];
        if (world.isBlank() || world.equals(".") || world.equals("..") || world.contains("/") || world.contains("\\") ||
                world.chars().anyMatch(Character::isISOControl) || !Files.isDirectory(root.resolve(world), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Select an existing world");
        String scope = root + "\n" + world;
        StringBuilder report = new StringBuilder("Wurm Server 0.6.0 — stopped storage audit\n")
                .append("Time: ").append(Instant.now()).append("\nMode: ").append(mode)
                .append("\nWorking copy: ").append(root.getFileName()).append("\nWorld: ").append(world)
                .append("\nScope: every regular file in the complete working runtime, including other worlds and localhost/sqlite.\n")
                .append("SQLite opens disposable copies only. No table contents are exported.\n")
                .append("Hash equality and quick_check do not prove Wurm saved a particular gameplay change.\n\n");
        try {
            Snapshot current = scan(root, scope);
            report.append("FILES=").append(current.files.size()).append(" BYTES=")
                    .append(current.files.values().stream().mapToLong(Stamp::bytes).sum()).append('\n');
            Path baseline = store.resolve("baseline.bin");
            Path previous = store.resolve("last-check.bin");
            if (mode.equals("check")) {
                compare("BASELINE", load(baseline), current, report);
                if (Files.exists(previous)) compare("LAST_CHECK", load(previous), current, report);
                else report.append("LAST_CHECK_NONE: this is the first check after the baseline.\n");
            }
            int databases = 0;
            int failures = 0;
            for (String name : current.files.keySet()) {
                Path file = root.resolve(name);
                if (!isDatabase(file)) continue;
                if (++databases > 512) throw new IOException("Too many database candidates");
                System.out.println("[audit] Checking database " + clean(name));
                try { checkDatabase(root, name, current.files, store, report); }
                catch (Exception failure) {
                    failures++;
                    report.append("DB_CHECK_FAILED ").append(clean(name)).append(" — ").append(clean(failure.toString())).append('\n');
                }
            }
            report.append("DATABASES=").append(databases).append(" FAILED=").append(failures).append('\n');
            if (databases == 0 || failures != 0) throw new IOException("Database checks incomplete; review DB_CHECK_FAILED entries");
            // Catch unexpected writers and verify that SQLite-copy checks did not change source bytes.
            Snapshot after = scan(root, scope);
            if (!current.files.equals(after.files)) throw new IOException("Runtime changed during audit; no snapshot accepted");
            report.append("SOURCE_UNCHANGED: all source file bytes matched before and after database checks.\n");
            if (mode.equals("baseline")) {
                save(baseline, current);
                Files.deleteIfExists(previous);
                report.append("BASELINE_CAPTURED\n");
            } else {
                save(previous, current);
                report.append("STORAGE_CHECK_COMPLETE\n");
            }
            writeReport(store, report.toString());
            System.out.println(mode.equals("baseline") ? "[audit] BASELINE_CAPTURED" : "[audit] STORAGE_CHECK_COMPLETE");
        } catch (Exception failure) {
            report.append("AUDIT_FAILED: ").append(clean(failure.toString())).append('\n');
            writeReport(store, report.toString());
            System.err.println("[audit] AUDIT_FAILED: " + clean(failure.toString()));
            throw failure;
        }
    }

    private static Snapshot scan(Path root, String scope) throws Exception {
        TreeMap<String, Stamp> files = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) throw new IOException("Unsupported runtime entry: " + file.getFileName());
                if (files.size() >= LIMIT) throw new IOException("Too many runtime files");
                String relative = root.relativize(file).toString();
                if (relative.chars().anyMatch(Character::isISOControl)) throw new IOException("Control character in runtime path");
                files.put(relative, stamp(file));
                if (files.size() % 1000 == 0) System.out.println("[audit] Hashed " + files.size() + " files");
                return FileVisitResult.CONTINUE;
            }
        });
        return new Snapshot(scope, Instant.now().toString(), files);
    }

    private static Stamp stamp(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = input.read(buffer)) >= 0) { digest.update(buffer, 0, n); bytes += n; }
            }
            return new Stamp(bytes, HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static boolean isDatabase(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3")) return true;
        try (InputStream input = Files.newInputStream(file)) { return Arrays.equals(input.readNBytes(16), SQLITE); }
    }

    private static void checkDatabase(Path root, String name, Map<String, Stamp> expected, Path store, StringBuilder report) throws Exception {
        Path scratch = Files.createTempDirectory(store, "db-check-");
        try {
            Path source = root.resolve(name);
            Path copy = scratch.resolve(source.getFileName());
            for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
                Stamp identity = expected.get(name + suffix);
                if (identity == null) continue;
                if (Files.getFileStore(scratch).getUsableSpace() <= identity.bytes + RESERVE) throw new IOException("Not enough free space for disposable database copy");
                Path target = scratch.resolve(source.getFileName() + suffix);
                Files.copy(root.resolve(name + suffix), target);
                if (!identity.equals(stamp(target))) throw new IOException("Database changed during copy");
            }
            Class.forName("org.sqlite.JDBC");
            // URI encodes spaces/#/? in the path. mode=ro cannot create a missing database.
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + copy.toUri().toASCIIString() + "?mode=ro");
                 Statement query = db.createStatement()) {
                query.execute("PRAGMA query_only=ON");
                try (ResultSet check = query.executeQuery("PRAGMA quick_check(20)")) {
                    if (!check.next() || !"ok".equals(check.getString(1)) || check.next()) throw new IOException("SQLite quick_check did not return exactly ok");
                }
                List<String> tables = new ArrayList<>();
                try (ResultSet rows = query.executeQuery("SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                    while (rows.next()) {
                        if (tables.size() >= 2048) throw new IOException("Too many tables");
                        tables.add(rows.getString(1));
                    }
                }
                long count = 0;
                for (String table : tables) {
                    try (ResultSet rows = query.executeQuery("SELECT count(*) FROM \"" + table.replace("\"", "\"\"") + "\"")) {
                        if (!rows.next()) throw new IOException("Missing row count");
                        count = Math.addExact(count, rows.getLong(1));
                    }
                }
                report.append("DB_OK ").append(clean(name)).append(" tables=").append(tables.size())
                        .append(" rows=").append(count).append(" sha256=").append(expected.get(name).sha)
                        .append(" wal=").append(expected.containsKey(name + "-wal")).append('\n');
            }
        } finally { removeScratch(scratch); }
    }

    private static void compare(String label, Snapshot old, Snapshot current, StringBuilder report) throws IOException {
        if (!old.scope.equals(current.scope)) throw new IOException(label + " belongs to another working copy/world; capture a new baseline");
        int added = 0, changed = 0, removed = 0, shown = 0;
        StringBuilder details = new StringBuilder();
        TreeSet<String> names = new TreeSet<>(old.files.keySet()); names.addAll(current.files.keySet());
        for (String name : names) {
            Stamp before = old.files.get(name), after = current.files.get(name);
            if (Objects.equals(before, after)) continue;
            String kind;
            if (before == null) { added++; kind = "ADDED"; }
            else if (after == null) { removed++; kind = "REMOVED"; }
            else { changed++; kind = "CHANGED"; }
            if (++shown <= 100) details.append("  ").append(kind).append(' ').append(clean(name)).append('\n');
        }
        report.append(label).append(added + changed + removed == 0 ? "_MATCH" : "_DIFF")
                .append(" since=").append(old.time).append(" added=").append(added).append(" changed=")
                .append(changed).append(" removed=").append(removed).append('\n').append(details);
        if (shown > 100) report.append("  Additional changed paths omitted; counts include all files.\n");
    }

    private static Snapshot load(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (!input.readUTF().equals("WURM_STORAGE_1")) throw new IOException("Unsupported snapshot");
            String scope = input.readUTF(), time = input.readUTF();
            int count = input.readInt();
            if (count < 0 || count > LIMIT) throw new IOException("Invalid snapshot size");
            TreeMap<String, Stamp> files = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                String name = input.readUTF(); long bytes = input.readLong(); String hash = input.readUTF();
                if (bytes < 0 || !hash.matches("[a-f0-9]{64}") || files.put(name, new Stamp(bytes, hash)) != null) throw new IOException("Invalid snapshot entry");
            }
            if (input.read() != -1) throw new IOException("Trailing snapshot data");
            return new Snapshot(scope, time, files);
        }
    }

    private static void save(Path target, Snapshot snapshot) throws IOException {
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pending)))) {
                output.writeUTF("WURM_STORAGE_1"); output.writeUTF(snapshot.scope); output.writeUTF(snapshot.time);
                output.writeInt(snapshot.files.size());
                for (var entry : snapshot.files.entrySet()) {
                    output.writeUTF(entry.getKey()); output.writeLong(entry.getValue().bytes); output.writeUTF(entry.getValue().sha);
                }
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }

    private static void writeReport(Path store, String report) throws IOException {
        Path pending = store.resolve("report.pending");
        Files.writeString(pending, report, StandardCharsets.UTF_8);
        Files.move(pending, store.resolve("report.txt"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void removeScratch(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
    private static String clean(String value) {
        String safe = value.replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(safe.length(), 1000));
    }
}
