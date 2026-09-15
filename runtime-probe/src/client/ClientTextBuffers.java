package client;

import java.lang.invoke.*;
import java.nio.FloatBuffer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded reuse of exact-size SimpleTextFont vertices after their original release point. */
public final class ClientTextBuffers {
    static final int SLOTS=256, MAX_BUFFER_BYTES=64*1024, MAX_BYTES=4*1024*1024;
    static final long IDLE_NANOS=30_000_000_000L;
    private static final Object[] vertices=new Object[SLOTS];
    private static final int[] sizes=new int[SLOTS];
    private static final boolean[] idle=new boolean[SLOTS];
    private static final long[] releasedAt=new long[SLOTS];
    private static MethodHandle factory,delete,systemBuffer,refCount,numVertex,allowGpu,useGpu,locked,bound;
    private static MethodHandle engineAllocated;
    private static Object gui;
    private static int entries,bytes;
    private static long calls,created,reused,releases,bypassed,evicted,zeroedBytes;
    private ClientTextBuffers() { }
    public static boolean enabled() {return Boolean.getBoolean("wurm.client.reuseTextBuffers");}
    private static void initialize() throws Throwable {
        if(factory!=null)return;
        Class<?> type=Class.forName(ClientTextPatch.VERTEX.replace('/','.'));
        Class<?> usage=Class.forName(ClientTextPatch.VERTEX.replace('/','.')+"$Usage");
        var lookup=MethodHandles.privateLookupIn(type,MethodHandles.lookup());
        var typed=MethodType.methodType(type,usage,int.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class,int.class,int.class,boolean.class,boolean.class);
        MethodHandle create=lookup.findStatic(type,"create",typed).asType(typed.changeParameterType(0,Object.class).changeReturnType(Object.class));
        delete=lookup.findVirtual(type,"delete",MethodType.methodType(void.class)).asType(MethodType.methodType(void.class,Object.class));
        systemBuffer=getter(lookup,type,"systemBuffer",FloatBuffer.class);
        refCount=getter(lookup,type,"refCount",int.class);numVertex=getter(lookup,type,"numVertex",int.class);
        allowGpu=getter(lookup,type,"allowGPU",boolean.class);bound=getter(lookup,type,"boundBufferObject",boolean.class);
        locked=getter(lookup,type,"isLocked",AtomicInteger.class);
        useGpu=lookup.findStatic(type,"useGPU",MethodType.methodType(boolean.class));
        gui=MethodHandles.publicLookup().findStaticGetter(usage,"GUI",usage).asType(MethodType.methodType(Object.class)).invokeExact();
        Class<?> buffers=Class.forName("com.wurmonline.client.util.BufferUtil");
        engineAllocated=MethodHandles.publicLookup().findStatic(buffers,"getAllocatedMemory",MethodType.methodType(int.class));
        factory=create; // Publish only after every required handle has resolved under the monitor.
    }
    private static MethodHandle getter(MethodHandles.Lookup lookup,Class<?> type,String name,Class<?> result) throws Exception {
        return lookup.findGetter(type,name,result).asType(MethodType.methodType(result,Object.class));
    }
    public static synchronized Object create(Object usage,int count,boolean coords,boolean weight,boolean normal,boolean color,boolean index,int tex0,int tex1,boolean dynamic,boolean last) throws Throwable {
        initialize();calls++;
        long size=(long)count*20;
        boolean eligible=enabled()&&usage==gui&&coords&&!weight&&!normal&&!color&&!index&&tex0==2&&tex1==0&&!dynamic&&!last&&count>0&&size<=MAX_BUFFER_BYTES;
        if(eligible) {
            long now=System.nanoTime();trim(now);
            for(int i=0;i<SLOTS;i++)if(vertices[i]!=null&&idle[i]&&sizes[i]==size) {
                Object vertex=vertices[i];
                if(reusable(vertex,count)) {
                    FloatBuffer buffer=(FloatBuffer)systemBuffer.invokeExact(vertex);
                    // A fresh allocation is zero-filled. Preserve even unused glyph-tail bytes.
                    buffer.clear();for(int j=0;j<buffer.capacity();j++)buffer.put(j,0f);
                    idle[i]=false;reused++;zeroedBytes+=size;return vertex;
                }
                remove(i,true);
            }
        }
        Object vertex=(Object)factory.invokeExact(usage,count,coords,weight,normal,color,index,tex0,tex1,dynamic,last);
        created++;
        if(eligible&&entries<SLOTS&&bytes+size<=MAX_BYTES) {
            for(int i=0;i<SLOTS;i++)if(vertices[i]==null){vertices[i]=vertex;sizes[i]=(int)size;idle[i]=false;entries++;bytes+=(int)size;break;}
        } else bypassed++;
        return vertex;
    }
    private static boolean reusable(Object vertex,int count) throws Throwable {
        FloatBuffer buffer=(FloatBuffer)systemBuffer.invokeExact(vertex);
        return (int)refCount.invokeExact(vertex)==1 && (int)numVertex.invokeExact(vertex)==count &&
            ((AtomicInteger)locked.invokeExact(vertex)).get()==0 && !(boolean)bound.invokeExact(vertex) &&
            (boolean)allowGpu.invokeExact(vertex)==(boolean)useGpu.invokeExact() &&
            buffer!=null&&buffer.isDirect()&&!buffer.isReadOnly()&&buffer.capacity()==count*5;
    }
    public static synchronized void release(Object vertex) throws Throwable {
        initialize();
        for(int i=0;i<SLOTS;i++)if(vertices[i]==vertex) {
            if(!idle[i]&&enabled()&&reusable(vertex,sizes[i]/20)) {
                idle[i]=true;releasedAt[i]=System.nanoTime();releases++;return;
            }
            // Reference counts, modified/locked buffers, or repeated releases follow the engine.
            remove(i,false);break;
        }
        delete.invokeExact(vertex);
    }
    private static void remove(int i,boolean release) throws Throwable {
        Object vertex=vertices[i];bytes-=sizes[i];entries--;vertices[i]=null;sizes[i]=0;idle[i]=false;releasedAt[i]=0;evicted++;
        if(release && (int)refCount.invokeExact(vertex)>0)delete.invokeExact(vertex);
    }
    private static void trim(long now) throws Throwable {
        for(int i=0;i<SLOTS;i++)if(vertices[i]!=null&&idle[i]&&now-releasedAt[i]>=IDLE_NANOS)remove(i,true);
    }
    public static synchronized String sample() {
        int available=0;for(int i=0;i<SLOTS;i++)if(vertices[i]!=null&&idle[i])available++;
        int engine=-1;
        try{if(engineAllocated!=null)engine=(int)engineAllocated.invokeExact();}catch(Throwable unavailable){/* diagnostic only */}
        return "[client-text-buffers] "+Instant.now()+" pid="+ProcessHandle.current().pid()+
            " enabled="+enabled()+" initialized="+(factory!=null)+" calls="+calls+" created="+created+" reused="+reused+
            " releases="+releases+" bypassed="+bypassed+" evicted="+evicted+" entries="+entries+" idle="+available+
            " active="+(entries-available)+" capacityBytes="+bytes+" maxCapacityBytes="+MAX_BYTES+" maxEntries="+SLOTS+
            " zeroedBytes="+zeroedBytes+" engineBufferAccountingBytes="+engine+
            "; cumulative text-factory counters; capacity covers active+idle system storage with at most equal GPU payload; engine accounting is not retained memory";
    }
}
