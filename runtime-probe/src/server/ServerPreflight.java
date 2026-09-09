package server;

import java.nio.file.Path;

/** Exercise the existing JVM probe and prepare compatibility before opening any world. */
public final class ServerPreflight {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected disposable session and imported runtime");
        probe.RuntimeProbe.main(new String[]{args[0]});
        ServerSqlitePatch.prepare(Path.of(args[1], "server.jar"), Path.of(args[0], "server-sqlite.jar"));
        System.out.println("[server-sqlite] SERVER_PREFLIGHT_OK");
    }
}
