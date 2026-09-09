package client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/** Client-only Java 2D font setup; no X11, JavaFX, font downloads or server changes. */
public final class ClientFonts {
    private static final String[] STYLES = {"plain", "bold", "italic", "bolditalic"};
    private static final String[] SUFFIXES = {"Regular", "Bold", "Italic", "BoldItalic"};
    private static final String[] LOGICAL = {"sansserif", "dialog", "serif", "monospaced", "dialoginput"};
    private static void log(String text) { System.out.println("[client] " + text); }

    private static Path first(Path directory, String... names) {
        for (String name : names) {
            Path path = directory.resolve(name);
            if (Files.isRegularFile(path) && Files.isReadable(path)) return path.toAbsolutePath().normalize();
        }
        return null;
    }

    private static Path style(Path regular, int index) {
        if (index == 0) return regular;
        String name = regular.getFileName().toString();
        String styled = name.endsWith("-Regular.ttf") ? name.replace("-Regular.ttf", "-" + SUFFIXES[index] + ".ttf")
            : name.substring(0, name.length() - 4) + "-" + SUFFIXES[index] + ".ttf";
        Path found = first(regular.getParent(), styled);
        return found == null ? regular : found; // Java 2D synthesizes absent bold/italic styles.
    }

    /** Must run before any FontManager use. The config lives in the owned client session. */
    public static void prepareIfConfigured() throws Exception {
        String setting = System.getProperty("wurm.client.fontConfig");
        if (setting == null) { log("FONT_CONFIG_UNMANAGED host/runtime defaults; Android entry must supply a session config"); return; }
        if (!GraphicsEnvironment.isHeadless()) throw new IllegalStateException("FONT_REQUIRES_HEADLESS_JAVA2D");
        Path directory = Path.of(System.getProperty("wurm.client.fontDir", "/system/fonts")).toAbsolutePath().normalize();
        Path config = Path.of(setting).toAbsolutePath().normalize();
        log("FONT_CONFIG_BEGIN directory=" + directory + " config=" + config);
        Path sans = first(directory, "Roboto-Regular.ttf", "NotoSans-Regular.ttf", "DroidSans.ttf");
        if (sans == null) throw new IOException("ANDROID_FONTS_MISSING: no readable Roboto/NotoSans/DroidSans in " + directory);
        Path serif = first(directory, "NotoSerif-Regular.ttf", "DroidSerif-Regular.ttf");
        Path mono = first(directory, "DroidSansMono.ttf", "NotoSansMono-Regular.ttf", "RobotoMono-Regular.ttf");
        if (serif == null) { serif = sans; log("FONT_FALLBACK serif=sans; preferred serif font unavailable"); }
        if (mono == null) { mono = sans; log("FONT_FALLBACK monospaced=sans; fixed pitch unavailable"); }
        Path[] families = {sans, sans, serif, mono, mono};
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("sequence.allfonts", "latin-1");
        properties.setProperty("appendedfontpath", directory.toString());
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        for (int family = 0; family < LOGICAL.length; family++) {
            for (int index = 0; index < STYLES.length; index++) {
                Path path = style(families[family], index);
                String alias = "WurmAndroid " + LOGICAL[family] + " " + STYLES[index];
                properties.setProperty(LOGICAL[family] + "." + STYLES[index] + ".latin-1", alias);
                properties.setProperty("filename." + alias.replace(' ', '_'), path.toString());
                files.add(path);
                log("FONT_MAP " + LOGICAL[family] + "." + STYLES[index] + "=" + path.getFileName());
            }
        }
        for (Path path : files) {
            long size = Files.size(path);
            if (size < 12 || size > 32 * 1024 * 1024) throw new IOException("FONT_FILE_SIZE_INVALID " + path);
            byte[] data = Files.readAllBytes(path);
            log("FONT_FILE file=" + path + " bytes=" + size + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        }
        // Create only a new session file: never replace an imported/user font configuration.
        try (OutputStream out = Files.newOutputStream(config, StandardOpenOption.CREATE_NEW)) {
            properties.store(out, "Wurm Android client logical fonts; generated from readable system files");
        }
        System.setProperty("sun.awt.fontconfig", config.toString());
        System.setProperty("sun.java2d.fontpath", directory.toString());
        log("FONT_CONFIG_READY mode=explicit-java-properties logicalStyles=20; system font files read-only");
        for (Path path : files) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
            log("FONT_FILE_VALIDATED file=" + path.getFileName() + " name=" + font.getFontName(Locale.ROOT));
        }
        rasterProbe();
    }

    private static void rasterProbe() {
        String sample = "Wurm Thor 0123";
        int checked = 0;
        for (String family : List.of(Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED, Font.DIALOG, Font.DIALOG_INPUT)) {
            for (int style = 0; style < 4; style++) {
                BufferedImage image = new BufferedImage(256, 64, BufferedImage.TYPE_4BYTE_ABGR);
                Graphics2D graphics = image.createGraphics();
                int width, ascent;
                try {
                    Font font = new Font(family, style, 18);
                    if (font.canDisplayUpTo(sample) != -1) throw new IllegalStateException("FONT_SAMPLE_GLYPHS_MISSING " + family);
                    graphics.setFont(font);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    FontMetrics metrics = graphics.getFontMetrics();
                    width = metrics.stringWidth(sample); ascent = metrics.getAscent();
                    if (width <= 0 || width > 250 || ascent <= 0 || metrics.getHeight() > 56)
                        throw new IllegalStateException("FONT_METRICS_INVALID " + family);
                    graphics.setColor(Color.WHITE); graphics.drawString(sample, 2, 2 + ascent);
                } finally { graphics.dispose(); }
                int pixels = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    if ((image.getRGB(x, y) >>> 24) != 0) pixels++;
                if (pixels == 0) throw new IllegalStateException("FONT_RASTER_EMPTY " + family);
                log("FONT_RASTER_OK family=" + family + " style=" + style + " width=" + width + " ascent=" + ascent + " pixels=" + pixels);
                checked++;
            }
        }
        log("FONT_PREFLIGHT_PASS logicalStyles=" + checked + "; CPU text rasterization only, Wurm GL upload/login not tested");
    }
}
