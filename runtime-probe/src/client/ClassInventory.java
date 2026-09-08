package client;

import java.io.*;
import java.util.*;

/** Reads API metadata without resolving dependencies, initializing classes or exporting code. */
public final class ClassInventory {
    public record Member(int flags, String name, String descriptor) {}
    public record Info(String name, List<Member> fields, List<Member> methods, Set<String> references) {}
    public static Info read(InputStream source) throws IOException {
        byte[] bytes = source.readNBytes(4 * 1024 * 1024 + 1);
        if (bytes.length > 4 * 1024 * 1024) throw new IOException("Class exceeds metadata limit");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xcafebabe) throw new IOException("Not a class file");
        in.readUnsignedShort(); int major = in.readUnsignedShort();
        if (major > 61) throw new IOException("Requires newer JVM: class version " + major);
        Object[] cp = new Object[in.readUnsignedShort()];
        Set<Integer> classes = new HashSet<>();
        for (int i = 1; i < cp.length; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> cp[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7 -> { cp[i] = in.readUnsignedShort(); classes.add(i); }
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> { in.readUnsignedShort(); in.readUnsignedShort(); }
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException("Unknown constant tag " + tag);
            }
        }
        in.readUnsignedShort(); int self = in.readUnsignedShort(); in.readUnsignedShort();
        int interfaces = in.readUnsignedShort(); for (int i = 0; i < interfaces; i++) in.readUnsignedShort();
        List<Member> fields = members(in, cp); List<Member> methods = members(in, cp);
        Set<String> refs = new TreeSet<>();
        for (int index : classes) refs.add((String) cp[(Integer) cp[index]]);
        return new Info((String) cp[(Integer) cp[self]], fields, methods, refs);
    }
    private static List<Member> members(DataInputStream in, Object[] cp) throws IOException {
        List<Member> result = new ArrayList<>();
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            result.add(new Member(in.readUnsignedShort(), (String) cp[in.readUnsignedShort()], (String) cp[in.readUnsignedShort()]));
            int attrs = in.readUnsignedShort();
            for (int a = 0; a < attrs; a++) {
                in.readUnsignedShort(); long length = Integer.toUnsignedLong(in.readInt());
                if (length > 4 * 1024 * 1024) throw new IOException("Attribute exceeds limit");
                in.skipNBytes(length);
            }
        }
        return result;
    }
}
