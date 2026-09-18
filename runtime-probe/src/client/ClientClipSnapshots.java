package client;

import java.lang.invoke.*;
import java.util.Arrays;

/** Bounded memoization of read-only clip results, never mutation/recycling of queued snapshots. */
public final class ClientClipSnapshots {
    static final int SLOTS=256;
    private static final Object[] snapshots=new Object[SLOTS];
    private static MethodHandle original,owner,x0,y0,x1,y1;
    private static Object cachedOwner;
    private static long calls,hits,misses,ownerChanges;
    private ClientClipSnapshots() { }
    public static boolean enabled(){return Boolean.getBoolean("wurm.client.reuseClipSnapshots");}
    private static void initialize()throws Exception {
        if(original!=null)return;
        Class<?> type=Class.forName(ClientClipPatch.TYPE.replace('/','.'));
        Class<?> control=Class.forName(ClientClipPatch.OWNER.replace('/','.').replace(".class",""));
        var lookup=MethodHandles.privateLookupIn(type,MethodHandles.lookup());
        x0=getter(lookup,type,"x0",int.class);y0=getter(lookup,type,"y0",int.class);
        x1=getter(lookup,type,"x1",int.class);y1=getter(lookup,type,"y1",int.class);
        owner=getter(lookup,type,"this$0",control);
        original=lookup.findVirtual(type,"androidOriginalClip",MethodType.methodType(type,int.class,int.class,int.class,int.class))
            .asType(MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class,int.class));
    }
    private static MethodHandle getter(MethodHandles.Lookup lookup,Class<?> type,String name,Class<?> result)throws Exception {
        return lookup.findGetter(type,name,result).asType(MethodType.methodType(result==int.class?int.class:Object.class,Object.class));
    }
    public static synchronized Object clip(Object parent,int a,int b,int c,int d)throws Throwable {
        initialize();calls++;
        Object control=(Object)owner.invokeExact(parent);
        // A single controller and 256 rectangles maximum; no accumulating owners or game trees.
        if(cachedOwner!=control){Arrays.fill(snapshots,null);cachedOwner=control;ownerChanges++;}
        int left=Math.max((int)x0.invokeExact(parent),a),top=Math.max((int)y0.invokeExact(parent),b);
        int right=Math.min((int)x1.invokeExact(parent),c),bottom=Math.min((int)y1.invokeExact(parent),d);
        int hash=left*31+top;hash=hash*31+right;hash=hash*31+bottom;hash^=hash>>>16;
        int slot=hash&(SLOTS-1);Object found=snapshots[slot];
        // Validate the snapshot itself, not just a stale key. We never change its fields.
        if(enabled()&&found!=null&&(Object)owner.invokeExact(found)==control&&
            (int)x0.invokeExact(found)==left&&(int)y0.invokeExact(found)==top&&
            (int)x1.invokeExact(found)==right&&(int)y1.invokeExact(found)==bottom){hits++;return found;}
        Object result=(Object)original.invokeExact(parent,a,b,c,d);misses++;
        if(enabled())snapshots[slot]=result;
        return result;
    }
    public static synchronized String sample() {
        int entries=0;for(Object value:snapshots)if(value!=null)entries++;
        return "CLIP_CACHE calls="+calls+" hits="+hits+" misses="+misses+" entries="+entries+" maxEntries="+SLOTS+
            " ownerChanges="+ownerChanges+" enabled="+enabled()+"; cumulative; snapshots never mutated; evictions may remain in original draw queues";
    }
    static synchronized void clear(){Arrays.fill(snapshots,null);cachedOwner=null;}
}
