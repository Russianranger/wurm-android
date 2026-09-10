package wurm.graphics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Bounded, atomic ARGB frame shared with Android; no native handles cross processes. */
public final class FrameFile {
    public static final int MAGIC = 0x57554746;
    public static void write(Path target, int width, int height, int sequence, ByteBuffer rgba) throws IOException {
        writeFrame(target, width, height, sequence, rgba, null);
    }
    public static void write(Path target, int width, int height, int sequence, ByteBuffer rgba,
                             int cursorX, int cursorY, boolean visible, int applied) throws IOException {
        if (cursorX < 0 || cursorX >= width || cursorY < 0 || cursorY >= height || applied < 0)
            throw new IllegalArgumentException("Invalid pointer metadata");
        writeFrame(target, width, height, sequence, rgba, new int[]{cursorX, cursorY, visible ? 1 : 0, applied});
    }
    private static void writeFrame(Path target, int width, int height, int sequence, ByteBuffer rgba, int[] pointer) throws IOException {
        if (width < 16 || height < 16 || width > 1024 || height > 1024 || sequence < 1 ||
            rgba.remaining() != width * height * 4) throw new IllegalArgumentException("Invalid frame");
        Path pending = target.resolveSibling(target.getFileName()+".pending");
        try {
            try (FileOutputStream file = new FileOutputStream(pending.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeInt(MAGIC); out.writeInt(pointer == null ? 1 : 2); out.writeInt(width); out.writeInt(height); out.writeInt(sequence);
                if (pointer != null) for (int field : pointer) out.writeInt(field);
                // glReadPixels origin is bottom-left; Android bitmap origin is top-left.
                for (int y = height - 1; y >= 0; y--) for (int x = 0; x < width; x++) {
                    int p = rgba.position() + (y * width + x) * 4;
                    out.writeInt(((rgba.get(p+3)&255)<<24) | ((rgba.get(p)&255)<<16) | ((rgba.get(p+1)&255)<<8) | (rgba.get(p+2)&255));
                }
                out.flush(); file.getFD().sync();
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
