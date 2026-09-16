package client;

import java.io.*;
import java.util.*;

/** Six inspected call sites in Renderer.execute; same stack/length, byte-exact reversal. */
final class ClientGuiPatch {
    static final String RENDERER="com/wurmonline/client/renderer/gui/Renderer.class";
    static final String ORIGINAL="44d7584a7273457ebf13b6a7896eef57ddf05f2bcb25d950b979f48c2b617112";
    private static final String BASE="com/wurmonline/client/renderer/", QUEUE="L"+BASE+"backend/Queue;";
    record Site(int offset,int opcode,String owner,String name,String desc,String helper,String erased) { }
    static final List<Site> SITES=List.of(
        overlay(36,"renderSpyglassDistance","spyglass"), overlay(41,"renderCrosshair","crosshair"),
        overlay(68,"renderCrosshair","crosshair"),
        new Site(132,0xb6,BASE+"gui/WurmComponent","render","("+QUEUE+"F)V","component","(Ljava/lang/Object;Ljava/lang/Object;F)V"),
        new Site(199,0xb7,BASE+"gui/Renderer","renderHoverInfo","("+QUEUE+"II)V","hover","(Ljava/lang/Object;Ljava/lang/Object;II)V"),
        overlay(204,"renderOnscreenMessageViewer","messages"));
    private static Site overlay(int offset,String name,String helper) {
        return new Site(offset,0xb7,BASE+"gui/Renderer",name,"("+QUEUE+")V",helper,"(Ljava/lang/Object;Ljava/lang/Object;)V");
    }
    private record Entry(int tag,byte[] data) { }
    private static int u2(byte[] b,int p){return (b[p]&255)*256+(b[p+1]&255);}
    private static byte[] pair(int a,int b){return new byte[]{(byte)(a>>8),(byte)a,(byte)(b>>8),(byte)b};}
    private static Entry utf(String s)throws IOException{var b=new ByteArrayOutputStream();new DataOutputStream(b).writeUTF(s);return new Entry(1,b.toByteArray());}
    private static String text(List<Entry> cp,int i)throws IOException{return new DataInputStream(new ByteArrayInputStream(cp.get(i).data)).readUTF();}
    static byte[] prepare(byte[] original)throws Exception{
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL))throw new IOException("GUI_PATCH_UNSUPPORTED");
        return transform(original,false,SITES);
    }
    static void verify(byte[] patched)throws Exception{
        byte[] original=transform(patched,true,SITES);
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL)||!Arrays.equals(patched,prepare(original)))throw new IOException("GUI_PATCH_INTEGRITY_FAILED");
    }
    // Authored fixtures supply their own inspected offsets, never production inputs.
    static byte[] transform(byte[] bytes,boolean reverse,List<Site> sites)throws Exception{
        if(bytes.length>65536)throw new IOException("Oversized GUI class");
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0xcafebabe)throw new IOException("Invalid GUI class");
        int minor=in.readUnsignedShort(),major=in.readUnsignedShort(),count=in.readUnsignedShort();
        if(major>61)throw new IOException("Unsupported GUI class");
        var cp=new ArrayList<Entry>();cp.add(null);
        for(int i=1;i<count;i++){
            int tag=in.readUnsignedByte();byte[] data;
            if(tag==1){int n=in.readUnsignedShort();data=new byte[n+2];data[0]=(byte)(n>>8);data[1]=(byte)n;in.readFully(data,2,n);}
            else{int n=switch(tag){case 3,4,9,10,11,12,17,18->4;case 5,6->8;case 7,8,16,19,20->2;case 15->3;default->throw new IOException("Invalid GUI constant");};data=new byte[n];in.readFully(data);}
            cp.add(new Entry(tag,data));if(tag==5||tag==6){cp.add(null);i++;}
        }
        int base=reverse?count-sites.size()*6:count;
        if(base<1||base+sites.size()*6>=65535)throw new IOException("Invalid GUI pool size");
        byte[] tail=in.readAllBytes();int code=executeCode(tail,cp),length=readInt(tail,code);code+=4;
        for(int s=0;s<sites.size();s++){
            Site site=sites.get(s);int ref=0;
            for(int i=1;i<base;i++){
                Entry e=cp.get(i);if(e==null||e.tag!=10)continue;
                Entry owner=cp.get(u2(e.data,0)),nt=cp.get(u2(e.data,2));
                if(text(cp,u2(owner.data,0)).equals(site.owner)&&text(cp,u2(nt.data,0)).equals(site.name)&&text(cp,u2(nt.data,2)).equals(site.desc)){
                    if(ref!=0)throw new IOException("Ambiguous GUI call");ref=i;
                }
            }
            if(ref==0||site.offset<0||site.offset+3>length)throw new IOException("Missing GUI call");
            int b=base+s*6;
            List<Entry> added=List.of(utf("client/ClientGuiProfiler"),new Entry(7,Arrays.copyOf(pair(b,0),2)),utf(site.helper),utf(site.erased),new Entry(12,pair(b+2,b+3)),new Entry(10,pair(b+1,b+4)));
            if(reverse){for(int i=0;i<6;i++){Entry a=added.get(i),v=cp.get(b+i);if(a.tag!=v.tag||!Arrays.equals(a.data,v.data))throw new IOException("Invalid GUI helper");}}
            else cp.addAll(added);
            int p=code+site.offset;
            if((tail[p]&255)!=(reverse?0xb8:site.opcode)||u2(tail,p+1)!=(reverse?b+5:ref))throw new IOException("GUI call site mismatch");
            int target=reverse?ref:b+5;tail[p]=(byte)(reverse?site.opcode:0xb8);tail[p+1]=(byte)(target>>8);tail[p+2]=(byte)target;
        }
        if(reverse)cp.subList(base,cp.size()).clear();
        var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);
        out.writeInt(0xcafebabe);out.writeShort(minor);out.writeShort(major);out.writeShort(cp.size());
        for(Entry e:cp)if(e!=null){out.writeByte(e.tag);out.write(e.data);}out.write(tail);return b.toByteArray();
    }
    private static int readInt(byte[] b,int p)throws IOException{int n=new DataInputStream(new ByteArrayInputStream(b,p,4)).readInt();if(n<0)throw new IOException("Invalid GUI size");return n;}
    private static int skipAttrs(byte[] b,int p)throws IOException{int n=u2(b,p);p+=2;for(int i=0;i<n;i++){p+=6+readInt(b,p+2);if(p>b.length)throw new IOException("Truncated GUI class");}return p;}
    private static int executeCode(byte[] b,List<Entry> cp)throws IOException{
        int p=8+2*u2(b,6),fields=u2(b,p);p+=2;for(int i=0;i<fields;i++)p=skipAttrs(b,p+6);
        int methods=u2(b,p),found=-1;p+=2;
        for(int i=0;i<methods;i++){
            boolean target=text(cp,u2(b,p+2)).equals("execute")&&text(cp,u2(b,p+4)).equals("(Ljava/lang/Object;)V");
            int attrs=u2(b,p+6);p+=8;
            for(int j=0;j<attrs;j++){int n=readInt(b,p+2);if(target&&text(cp,u2(b,p)).equals("Code")){if(found!=-1)throw new IOException("Ambiguous GUI execute");found=p+10;}p+=6+n;if(p>b.length)throw new IOException("Truncated GUI method");}
        }
        if(found<0)throw new IOException("Missing GUI execute");return found;
    }
}
