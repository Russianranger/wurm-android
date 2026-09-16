package client;

import java.nio.*;
import java.nio.file.*;
import java.lang.invoke.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;

/** Original host work/driver boundaries; optionally runs the owner's exact private classes. */
public final class TextFixture {
    static Class<?> vertex,usage;
    static Object gui;
    static MethodHandle create,lock,unlock,release,reference;
    static boolean pooled;
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static Object make(int n) throws Throwable {return (Object)create.invokeExact(gui,n,true,false,false,false,false,2,0,false,false);}
    static FloatBuffer fill(Object v,float value) throws Throwable {
        FloatBuffer b=(FloatBuffer)lock.invokeExact(v);
        for(int i=0;i<b.capacity();i++)check(Float.floatToRawIntBits(b.get(i))==0);
        for(int i=0;i<b.capacity();i++)b.put(i,value+i);
        unlock.invokeExact(v);return b;
    }
    static long number(String key) {var m=java.util.regex.Pattern.compile("\\b"+key+"=(\\d+)").matcher(ClientTextBuffers.sample());check(m.find());return Long.parseLong(m.group(1));}
    static void setup(boolean pool) throws Throwable {
        pooled=pool;
        vertex=Class.forName(ClientTextPatch.VERTEX.replace('/','.'));usage=Class.forName(vertex.getName()+"$Usage");gui=usage.getField("GUI").get(null);
        var lookup=MethodHandles.publicLookup();
        var signature=MethodType.methodType(vertex,usage,int.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class,int.class,int.class,boolean.class,boolean.class);
        create=lookup.findStatic(vertex,pool?"androidTextCreate":"create",signature).asType(signature.changeParameterType(0,Object.class).changeReturnType(Object.class));
        lock=lookup.findVirtual(vertex,"lock",MethodType.methodType(FloatBuffer.class)).asType(MethodType.methodType(FloatBuffer.class,Object.class));
        unlock=lookup.findVirtual(vertex,"unlock",MethodType.methodType(void.class)).asType(MethodType.methodType(void.class,Object.class));
        release=lookup.findVirtual(vertex,pool?"androidTextDelete":"delete",MethodType.methodType(void.class)).asType(MethodType.methodType(void.class,Object.class));
        reference=lookup.findVirtual(vertex,"reference",MethodType.methodType(vertex)).asType(MethodType.methodType(Object.class,Object.class));
    }
    static void patch(String input,String output,boolean real) throws Exception {
        try(var src=new JarFile(input);var dst=new JarOutputStream(Files.newOutputStream(Path.of(output)))) {
            for(String name:ClientTextPatch.ORIGINALS.keySet()) {
                byte[] original=src.getInputStream(src.getJarEntry(name)).readAllBytes();
                byte[] changed=real?ClientTextPatch.prepare(name,original):ClientTextPatch.transform(name,original,false);
                check(Arrays.equals(original,ClientTextPatch.transform(name,changed,true)));
                if(real){ClientTextPatch.verify(name,changed);byte[] bad=changed.clone();bad[bad.length-1]^=1;try{ClientTextPatch.verify(name,bad);throw new AssertionError("tamper accepted");}catch(Exception expected){}}
                dst.putNextEntry(new JarEntry(name));dst.write(changed);dst.closeEntry();
            }
            if(real)for(String name:ClientBuffers.ORIGINALS.keySet()) {
                byte[] original=src.getInputStream(src.getJarEntry(name)).readAllBytes();
                dst.putNextEntry(new JarEntry(name));dst.write(ClientBuffers.prepare(name,original));dst.closeEntry();
            }
        }
        System.out.println("TEXT_PATCH_PASS reverse=byte-exact private="+real);
    }
    public static void main(String[] args) throws Throwable {
        if(args[0].equals("patch")){patch(args[1],args[2],Boolean.parseBoolean(args[3]));return;}
        if(args[0].equals("prepare-overlay")||args[0].equals("verify-overlay")) {
            System.setProperty("wurm.client.offscreenOverlay",args[1]);
            System.setProperty("wurm.client.reuseTextBuffers",args[2]);System.setProperty("wurm.client.jobProfiling",args[3]);
            if(args[0].equals("prepare-overlay"))ClientGraphicsPatch.prepare();else ClientGraphicsPatch.verifySelected();
            try(var jar=new JarFile(args[1])){check(jar.size()==10+(Boolean.parseBoolean(args[2])?3:0)+(Boolean.parseBoolean(args[3])?2:0));}
            System.out.println("TEXT_FULL_OVERLAY_PASS");return;
        }
        setup(!args[0].endsWith("baseline"));
        switch(args[0]) {
            case "baseline", "benchmark", "reject-baseline", "reject-benchmark" -> {
                boolean rejecting=args[0].startsWith("reject-");
                Object[] held=new Object[64];
                var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
                long allocated=0,elapsed=0;
                for(int frame=0;frame<350;frame++) {
                    if(frame==100){allocated=bean.getCurrentThreadAllocatedBytes();elapsed=System.nanoTime();}
                    for(int i=0;i<held.length;i++){Object v=make(60+6*(i%12));held[i]=v;FloatBuffer b=(FloatBuffer)lock.invokeExact(v);for(int j=0;j<b.capacity();j++)b.put(j,(float)(j+frame));unlock.invokeExact(v);if(rejecting){Object ref=(Object)reference.invokeExact(v);check(ref==v);}}
                    for(Object v:held){release.invokeExact(v);if(rejecting)release.invokeExact(v);}
                }
                allocated=bean.getCurrentThreadAllocatedBytes()-allocated;elapsed=System.nanoTime()-elapsed;
                if(pooled&&rejecting){check(number("created")==22400);check(number("reused")==0);check(number("rejectRefs")==22400);check(number("entries")==0);}
                if(pooled&&!rejecting){check(number("created")==64);check(number("active")==0);check(number("reused")==64*349);}
                System.out.println("TEXT_BENCH pooled="+pooled+" rejecting="+rejecting+" draws=16000 heapBytes="+allocated+" nanos="+elapsed);
            }
            case "lifetime" -> {
                Object a=make(60),b=make(60);check(a!=b);FloatBuffer first=fill(a,1);fill(b,2);
                release.invokeExact(a);Object reused=make(60);check(reused==a);fill(reused,3);check(first.get(0)==3);
                release.invokeExact(b);release.invokeExact(reused);
                Object shared=make(60);fill(shared,4);Object ref=(Object)reference.invokeExact(shared);check(ref==shared);
                release.invokeExact(shared);Object next=make(60);check(next!=shared);check(((FloatBuffer)vertex.getMethod("getSystemBuffer").invoke(shared)).get(0)==4);
                release.invokeExact(shared);fill(next,5);release.invokeExact(next);
                release.invokeExact(next); // engine semantics for a duplicate release; never reuse freed storage
                Object fresh=make(60);check(fresh!=next);fill(fresh,6);release.invokeExact(fresh);
                var times=ClientTextBuffers.class.getDeclaredField("releasedAt");times.setAccessible(true);
                for(int i=0;i<ClientTextBuffers.SLOTS;i++)((long[])times.get(null))[i]=System.nanoTime()-ClientTextBuffers.IDLE_NANOS-1;
                fresh=make(60);fill(fresh,7);release.invokeExact(fresh);check(number("evicted")>0);
                check(number("active")==0);System.out.println("TEXT_LIFETIME_PASS sharedRefs=true zeroFilled=true expiry=true");
            }
            case "guards" -> {
                // Corrupted/ineligible entries must reach original deletion, never another borrower.
                Object v=make(60);fill(v,1);Object ref=(Object)reference.invokeExact(v);check(ref==v);release.invokeExact(v);release.invokeExact(v);
                var locked=vertex.getDeclaredField("isLocked");locked.setAccessible(true);
                v=make(60);fill(v,2);((java.util.concurrent.atomic.AtomicInteger)locked.get(v)).set(2);release.invokeExact(v);
                var count=vertex.getDeclaredField("numVertex");count.setAccessible(true);
                v=make(60);fill(v,3);count.setInt(v,61);release.invokeExact(v);
                var gpu=Class.forName("com.wurmonline.client.options.GLOption").getField("gpu");
                v=make(60);fill(v,4);gpu.setBoolean(null,true);release.invokeExact(v);gpu.setBoolean(null,false);
                v=make(60);release.invokeExact(v); // No lock/allocation: missing storage.
                v=make(60);fill(v,5);release.invokeExact(v);release.invokeExact(v);
                v=make(60);fill(v,6);System.setProperty("wurm.client.reuseTextBuffers","false");release.invokeExact(v);System.setProperty("wurm.client.reuseTextBuffers","true");
                for(String key:new String[]{"rejectRefs","rejectLocked","rejectSize","rejectGpuMode","rejectStorage","rejectDuplicate","rejectDisabled"})check(number(key)==1);
                check(number("releaseRejected")==7);check(number("borrowRejected")==0);check(number("entries")==0);
                System.out.println("TEXT_GUARDS_PASS allReasons=true originalDelete=true");
            }
            case "limits" -> {
                Object[] held=new Object[300];for(int i=0;i<held.length;i++){held[i]=make(60);fill(held[i],i);}
                check(number("entries")==ClientTextBuffers.SLOTS);check(number("bypassed")==44);
                for(Object v:held)release.invokeExact(v);
                Object huge=make(10000);fill(huge,5);release.invokeExact(huge);check(number("entries")==ClientTextBuffers.SLOTS);
                check(number("capacityBytes")<=ClientTextBuffers.MAX_BYTES);System.out.println("TEXT_LIMIT_PASS slots=true oversize=true");
            }
            case "bytes" -> {
                Object[] held=new Object[80];for(int i=0;i<held.length;i++){held[i]=make(3276);fill(held[i],i);}
                check(number("entries")==64 && number("capacityBytes")==64*65520);for(Object v:held)release.invokeExact(v);
                check(number("active")==0);System.out.println("TEXT_BYTE_LIMIT_PASS activePlusIdle=true");
            }
            case "threads" -> {
                Map<Object,Boolean> live=Collections.synchronizedMap(new IdentityHashMap<>());
                var failures=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();Thread[] threads=new Thread[4];
                for(int t=0;t<threads.length;t++) {threads[t]=new Thread(()->{try{for(int i=0;i<100;i++){Object v=make(60);check(live.put(v,true)==null);fill(v,i);check(live.remove(v)!=null);release.invokeExact(v);}}catch(Throwable e){failures.add(e);}});threads[t].start();}
                for(Thread t:threads)t.join();if(!failures.isEmpty())throw failures.peek();check(live.isEmpty());check(number("active")==0);System.out.println("TEXT_THREADS_PASS exclusiveLeases=true");
            }
            case "queue" -> {
                Class<?> q=Class.forName("com.wurmonline.client.renderer.backend.Queue"),p=Class.forName("com.wurmonline.client.renderer.backend.Primitive"),matrix=Class.forName("com.wurmonline.client.renderer.Matrix");
                Object queue=q.getConstructor(int.class,boolean.class).newInstance(128,false),primitive=q.getMethod("reservePrimitive").invoke(queue);
                Object v=make(60);fill(v,1);p.getField("vertex").set(primitive,v);p.getField("destroyBuffers").setBoolean(primitive,true);
                p.getField("num").setInt(primitive,1);q.getMethod("queue",p,matrix).invoke(queue,primitive,null);
                check(number("active")==1);q.getMethod("clear").invoke(queue);check(number("active")==0);check(p.getField("vertex").get(primitive)==null);
                Object next=make(60);check(next==v);fill(next,2);release.invokeExact(next);System.out.println("TEXT_QUEUE_PASS originalClear=true");
            }
            case "gpu" -> {
                Class<?> backend=Class.forName("com.wurmonline.client.renderer.backend.Backend"),option=Class.forName("com.wurmonline.client.options.GLOption"),gl15=Class.forName("org.lwjgl.opengl.GL15"),gl30=Class.forName("org.lwjgl.opengl.GL30");
                var gl=backend.getField("gl");var gpu=option.getField("gpu");gpu.setBoolean(null,true);gl.setBoolean(null,true);
                var id=vertex.getDeclaredField("bufferObject");id.setAccessible(true);
                var vao=vertex.getDeclaredField("arrayObject");vao.setAccessible(true);
                Object v=make(60);fill(v,1);int buffer=id.getInt(v);check(buffer!=0);check((float)gl15.getMethod("first",int.class).invoke(null,buffer)==1);
                check((boolean)vertex.getMethod("bind",boolean.class).invoke(v,true));int array=vao.getInt(v);check(array!=0);
                gl30.getMethod("glBindVertexArray",int.class).invoke(null,0);release.invokeExact(v);
                check((int)gl15.getField("deleted").get(null)==0);check((int)gl30.getField("deleted").get(null)==0);
                // Fill on a worker, then upload on the render thread through the original bind path.
                gl.setBoolean(null,false);Object next=make(60);check(next==v);fill(next,23);
                check((float)gl15.getMethod("first",int.class).invoke(null,buffer)==1);
                gl.setBoolean(null,true);vertex.getMethod("bind",boolean.class).invoke(next,true);
                check((float)gl15.getMethod("first",int.class).invoke(null,buffer)==23);check(id.getInt(next)==buffer&&vao.getInt(next)==array);
                gl30.getMethod("glBindVertexArray",int.class).invoke(null,0);release.invokeExact(next);
                // Expiry on a worker must defer original native deletion until the GL thread drains it.
                gl.setBoolean(null,false);var times=ClientTextBuffers.class.getDeclaredField("releasedAt");times.setAccessible(true);
                for(int i=0;i<ClientTextBuffers.SLOTS;i++)((long[])times.get(null))[i]=System.nanoTime()-ClientTextBuffers.IDLE_NANOS-1;
                next=make(60);check(next!=v);fill(next,31);check((int)gl15.getField("deleted").get(null)==0);
                gl.setBoolean(null,true);vertex.getMethod("deleteDeferred").invoke(null);
                check((int)gl15.getField("deleted").get(null)==1);check((int)gl30.getField("deleted").get(null)==1);
                vertex.getMethod("bind",boolean.class).invoke(next,true);gl30.getMethod("glBindVertexArray",int.class).invoke(null,0);release.invokeExact(next);
                // A graphics-mode change cannot borrow an incompatible GPU buffer.
                gpu.setBoolean(null,false);Object cpu=make(60);check(cpu!=next);fill(cpu,45);release.invokeExact(cpu);
                check((int)gl15.getMethod("count").invoke(null)==0);check(number("active")==0);
                System.out.println("TEXT_GPU_PASS currentData=true vaoReused=true deferredDelete=true modeChange=true");
            }
            case "font", "font-baseline", "font-legacy", "font-legacy-baseline" -> {
                // Exact SimpleTextFont/Queue/Primitive/Matrix/VertexBuffer code; authored glyph and GL boundaries.
                boolean compare=args[0].endsWith("baseline"),legacy=args[0].contains("legacy");System.setProperty("wurm.client.reuseTextBuffers",String.valueOf(!compare));
                Class<?> font=Class.forName("com.wurmonline.client.renderer.gui.text.SimpleTextFont"),q=Class.forName("com.wurmonline.client.renderer.backend.Queue"),p=Class.forName("com.wurmonline.client.renderer.backend.Primitive");
                var ctor=font.getDeclaredConstructor(java.awt.Font.class,boolean.class);ctor.setAccessible(true);Object text=ctor.newInstance(new java.awt.Font("Dialog",0,12),false),queue=q.getConstructor(int.class,boolean.class).newInstance(128,false);
                if(legacy){Class.forName("com.wurmonline.client.options.GLOption").getField("gpu").setBoolean(null,true);}
                var draw=font.getMethod("drawString",q,String.class,int.class,int.class,float.class,float.class,float.class,float.class);
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                String[] words={"chat","short","longer label","","\u03a9","mixed\u03a9","12345","54321"};
                for(int frame=0;frame<20;frame++) {
                    for(int i=0;i<words.length;i++)digest.update(ByteBuffer.allocate(4).putInt((Integer)draw.invoke(text,queue,words[(i+frame)%words.length],i*10,frame,0.25f,0.5f,0.75f,1f)).array());
                    Object[] primitives=(Object[])q.getMethod("getQueue").invoke(queue);int n=(Integer)q.getMethod("getQueueCount").invoke(queue);
                    for(int i=0;i<n;i++) {
                        Object primitive=primitives[i],v=p.getField("vertex").get(primitive);FloatBuffer b=(FloatBuffer)vertex.getMethod("getSystemBuffer").invoke(v);
                        for(int j=0;j<b.capacity();j++)digest.update(ByteBuffer.allocate(4).putFloat(b.get(j)).array());
                        digest.update(ByteBuffer.allocate(4).putInt(p.getField("num").getInt(primitive)).array());
                    }
                    if(legacy) {
                        Class.forName("com.wurmonline.client.renderer.backend.Backend").getField("gl").setBoolean(null,true);
                        q.getMethod("sort").invoke(queue);q.getMethod("render").invoke(queue);
                        if(frame==0){var bnd=vertex.getDeclaredField("boundBufferObject");bnd.setAccessible(true);check(bnd.getBoolean(p.getField("vertex").get(primitives[0])));System.out.println("LEGACY_DRAW_BOUND_LAYOUT_CONFIRMED");for(int j=0;j<n;j++)check(bnd.getBoolean(p.getField("vertex").get(primitives[j])));}
                        Class.forName("com.wurmonline.client.renderer.backend.Backend").getField("gl").setBoolean(null,false);
                    }
                    q.getMethod("clear").invoke(queue);
                    if(legacy){Class.forName("com.wurmonline.client.renderer.backend.Backend").getField("gl").setBoolean(null,true);vertex.getMethod("deleteDeferred").invoke(null);Class.forName("com.wurmonline.client.renderer.backend.Backend").getField("gl").setBoolean(null,false);}
                }
                System.out.println(ClientTextBuffers.sample());
                check(number("active")==0);if(!compare){check(number("reused")>0);if(legacy){check(number("evicted")==0);check(number("vboLayoutReturns")==120);check(number("reused")==133);check(number("releaseRejected")==0);}}
                System.out.println("TEXT_FONT_PASS geometry="+HexFormat.of().formatHex(digest.digest())+" baseline="+compare+" legacy="+legacy+" draws="+Class.forName("org.lwjgl.opengl.GL11").getField("draws").getInt(null)+" drawHash="+Class.forName("org.lwjgl.opengl.GL11").getField("drawHash").getLong(null));
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.out.println(ClientTextBuffers.sample());
    }
}
