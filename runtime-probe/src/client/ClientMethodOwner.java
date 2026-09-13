package client;

import java.io.*;
import java.util.*;

/** Retarget one method reference, retaining every instruction and attribute byte. */
final class ClientMethodOwner {
    private record Entry(int tag, byte[] data) {}
    private static int u2(byte[] b, int p) { return (b[p]&255)*256+(b[p+1]&255); }
    private static byte[] pair(int a, int b) { return new byte[]{(byte)(a>>8),(byte)a,(byte)(b>>8),(byte)b}; }
    private static String utf(List<Entry> cp, int i) throws IOException {
        Entry e=cp.get(i);
        if(e==null || e.tag!=1) throw new IOException("Invalid method-owner UTF8 index");
        return new DataInputStream(new ByteArrayInputStream(e.data)).readUTF();
    }
    static byte[] redirect(byte[] bytes, String expected, String from, String to,
                           String method, String descriptor, boolean reverse) throws Exception {
        if(bytes.length>4*1024*1024 || !ClientGraphicsPatch.sha(bytes).equals(expected))
            throw new IOException("CLIENT_METHOD_OWNER_UNSUPPORTED");
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0xcafebabe) throw new IOException("Invalid class magic");
        int minor=in.readUnsignedShort(), major=in.readUnsignedShort();
        if(major>61) throw new IOException("Unsupported class version");
        int count=in.readUnsignedShort();
        var cp=new ArrayList<Entry>(); cp.add(null);
        for(int i=1;i<count;i++) {
            int tag=in.readUnsignedByte(); byte[] data;
            if(tag==1) {
                int n=in.readUnsignedShort(); byte[] text=in.readNBytes(n);
                if(text.length!=n) throw new EOFException();
                data=new byte[n+2]; data[0]=(byte)(n>>8); data[1]=(byte)n;
                System.arraycopy(text,0,data,2,n);
            } else {
                int n=switch(tag) { case 3,4,9,10,11,12,17,18->4; case 5,6->8;
                    case 7,8,16,19,20->2; case 15->3; default->throw new IOException("Invalid pool tag"); };
                data=in.readNBytes(n); if(data.length!=n) throw new EOFException();
            }
            cp.add(new Entry(tag,data));
            if(tag==5 || tag==6) { cp.add(null); i++; }
        }
        String owner=reverse?to:from;
        int ref=0, oldOwner=0;
        for(int i=1;i<cp.size();i++) {
            Entry e=cp.get(i); if(e==null || e.tag!=10) continue;
            Entry c=cp.get(u2(e.data,0)), nt=cp.get(u2(e.data,2));
            if(c==null || c.tag!=7 || nt==null || nt.tag!=12) throw new IOException("Invalid method reference");
            if(utf(cp,u2(c.data,0)).equals(owner) && utf(cp,u2(nt.data,0)).equals(method) && utf(cp,u2(nt.data,2)).equals(descriptor)) {
                if(ref!=0) throw new IOException("Ambiguous method reference");
                ref=i; oldOwner=u2(e.data,0);
            }
        }
        if(ref==0) throw new IOException("Required method reference missing");
        int newOwner;
        if(reverse) {
            if(oldOwner!=count-1 || cp.get(count-1).tag!=7 || u2(cp.get(count-1).data,0)!=count-2 || !utf(cp,count-2).equals(to))
                throw new IOException("Invalid appended method owner");
            newOwner=0;
            for(int i=1;i<count-2;i++) {
                Entry e=cp.get(i);
                if(e!=null && e.tag==7 && utf(cp,u2(e.data,0)).equals(from)) {
                    if(newOwner!=0) throw new IOException("Ambiguous original owner"); newOwner=i;
                }
            }
            if(newOwner==0) throw new IOException("Original method owner missing");
            cp.remove(cp.size()-1); cp.remove(cp.size()-1);
        } else {
            if(count>65533) throw new IOException("Constant pool full");
            var encoded=new ByteArrayOutputStream(); new DataOutputStream(encoded).writeUTF(to);
            cp.add(new Entry(1,encoded.toByteArray()));
            cp.add(new Entry(7,Arrays.copyOf(pair(count,0),2))); newOwner=count+1;
        }
        cp.set(ref,new Entry(10,pair(newOwner,u2(cp.get(ref).data,2))));
        var buffer=new ByteArrayOutputStream(); var out=new DataOutputStream(buffer);
        out.writeInt(0xcafebabe); out.writeShort(minor); out.writeShort(major); out.writeShort(cp.size());
        for(Entry e:cp) if(e!=null) { out.writeByte(e.tag); out.write(e.data); }
        in.transferTo(out); return buffer.toByteArray();
    }
}
