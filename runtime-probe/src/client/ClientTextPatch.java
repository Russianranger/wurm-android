package client;

import java.io.*;
import java.util.*;

/** Hash-pinned text factory/release adapters. Original instruction bodies remain intact. */
final class ClientTextPatch {
    static final String VERTEX="com/wurmonline/client/renderer/backend/VertexBuffer";
    static final String FONT="com/wurmonline/client/renderer/gui/text/SimpleTextFont.class";
    static final String QUEUE="com/wurmonline/client/renderer/backend/Queue.class";
    static final String FACTORY="(L"+VERTEX+"$Usage;IZZZZZIIZZ)L"+VERTEX+";";
    static final String ERASED="(Ljava/lang/Object;IZZZZZIIZZ)Ljava/lang/Object;";
    static final Map<String,String> ORIGINALS=Map.of(
        VERTEX+".class","68f99c3b477eff2507dc498ea5a1546ee99c7b854f1c1f1fcfa482de3fc2d766",
        FONT,"b306197e0eb400cdbcfaa4d93b9d9fb0c30b5e0f4f1cec522d43dc07ff77fb1c",
        QUEUE,"ba694299f062f82e7ff33637d0e2d3095bf94fed1d182dd49919a33640be819c");
    private static final String MARKER="WurmAndroidTextBuffers";
    record Entry(int tag,byte[] bytes) { }
    record Attribute(int name,byte[] bytes) { }
    record Member(int access,int name,int desc,List<Attribute> attrs) { }
    private static int u2(byte[] b,int p) { return (b[p]&255)*256+(b[p+1]&255); }
    static byte[] shorts(int... values) throws IOException {
        var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);
        for(int v:values)out.writeShort(v);return b.toByteArray();
    }
    private static byte[] bytes(DataInputStream in,int n) throws IOException {
        if(n<0 || n>1_048_576)throw new IOException("Invalid text patch length");
        byte[] b=new byte[n];in.readFully(b);return b;
    }
    static final class File {
        final List<Entry> cp=new ArrayList<>();
        final List<Member> fields=new ArrayList<>(),methods=new ArrayList<>();
        List<Attribute> attrs;int minor,major,access,self,parent;byte[] interfaces;
        File(byte[] source) throws IOException {
            var in=new DataInputStream(new ByteArrayInputStream(source));
            if(in.readInt()!=0xcafebabe)throw new IOException("Invalid text class");
            minor=in.readUnsignedShort();major=in.readUnsignedShort();
            if(major>61)throw new IOException("Unsupported text class version");
            int count=in.readUnsignedShort();cp.add(null);
            for(int i=1;i<count;i++) {
                int tag=in.readUnsignedByte();byte[] b;
                if(tag==1) {int n=in.readUnsignedShort();var out=new ByteArrayOutputStream();out.write(shorts(n));out.write(bytes(in,n));b=out.toByteArray();}
                else b=bytes(in,switch(tag){case 3,4,9,10,11,12,17,18->4;case 5,6->8;case 7,8,16,19,20->2;case 15->3;default->throw new IOException("Invalid text constant");});
                cp.add(new Entry(tag,b));if(tag==5||tag==6){cp.add(null);i++;}
            }
            access=in.readUnsignedShort();self=in.readUnsignedShort();parent=in.readUnsignedShort();
            interfaces=bytes(in,2*in.readUnsignedShort());
            readMembers(in,fields);readMembers(in,methods);attrs=readAttrs(in);
            if(in.read()!=-1)throw new IOException("Trailing text class bytes");
        }
        List<Attribute> readAttrs(DataInputStream in) throws IOException {
            var out=new ArrayList<Attribute>();int count=in.readUnsignedShort();
            for(int i=0;i<count;i++)out.add(new Attribute(in.readUnsignedShort(),bytes(in,in.readInt())));return out;
        }
        void readMembers(DataInputStream in,List<Member> out) throws IOException {
            int count=in.readUnsignedShort();for(int i=0;i<count;i++)out.add(new Member(in.readUnsignedShort(),in.readUnsignedShort(),in.readUnsignedShort(),readAttrs(in)));
        }
        String utf(int i) throws IOException { Entry e=cp.get(i);if(e.tag!=1)throw new IOException("Expected UTF8");return new DataInputStream(new ByteArrayInputStream(e.bytes)).readUTF(); }
        int add(int tag,byte[] b) throws IOException {if(cp.size()>=65535)throw new IOException("Text constant pool full");cp.add(new Entry(tag,b));return cp.size()-1;}
        int utf(String s) throws IOException {var b=new ByteArrayOutputStream();new DataOutputStream(b).writeUTF(s);return add(1,b.toByteArray());}
        int type(String s) throws IOException {return add(7,shorts(utf(s)));}
        int nt(String name,String desc) throws IOException {return add(12,shorts(utf(name),utf(desc)));}
        int method(String owner,String name,String desc) throws IOException {return add(10,shorts(type(owner),nt(name,desc)));}
        void redirect(String oldName,String newName,String desc,int originalCount,boolean reverse) throws IOException {
            int oldNt=0,ref=0;
            for(int i=1;i<originalCount;i++) {
                Entry e=cp.get(i);if(e==null)continue;
                if(e.tag==12&&utf(u2(e.bytes,0)).equals(oldName)&&utf(u2(e.bytes,2)).equals(desc))oldNt=i;
                if(e.tag==10) {
                    Entry owner=cp.get(u2(e.bytes,0)),nt=cp.get(u2(e.bytes,2));
                    if(utf(u2(owner.bytes,0)).equals(VERTEX)&&utf(u2(nt.bytes,0)).equals(reverse?newName:oldName)&&utf(u2(nt.bytes,2)).equals(desc)) {
                        if(ref!=0)throw new IOException("Ambiguous text call");ref=i;
                    }
                }
            }
            if(ref==0||oldNt==0)throw new IOException("Missing text call");
            Entry e=cp.get(ref);cp.set(ref,new Entry(10,shorts(u2(e.bytes,0),reverse?oldNt:nt(newName,desc))));
        }
        void bridge(boolean factory) throws IOException {
            String name=factory?"androidTextCreate":"androidTextDelete",desc=factory?FACTORY:"()V";
            for(Member m:methods)if(utf(m.name).equals(name))throw new IOException("Existing text adapter");
            var code=new ByteArrayOutputStream();var out=new DataOutputStream(code);
            out.writeByte(0x2a); // receiver / Usage reference
            if(factory)for(int i=1;i<11;i++){out.writeByte(0x15);out.writeByte(i);}
            out.writeByte(0xb8);out.writeShort(method("client/ClientTextBuffers",factory?"create":"release",factory?ERASED:"(Ljava/lang/Object;)V"));
            if(factory){out.writeByte(0xc0);out.writeShort(self);out.writeByte(0xb0);}else out.writeByte(0xb1);
            var body=new ByteArrayOutputStream();var data=new DataOutputStream(body);
            data.writeShort(factory?11:1);data.writeShort(factory?11:1);data.writeInt(code.size());data.write(code.toByteArray());data.writeShort(0);data.writeShort(0);
            methods.add(new Member(factory?0x1009:0x1001,utf(name),utf(desc),List.of(new Attribute(utf("Code"),body.toByteArray()))));
        }
        void writeAttrs(DataOutputStream out,List<Attribute> attrs) throws IOException {out.writeShort(attrs.size());for(Attribute a:attrs){out.writeShort(a.name);out.writeInt(a.bytes.length);out.write(a.bytes);}}
        void writeMembers(DataOutputStream out,List<Member> members) throws IOException {out.writeShort(members.size());for(Member m:members){out.writeShort(m.access);out.writeShort(m.name);out.writeShort(m.desc);writeAttrs(out,m.attrs);}}
        byte[] write() throws IOException {
            var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeInt(0xcafebabe);out.writeShort(minor);out.writeShort(major);out.writeShort(cp.size());
            for(Entry e:cp)if(e!=null){out.writeByte(e.tag);out.write(e.bytes);}
            out.writeShort(access);out.writeShort(self);out.writeShort(parent);out.writeShort(interfaces.length/2);out.write(interfaces);
            writeMembers(out,fields);writeMembers(out,methods);writeAttrs(out,attrs);return b.toByteArray();
        }
    }
    static byte[] prepare(String name,byte[] original) throws Exception {
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINALS.get(name)))throw new IOException("TEXT_PATCH_UNSUPPORTED class="+name);
        return transform(name,original,false);
    }
    static void verify(String name,byte[] patched) throws Exception {
        byte[] original=transform(name,patched,true);
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINALS.get(name))||!Arrays.equals(patched,transform(name,original,false)))throw new IOException("TEXT_PATCH_INTEGRITY_FAILED class="+name);
    }
    // Authored fixtures exercise the same transformation with their own original bytes.
    static byte[] transform(String name,byte[] source,boolean reverse) throws Exception {
        if(!ORIGINALS.containsKey(name)||source.length>1_048_576)throw new IOException("Unknown text patch class");
        File f=new File(source);int count=f.cp.size(),methods=f.methods.size();
        if(reverse) {
            if(f.attrs.isEmpty())throw new IOException("Missing text marker");
            Attribute a=f.attrs.remove(f.attrs.size()-1);
            if(!f.utf(a.name).equals(MARKER)||a.bytes.length!=4)throw new IOException("Invalid text marker");
            count=u2(a.bytes,0);methods=u2(a.bytes,2);
            if(count<1||count>=f.cp.size()||methods>f.methods.size())throw new IOException("Invalid original text structure");
        } else for(Attribute a:f.attrs)if(f.utf(a.name).equals(MARKER))throw new IOException("Already patched text class");
        if(name.equals(FONT)) {f.redirect("create","androidTextCreate",FACTORY,count,reverse);f.redirect("delete","androidTextDelete","()V",count,reverse);}
        else if(name.equals(QUEUE))f.redirect("delete","androidTextDelete","()V",count,reverse);
        else if(!reverse){f.bridge(true);f.bridge(false);}
        if(reverse){f.cp.subList(count,f.cp.size()).clear();f.methods.subList(methods,f.methods.size()).clear();}
        else f.attrs.add(new Attribute(f.utf(MARKER),shorts(count,methods)));
        return f.write();
    }
}
