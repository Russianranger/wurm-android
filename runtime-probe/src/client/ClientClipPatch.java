package client;

import java.io.*;
import java.util.*;

/** Wrap the exact ClipRect intersection method; preserve its original implementation. */
final class ClientClipPatch {
    static final String TYPE="com/wurmonline/client/renderer/backend/ScissorControl$ClipRect";
    static final String CLASS=TYPE+".class";
    static final String ORIGINAL="fa0ab689092d527e86188b38a33049f770140d5fdf47d28564df409cd246aac6";
    static final String OWNER="com/wurmonline/client/renderer/backend/ScissorControl.class";
    static final String OWNER_SHA="a2ff8b75e313dc5201b6b9f22de0f8067281a1f3fee9b1085da6db616ea37d01";
    private static final String DESC="(IIII)L"+TYPE+";", MARKER="WurmAndroidClipSnapshots";
    static byte[] prepare(byte[] original)throws Exception {
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL))throw new IOException("CLIP_PATCH_UNSUPPORTED");
        return transform(original,false);
    }
    static void verify(byte[] patched)throws Exception {
        byte[] original=transform(patched,true);
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL)||!Arrays.equals(patched,prepare(original)))
            throw new IOException("CLIP_PATCH_INTEGRITY_FAILED");
    }
    // Shared class-file codec; original method bytes, attributes and stack maps are retained.
    static byte[] transform(byte[] source,boolean reverse)throws Exception {
        var f=new ClientTextPatch.File(source);
        int count=f.cp.size(),methods=f.methods.size(),oldName=-1;
        if(reverse) {
            if(f.attrs.isEmpty())throw new IOException("Missing clip marker");
            var marker=f.attrs.remove(f.attrs.size()-1);
            if(!f.utf(marker.name()).equals(MARKER)||marker.bytes().length!=6)throw new IOException("Invalid clip marker");
            var in=new DataInputStream(new ByteArrayInputStream(marker.bytes()));
            count=in.readUnsignedShort();methods=in.readUnsignedShort();oldName=in.readUnsignedShort();
            if(count<1||count>=f.cp.size()||methods!=f.methods.size()-1||!f.utf(oldName).equals("clip"))throw new IOException("Invalid clip structure");
        } else for(var a:f.attrs)if(f.utf(a.name()).equals(MARKER))throw new IOException("Already patched clip");
        int selected=-1;
        for(int i=0;i<methods;i++) {
            var m=f.methods.get(i);
            if(f.utf(m.name()).equals(reverse?"androidOriginalClip":"clip")&&f.utf(m.desc()).equals(DESC)) {
                if(selected!=-1)throw new IOException("Ambiguous clip method");selected=i;
            }
            if(!reverse&&f.utf(m.name()).equals("androidOriginalClip"))throw new IOException("Existing clip bridge");
        }
        if(selected<0)throw new IOException("Missing clip method");
        var original=f.methods.get(selected);
        if(!reverse)oldName=original.name();
        f.methods.set(selected,new ClientTextPatch.Member(original.access(),reverse?oldName:f.utf("androidOriginalClip"),original.desc(),original.attrs()));
        if(reverse) {
            f.cp.subList(count,f.cp.size()).clear();f.methods.subList(methods,f.methods.size()).clear();
        } else {
            var code=new ByteArrayOutputStream();var out=new DataOutputStream(code);
            out.writeByte(0x2a); // this
            for(int i=1;i<=4;i++){out.writeByte(0x15);out.writeByte(i);}
            out.writeByte(0xb8);out.writeShort(f.method("client/ClientClipSnapshots","clip","(Ljava/lang/Object;IIII)Ljava/lang/Object;"));
            out.writeByte(0xc0);out.writeShort(f.self);out.writeByte(0xb0);
            var body=new ByteArrayOutputStream();var data=new DataOutputStream(body);
            data.writeShort(5);data.writeShort(5);data.writeInt(code.size());data.write(code.toByteArray());data.writeShort(0);data.writeShort(0);
            f.methods.add(new ClientTextPatch.Member(original.access()|0x1000,oldName,original.desc(),List.of(new ClientTextPatch.Attribute(f.utf("Code"),body.toByteArray()))));
            f.attrs.add(new ClientTextPatch.Attribute(f.utf(MARKER),ClientTextPatch.shorts(count,methods,oldName)));
        }
        return f.write();
    }
}
