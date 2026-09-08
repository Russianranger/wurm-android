package wurm.graphics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Bounded, atomic ARGB frame shared with Android; no native handles cross processes. */
public final class FrameFile {
    public static final int MAGIC = 0x57554746;
    public static void write(Path target, int width, int height, int sequence, ByteBuffer rgba) throws IOException {
        if (width < 16 || height < 16 || width > 1024 || height > 1024 || sequence < 1 ||
            rgba.remaining() != width * height * 4) throw new IllegalArgumentException("Invalid frame");
        Path pending = target.resolveSibling(target.getFileName()+".pending");
        try {
            try (FileOutputStream file = new FileOutputStream(pending.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                out.writeInt(MAGIC); out.writeInt(1); out.writeInt(width); out.writeInt(height); out.writeInt(sequence);
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
