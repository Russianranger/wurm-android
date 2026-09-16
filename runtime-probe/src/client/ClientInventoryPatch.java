package client;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Replace only the inspected numeric alignment invocation; leave layout intact. */
final class ClientInventoryPatch {
    static final String PANEL="com/wurmonline/client/renderer/gui/WurmTreeList$TreeListPanel.class";
    static final String ORIGINAL="6b235f2eb2ce126f95ee4526592e6546d47dd19fcb30a042d731f914c6d359ee";
    static byte[] prepare(byte[] original)throws Exception {
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL))throw new IOException("INVENTORY_PATCH_UNSUPPORTED");
        return transform(original,false,2139,"renderComponent","(Lcom/wurmonline/client/renderer/backend/Queue;F)V");
    }
    static void verify(byte[] patched)throws Exception {
        byte[] original=transform(patched,true,2139,"renderComponent","(Lcom/wurmonline/client/renderer/backend/Queue;F)V");
        if(!ClientGraphicsPatch.sha(original).equals(ORIGINAL)||!Arrays.equals(patched,prepare(original)))
            throw new IOException("INVENTORY_PATCH_INTEGRITY_FAILED");
    }
    // Authored fixtures may supply their own method/offset, never production inputs.
    static byte[] transform(byte[] bytes,boolean reverse,int offset,String method,String descriptor)throws Exception {
        return ClientGuiPatch.transform(bytes,reverse,List.of(new ClientGuiPatch.Site(offset,0xb6,
            "java/lang/String","matches","(Ljava/lang/String;)Z","matches","(Ljava/lang/String;Ljava/lang/String;)Z")),
            method,descriptor,"client/ClientInventoryNumbers");
    }
}
