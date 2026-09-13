package server;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Resolve the world's actual DB_HOST before Wurm starts; never create/open a SQLite database. */
public final class ServerDatabasePreflight {
    public static void verify(Path runtime, String world) throws Exception {
        Path root=runtime.toRealPath();
        if(world.isBlank() || world.equals(".") || world.equals("..") || world.contains("/") || world.contains("\\"))
            throw new IOException("Invalid world name");
        Path ini=inside(root,root.resolve(world).resolve("wurm.ini"));
        if(!Files.isRegularFile(ini) || Files.size(ini)>65536) throw new IOException("World configuration missing or oversized");
        Properties properties=new Properties();
        try(var in=Files.newInputStream(ini)) { properties.load(in); }
        String host=properties.getProperty("DB_HOST");
        if(host==null || host.isBlank()) throw new IOException("WORLD_DATABASE_PATH_FAILED: DB_HOST is missing");
        Path configured=Path.of(host);
        Path directory=inside(root,(configured.isAbsolute() ? configured : root.resolve(configured)).resolve("sqlite"));
        if(!Files.isDirectory(directory) || !Files.isWritable(directory)) throw new IOException("WORLD_DATABASE_PATH_FAILED: sqlite directory unavailable");
        for(String suffix:List.of("creatures","deities","economy","items","login","logs","players","templates","zones")) {
            String name="wurm"+suffix+".db";
            Path file=inside(root,directory.resolve(name));
            if(!Files.isRegularFile(file) || Files.size(file)<100 || !Files.isWritable(file))
                throw new IOException("WORLD_DATABASE_PATH_FAILED: missing/incomplete/unwritable "+name);
            try(var in=Files.newInputStream(file)) {
                if(!Arrays.equals(in.readNBytes(16),"SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                    throw new IOException("WORLD_DATABASE_PATH_FAILED: invalid SQLite header "+name);
            }
        }
        System.out.println("[server-database] WORLD_DATABASE_PATHS_OK directory="+root.relativize(directory)+"; databases=9; headers checked; SQLite databases not opened");
    }
    private static Path inside(Path root,Path path) throws IOException {
        Path normalized=path.toAbsolutePath().normalize();
        if(!normalized.startsWith(root)) throw new IOException("WORLD_DATABASE_PATH_FAILED: path outside working runtime");
        if(!Files.exists(normalized)) throw new IOException("WORLD_DATABASE_PATH_FAILED: missing "+root.relativize(normalized)+"; prepare the original ZIP with the current app");
        if(!normalized.toRealPath().equals(normalized)) throw new IOException("WORLD_DATABASE_PATH_FAILED: linked database path");
        return normalized;
    }
}
