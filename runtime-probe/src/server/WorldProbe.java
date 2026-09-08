package server;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Best-effort observation of this JVM only. Never opens a game file or database. */
public final class WorldProbe {
    private static void emit(String value) { System.out.println("[world] " + value); }

    public static void capture() {
        emit("SNAPSHOT_BEGIN " + Instant.now());
        try {
            inspect(Path.of(".").toRealPath(), Path.of("/proc/self"));
        } catch (Exception failure) {
            emit("SNAPSHOT_UNAVAILABLE " + failure.getClass().getSimpleName());
        } finally {
            emit("SNAPSHOT_END " + Instant.now());
        }
    }

    // Separate input paths allow tests with handwritten proc fixtures.
    static void inspect(Path root, Path proc) {
        Set<String> sockets = new HashSet<>();
        Set<String> paths = new TreeSet<>();
        boolean fdComplete = true;
        int skipped = 0;
        try (var fds = Files.newDirectoryStream(proc.resolve("fd"))) {
            int count = 0;
            for (Path fd : fds) {
                if (++count > 4096) { fdComplete = false; emit("FD_LIMIT 4096"); break; }
                try {
                    String target = Files.readSymbolicLink(fd).toString();
                    if (target.matches("socket:\\[[0-9]+]")) sockets.add(target.substring(8, target.length() - 1));
                    else recordPath(root, target, "OPEN_FILE", paths);
                } catch (Exception gone) { skipped++; }
            }
        } catch (Exception unavailable) {
            fdComplete = false;
            emit("FD_UNAVAILABLE " + unavailable.getClass().getSimpleName());
        }
        emit("FD_SCAN complete=" + fdComplete + " skipped=" + skipped);
        try (BufferedReader maps = Files.newBufferedReader(proc.resolve("maps"))) {
            String line;
            int count = 0;
            while ((line = maps.readLine()) != null) {
                if (++count > 16384) { emit("MAPS_LIMIT 16384"); break; }
                String[] fields = line.trim().split("\\s+", 6);
                if (fields.length == 6) recordPath(root, fields[5], "MAPPED_FILE", paths);
            }
        } catch (Exception unavailable) { emit("MAPS_UNAVAILABLE " + unavailable.getClass().getSimpleName()); }
        paths.stream().limit(256).forEach(WorldProbe::emit);
        if (paths.size() > 256) emit("FILE_LIMIT 256");
        if (paths.isEmpty()) emit("FILES_NOT_OBSERVED no matching open or mapped game files in this snapshot");
        // These files describe the network namespace, not just this process.
        // Match the inode to this child's own socket FDs before reporting a listener.
        for (String family : new String[]{"tcp", "tcp6"}) {
            int found = 0;
            try (BufferedReader tcp = Files.newBufferedReader(proc.resolve("net").resolve(family))) {
                String line;
                int count = 0;
                while ((line = tcp.readLine()) != null) {
                    if (++count > 16384) { emit("TCP_LIMIT " + family); break; }
                    String endpoint = listener(line, sockets);
                    if (endpoint != null) { found++; emit("TCP_LISTEN " + family + " " + endpoint); }
                    if (found == 128) { emit("LISTENER_LIMIT 128"); break; }
                }
                emit("TCP_SCAN " + family + " matched=" + found + " fdComplete=" + fdComplete);
            } catch (Exception unavailable) {
                emit("TCP_UNAVAILABLE " + family + " " + unavailable.getClass().getSimpleName());
            }
        }
    }

    private static void recordPath(Path root, String target, String kind, Set<String> paths) {
        if (paths.size() >= 257 || target.length() > 2048 || target.chars().anyMatch(Character::isISOControl)) return;
        boolean deleted = target.endsWith(" (deleted)");
        String name = deleted ? target.substring(0, target.length() - 10) : target;
        Path path = Path.of(name).normalize();
        if (!path.isAbsolute() || !path.startsWith(root)) return;
        if (!name.matches(".*\\.(map|db|sqlite|sqlite3)(-wal|-shm|-journal)?")) return;
        paths.add(kind + " " + root.relativize(path) + (deleted ? " [deleted]" : ""));
    }

    static String listener(String line, Set<String> sockets) throws Exception {
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 10 || !fields[3].equals("0A") || !sockets.contains(fields[9])) return null;
        String[] endpoint = fields[1].split(":");
        if (endpoint.length != 2 || !endpoint[0].matches("[A-Fa-f0-9]{8}|[A-Fa-f0-9]{32}") ||
                !endpoint[1].matches("[A-Fa-f0-9]{4}")) return null;
        byte[] address = new byte[endpoint[0].length() / 2];
        // proc renders each 32-bit address word in native byte order.
        for (int i = 0; i < address.length; i++) {
            int source = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? (i / 4) * 4 + 3 - i % 4 : i;
            address[i] = (byte) Integer.parseInt(endpoint[0].substring(source * 2, source * 2 + 2), 16);
        }
        return "[" + InetAddress.getByAddress(address).getHostAddress() + "]:" + Integer.parseInt(endpoint[1], 16);
    }
}
