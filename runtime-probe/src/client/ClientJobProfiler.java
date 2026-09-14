package client;

import com.sun.management.ThreadMXBean;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Optional, bounded observation of completed jobs. Never retains job/argument objects. */
public final class ClientJobProfiler {
    static final int PAIRS=128, TOP=12, SECONDS=300;
    private static volatile Capture active;
    private static MethodHandle delegate;
    private static ThreadMXBean counters;
    private ClientJobProfiler() { }
    public static boolean enabled() { return Boolean.getBoolean("wurm.client.jobProfiling"); }
    public static void prepare() throws ReflectiveOperationException {
        if (!enabled()) return;
        // Resolve the public interface once, including when the implementation is not public.
        Class<?> job=Class.forName("com.wurmonline.client.job.Job");
        delegate=MethodHandles.publicLookup().findVirtual(job,"execute",MethodType.methodType(void.class,Object.class))
            .asType(MethodType.methodType(void.class,Object.class,Object.class));
        try {
            if (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                if(!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                counters=bean;
            }
        } catch (RuntimeException | LinkageError unavailable) { counters=null; }
        log("READY counters="+(counters!=null)+" durationSeconds="+SECONDS+"; recording starts only on request");
    }
    private static void log(String message) {
        System.out.println("[client-memory-test] "+Instant.now()+" pid="+ProcessHandle.current().pid()+" "+message);
    }
    public static synchronized void command(String command) {
        if(command.equals("stop")) { if(active!=null) active.cancel(); else log("IDLE"); return; }
        if(!command.equals("start")) throw new IllegalArgumentException("Unknown memory recording command");
        if(!enabled() || delegate==null || counters==null) { log("UNAVAILABLE enable-job-profiling-before-client-start=true"); return; }
        if(active!=null) { log("ALREADY_RECORDING"); return; }
        Capture capture=new Capture(System.nanoTime(),SECONDS*1_000_000_000L);
        Thread sampler=new Thread(()->record(capture),"wurm-job-observation");
        sampler.setDaemon(true); capture.sampler=sampler; active=capture;
        log("BEGIN durationSeconds="+SECONDS+" maxPairs="+PAIRS+" top="+TOP+
            "; completed-job heap allocation only; no forced GC, heap dump or stress allocation");
        try { sampler.start(); }
        catch (RuntimeException | LinkageError failure) { active=null; log("UNAVAILABLE reason="+failure.getClass().getSimpleName()); }
    }
    private static long allocated() {
        try { return counters==null?-1:counters.getCurrentThreadAllocatedBytes(); }
        catch (RuntimeException | LinkageError unavailable) { return -1; }
    }
    /** Invoked only from the opted-in, verified Executor.run call site. */
    public static void execute(Object job,Object argument) throws Throwable {
        Capture capture=active;
        if(capture==null) { delegate.invokeExact(job,argument); return; }
        long start=System.nanoTime();
        if(!capture.accepts(start)) { delegate.invokeExact(job,argument); return; }
        long before=allocated(); boolean succeeded=false;
        try { delegate.invokeExact(job,argument); succeeded=true; }
        finally {
            long after=allocated(), elapsed=System.nanoTime()-start;
            try {
                Thread thread=Thread.currentThread();
                capture.add(thread.getId(),thread.getName(),job.getClass().getName(),
                    before>=0 && after>=before?after-before:-1,elapsed,succeeded);
            } catch (RuntimeException | LinkageError unavailable) { capture.cancel(); }
        }
    }
    static void record(Capture capture) {
        String reason="duration";
        try {
            int samples=0;
            while(capture.accepts(System.nanoTime())) {
                // Proc/management observations are performed on this daemon, never on a game worker.
                log("SAMPLE recordingMs="+((System.nanoTime()-capture.started)/1_000_000));
                System.out.println(probe.RuntimeMeasurements.sample("client-recording",Path.of("/proc/self")));
                if(samples++%6==0) emit(capture,false);
                long remaining=capture.budget-(System.nanoTime()-capture.started);
                if(remaining>0) Thread.sleep(Math.min(5_000,Math.max(1,remaining/1_000_000)));
            }
            if(capture.cancelled) reason="cancelled";
        } catch (InterruptedException stop) { reason="cancelled"; Thread.currentThread().interrupt(); }
        catch (RuntimeException | LinkageError failure) { reason="unavailable-"+failure.getClass().getSimpleName(); }
        finally {
            capture.cancelled=true;
            synchronized(ClientJobProfiler.class) {
                try {
                    emit(capture,true);
                    log("END reason="+reason+" elapsedMs="+((System.nanoTime()-capture.started)/1_000_000)+
                        "; no leak verdict; compare natural post-GC heap and direct/PSS across repeated routes");
                } finally { capture.clear(); if(active==capture) active=null; }
            }
        }
    }
    private static void emit(Capture capture,boolean last) {
        Snapshot snapshot=capture.snapshot();
        log("JOBS windowMs="+snapshot.elapsedMs+" completed="+snapshot.completed+" observedAllocatedBytes="+snapshot.bytes+
            " unavailable="+snapshot.unavailable+" omitted="+snapshot.omitted+" pairs="+snapshot.rows.size()+" final="+last+
            "; excludes callbacks, waiting, incomplete jobs and non-job threads; elapsed includes GC/scheduling");
        int printed=0;
        for(Row row:snapshot.rows) {
            if(printed++==TOP) break;
            log("JOB threadId="+row.id+" thread="+row.thread+" class="+row.job+" calls="+row.calls+
                " allocatedBytes="+row.bytes+" elapsedMs="+(row.nanos/1_000_000)+" failed="+row.failed+" unavailable="+row.unavailable);
        }
    }
    static final class Row {
        final long id; final String thread,job;
        long calls,bytes,nanos,failed,unavailable;
        Row(long id,String thread,String job) {
            this.id=id; this.thread=safe(thread,48); this.job=safe(job,180);
        }
        Row copy() { Row r=new Row(id,thread,job); r.calls=calls; r.bytes=bytes; r.nanos=nanos; r.failed=failed; r.unavailable=unavailable; return r; }
    }
    record Snapshot(long elapsedMs,long completed,long bytes,long unavailable,long omitted,List<Row> rows) { }
    static final class Capture {
        final long started,budget;
        volatile boolean cancelled;
        Thread sampler;
        private final Row[] rows=new Row[PAIRS];
        private int size;
        private long completed,bytes,unavailable,omitted,previous;
        Capture(long started,long budget) { this.started=started; this.budget=budget; previous=started; }
        boolean accepts(long now) { return !cancelled && now-started<budget; }
        void cancel() { cancelled=true; if(sampler!=null) sampler.interrupt(); }
        synchronized void add(long id,String thread,String job,long allocated,long elapsed,boolean success) {
            if(!accepts(System.nanoTime())) return;
            Row row=null;
            for(int i=0;i<size;i++) if(rows[i].id==id && rows[i].job.equals(job)) { row=rows[i]; break; }
            completed++; if(allocated>=0) bytes+=allocated; else unavailable++;
            if(row==null) {
                if(size==PAIRS) { omitted++; return; }
                row=new Row(id,thread,job); rows[size++]=row;
            }
            row.calls++; if(allocated>=0) row.bytes+=allocated; else row.unavailable++;
            row.nanos+=elapsed; if(!success) row.failed++;
        }
        synchronized Snapshot snapshot() {
            var result=new ArrayList<Row>(size);
            for(int i=0;i<size;i++) { Row r=rows[i]; result.add(r.copy()); r.calls=r.bytes=r.nanos=r.failed=r.unavailable=0; }
            result.removeIf(r->r.calls==0);
            result.sort(Comparator.comparingLong((Row r)->r.bytes).reversed().thenComparingLong(r->r.id).thenComparing(r->r.job));
            long now=System.nanoTime(); Snapshot snapshot=new Snapshot((now-previous)/1_000_000,completed,bytes,unavailable,omitted,result);
            previous=now; completed=bytes=unavailable=omitted=0; return snapshot;
        }
        synchronized void clear() { Arrays.fill(rows,null); size=0; }
        synchronized int retainedPairs() { return size; }
    }
    static String safe(String value,int limit) {
        StringBuilder result=new StringBuilder();
        for(int i=0;i<Math.min(value.length(),limit);i++) {
            char c=value.charAt(i);
            result.append(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='_'||c=='.'||c=='$'||c=='-'?c:'_');
        }
        return result.toString();
    }
}
