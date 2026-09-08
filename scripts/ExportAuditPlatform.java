import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

/** Exports only JDK ancestor metadata for the developer audit, never for the APK classpath. */
public final class ExportAuditPlatform {
    public static void main(String[] args) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            for (String name : new String[] {"java/lang/Object", "java/lang/Throwable", "java/lang/Exception", "java/lang/RuntimeException"}) {
                try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                    if (in == null) throw new IOException("Missing platform class: " + name);
                    JarEntry entry = new JarEntry(name + ".class"); entry.setTime(0);
                    out.putNextEntry(entry); in.transferTo(out); out.closeEntry();
                }
            }
        }
    }
}
