package probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

/** Standard JVM bytecode, never compiled to Android dex. No Wurm dependency. */
public final class RuntimeProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected a disposable test directory");
        System.out.println("[probe] java.version=" + System.getProperty("java.version"));
        System.out.println("[probe] java.vm.name=" + System.getProperty("java.vm.name"));
        System.out.println("[probe] java.home=" + System.getProperty("java.home"));
        System.out.println("[probe] os.arch=" + System.getProperty("os.arch"));
        System.out.println("[probe] user.home=" + System.getProperty("user.home"));
        if (Runtime.version().feature() != 17) throw new IllegalStateException("Expected Java 17");
        // Exercise threads and a short JIT-eligible loop in the default mixed mode.
        AtomicLong result = new AtomicLong();
        Thread worker = new Thread(() -> {
            long sum = 0;
            for (int i = 0; i < 1_000_000; i++) sum += i;
            result.set(sum);
        }, "probe-worker");
        worker.start();
        worker.join();
        if (result.get() != 499_999_500_000L) throw new AssertionError("Thread/computation test failed");
        System.out.println("[probe] JAVA_OK");

        Path scratch = Path.of(args[0]).toRealPath();
        // Caller supplies a new per-run directory. Never reuse or open a Wurm DB.
        Path database = scratch.resolve("jvm-sqlite-probe.db");
        if (Files.exists(database)) throw new IllegalStateException("Test database must be new");
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + database;
        try (Connection connection = DriverManager.getConnection(url);
             Statement sql = connection.createStatement()) {
            try (ResultSet version = sql.executeQuery("select sqlite_version()")) {
                if (!version.next()) throw new AssertionError("No SQLite version");
                System.out.println("[probe] sqlite.version=" + version.getString(1));
            }
            sql.executeUpdate("create table probe_items (id integer primary key, value text not null)");
            sql.executeUpdate("insert into probe_items (id,value) values (1,'before')");
            connection.setAutoCommit(false);
            sql.executeUpdate("update probe_items set value='after' where id=1");
            connection.commit();
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("select value from probe_items where id=1")) {
            if (!rows.next() || !"after".equals(rows.getString(1))) throw new AssertionError("SQLite persistence failed");
        }
        System.out.println("[probe] SQLITE_OK: create/insert/update/commit/close/reopen");
        System.out.println("[probe] This does not test Wurm's item SQL patch or world saving.");
        System.out.println("[probe] PROBE_OK");
    }
}
