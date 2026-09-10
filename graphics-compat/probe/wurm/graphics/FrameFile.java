package wurm.graphics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Bounded, atomic frame shared with Android; no native handles cross processes. */
public final class FrameFile {
    public static final int MAGIC = 0x57554746;
    /** V3 is raw RGBA/bottom-up. Android copies it directly and flips only while drawing. */
    public static void writeRgba(Path target, int width, int height, int sequence, ByteBuffer rgba,
                                 int cursorX, int cursorY, boolean visible, int applied) throws IOException {
        if (width < 16 || height < 16 || width > 1024 || height > 1024 || sequence < 1 ||
            rgba.remaining() != width*height*4 || cursorX < 0 || cursorX >= width ||
            cursorY < 0 || cursorY >= height || applied < 0) throw new IllegalArgumentException("Invalid raw frame");
        Path pending=target.resolveSibling(target.getFileName()+".pending");
        try {
            try (var channel=java.nio.channels.FileChannel.open(pending, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer header=ByteBuffer.allocate(36);
                for (int value : new int[]{MAGIC,3,width,height,sequence,cursorX,cursorY,visible?1:0,applied}) header.putInt(value);
                header.flip(); while (header.hasRemaining()) channel.write(header);
                ByteBuffer view=rgba.duplicate(); while (view.hasRemaining()) channel.write(view);
            }
            Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
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
                // Bulk row copies avoid a DataOutputStream call for every pixel.
                // glReadPixels origin is bottom-left; file pixels are big-endian ARGB, top-left.
                byte[] row = new byte[width * 4];
                ByteBuffer view = rgba.duplicate();
                for (int y = height - 1; y >= 0; y--) {
                    view.position(rgba.position() + y * row.length);
                    view.get(row);
                    for (int x = 0; x < row.length; x += 4) {
                        byte alpha = row[x+3];
                        row[x+3] = row[x+2]; row[x+2] = row[x+1]; row[x+1] = row[x]; row[x] = alpha;
                    }
                    out.write(row);
                }
                // This is a disposable display frame, not a world save. Close + atomic rename
                // gives readers a complete image without forcing flash storage on every frame.
                out.flush();
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
