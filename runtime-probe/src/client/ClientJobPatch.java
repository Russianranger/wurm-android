package client;

import java.io.*;
import java.util.*;

/** Opt-in, hash-pinned job call observation. No scheduler, callback or argument changes. */
final class ClientJobPatch {
    static final String EXECUTOR = "com/wurmonline/client/job/Executor.class";
    static final String ORIGINAL = "1ee869cee678c32ad38a6993ba0bd619cc3c0d106e33b37ee2c457e97c9ae621";
    private static final String JOB = "com/wurmonline/client/job/Job";
    private static final String HELPER = "client/ClientJobProfiler";
    private static final String DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;)V";
    private record Entry(int tag, byte[] data) { }
    private static int u2(byte[] b, int p) { return (b[p]&255)*256+(b[p+1]&255); }
    private static byte[] pair(int a, int b) { return new byte[]{(byte)(a>>8),(byte)a,(byte)(b>>8),(byte)b}; }
    private static Entry utf(String text) throws IOException {
        var out = new ByteArrayOutputStream(); new DataOutputStream(out).writeUTF(text);
        return new Entry(1, out.toByteArray());
    }
    private static String text(List<Entry> cp, int index) throws IOException {
        Entry e = cp.get(index);
        if (e == null || e.tag != 1) throw new IOException("Invalid job UTF8");
        return new DataInputStream(new ByteArrayInputStream(e.data)).readUTF();
    }
    static byte[] prepare(byte[] bytes) throws Exception { return patch(bytes, ORIGINAL, false); }
    static void verify(byte[] bytes) throws Exception {
        byte[] original = patch(bytes, ClientGraphicsPatch.sha(bytes), true);
        if (!ClientGraphicsPatch.sha(original).equals(ORIGINAL)) throw new IOException("JOB_PATCH_INTEGRITY_FAILED");
    }
    // Package-private fixture entry; production pins the complete original above.
    static byte[] patch(byte[] bytes, String expected, boolean reverse) throws Exception {
        if (bytes.length > 65536 || !ClientGraphicsPatch.sha(bytes).equals(expected)) throw new IOException("JOB_PATCH_UNSUPPORTED");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xcafebabe) throw new IOException("Invalid class");
        int minor = in.readUnsignedShort(), major = in.readUnsignedShort(), count = in.readUnsignedShort();
        if (major > 61 || count > 65530) throw new IOException("Unsupported job class");
        var cp = new ArrayList<Entry>(); cp.add(null);
        for (int i=1; i<count; i++) {
            int tag=in.readUnsignedByte(); byte[] data;
            if (tag==1) {
                int n=in.readUnsignedShort(); data=new byte[n+2]; data[0]=(byte)(n>>8); data[1]=(byte)n;
                in.readFully(data,2,n);
            } else {
                int n=switch(tag) { case 3,4,9,10,11,12,17,18 -> 4; case 5,6 -> 8;
                    case 7,8,16,19,20 -> 2; case 15 -> 3; default -> throw new IOException("Invalid pool tag"); };
                data=new byte[n]; in.readFully(data);
            }
            cp.add(new Entry(tag,data));
            if (tag==5 || tag==6) { cp.add(null); i++; }
        }
        int originalRef=0, methodName=0;
        for (int i=1; i<cp.size(); i++) {
            Entry e=cp.get(i); if(e==null || e.tag!=11) continue;
            Entry owner=cp.get(u2(e.data,0)), nt=cp.get(u2(e.data,2));
            if (owner.tag==7 && nt.tag==12 && text(cp,u2(owner.data,0)).equals(JOB) &&
                    text(cp,u2(nt.data,0)).equals("execute") && text(cp,u2(nt.data,2)).equals("(Ljava/lang/Object;)V")) {
                if(originalRef!=0) throw new IOException("Ambiguous job call");
                originalRef=i; methodName=u2(nt.data,0);
            }
        }
        if(originalRef==0) throw new IOException("Missing job call");
        int base=reverse?count-5:count;
        List<Entry> appended=List.of(utf(HELPER),new Entry(7,Arrays.copyOf(pair(base,0),2)),
            utf(DESCRIPTOR),new Entry(12,pair(methodName,base+2)),new Entry(10,pair(base+1,base+3)));
        if(reverse) {
            for(int i=0;i<5;i++) {
                Entry a=appended.get(i), b=cp.get(base+i);
                if(a.tag!=b.tag || !Arrays.equals(a.data,b.data)) throw new IOException("Invalid job helper pool");
            }
        } else cp.addAll(appended);
        byte[] tail=in.readAllBytes();
        int offset=runCodeOffset(tail,cp);
        int length=readInt(tail,offset); offset+=4;
        if(length<5 || offset+length>tail.length) throw new IOException("Invalid job code");
        byte[] old=reverse?new byte[]{(byte)0xb8,(byte)((base+4)>>8),(byte)(base+4),0,0}
            :new byte[]{(byte)0xb9,(byte)(originalRef>>8),(byte)originalRef,2,0};
        byte[] replacement=reverse?new byte[]{(byte)0xb9,(byte)(originalRef>>8),(byte)originalRef,2,0}
            :new byte[]{(byte)0xb8,(byte)((base+4)>>8),(byte)(base+4),0,0};
        int changes=0;
        for(int i=offset;i<=offset+length-5;i++) if(Arrays.equals(tail,i,i+5,old,0,5)) {
            System.arraycopy(replacement,0,tail,i,5); changes++; i+=4;
        }
        if(changes!=1) throw new IOException("Expected one job invoke, got "+changes);
        if(reverse) cp.subList(base,cp.size()).clear();
        var buffer=new ByteArrayOutputStream(); var out=new DataOutputStream(buffer);
        out.writeInt(0xcafebabe); out.writeShort(minor); out.writeShort(major); out.writeShort(cp.size());
        for(Entry e:cp) if(e!=null) { out.writeByte(e.tag); out.write(e.data); }
        out.write(tail); return buffer.toByteArray();
    }
    private static int readInt(byte[] b,int p) throws IOException {
        int n=new DataInputStream(new ByteArrayInputStream(b,p,4)).readInt();
        if(n<0) throw new IOException("Oversized class attribute"); return n;
    }
    private static int skipAttributes(byte[] b,int p) throws IOException {
        int n=u2(b,p); p+=2;
        for(int i=0;i<n;i++) { int size=readInt(b,p+2); p+=6+size; if(p>b.length) throw new IOException("Truncated attribute"); }
        return p;
    }
    private static int runCodeOffset(byte[] b,List<Entry> cp) throws IOException {
        int p=8+2*u2(b,6), fields=u2(b,p); p+=2;
        for(int i=0;i<fields;i++) p=skipAttributes(b,p+6);
        int methods=u2(b,p); p+=2; int found=-1;
        for(int i=0;i<methods;i++) {
            boolean run=text(cp,u2(b,p+2)).equals("run") && text(cp,u2(b,p+4)).equals("()V");
            int attrs=u2(b,p+6); p+=8;
            for(int j=0;j<attrs;j++) {
                int size=readInt(b,p+2);
                if(run && text(cp,u2(b,p)).equals("Code")) {
                    if(found!=-1) throw new IOException("Ambiguous run code"); found=p+10;
                }
                p+=6+size; if(p>b.length) throw new IOException("Truncated job method");
            }
        }
        if(found<0) throw new IOException("Missing run code"); return found;
    }
}
