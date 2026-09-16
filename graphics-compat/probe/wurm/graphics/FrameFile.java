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
        if (width < 16 || height < 16 || width > 1280 || height > 1024 || sequence < 1 ||
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
    /** One owned publisher per window. Reuses metadata and view buffers across frames. */
    public static final class RawWriter implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private final Path target,pending;
        private static final java.util.Set<StandardOpenOption> OPTIONS=java.util.Set.of(
            StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        private static final CopyOption[] MOVE={StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING};
        private ByteBuffer header=ByteBuffer.allocateDirect(36),source,view;
        public RawWriter(Path target) {this.target=target;pending=target.resolveSibling(target.getFileName()+".pending");}
        private void owned() {if(Thread.currentThread()!=owner||header==null)throw new IllegalStateException("Frame writer is closed or not owned");}
        public void write(int width,int height,int sequence,ByteBuffer rgba,int x,int y,boolean visible,int applied)throws IOException {
            owned();
            if(width<16||height<16||width>1280||height>1024||sequence<1||rgba.remaining()!=width*height*4||x<0||x>=width||y<0||y>=height||applied<0)
                throw new IllegalArgumentException("Invalid raw frame");
            if(source!=rgba){source=rgba;view=rgba.duplicate();}
            // Source position/limit, channels and bottom-up origin remain untouched.
            view.clear().limit(rgba.limit()).position(rgba.position());
            header.clear();header.putInt(MAGIC).putInt(3).putInt(width).putInt(height).putInt(sequence)
                .putInt(x).putInt(y).putInt(visible?1:0).putInt(applied).flip();
            try {
                try(var channel=java.nio.channels.FileChannel.open(pending,OPTIONS)) {
                    while(header.hasRemaining())channel.write(header);
                    while(view.hasRemaining())channel.write(view);
                }
                Files.move(pending,target,MOVE);
            } finally {Files.deleteIfExists(pending);}
        }
        public void close() {owned();source=null;view=null;header=null;}
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
        if (width < 16 || height < 16 || width > 1280 || height > 1024 || sequence < 1 ||
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
