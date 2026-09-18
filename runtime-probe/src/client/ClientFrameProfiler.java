package client;

import java.lang.management.*;
import java.time.Instant;
import java.util.*;
import java.util.function.IntConsumer;

/** Opt-in five-minute frame attribution. No per-frame objects, file IO or stack walks. */
public final class ClientFrameProfiler implements IntConsumer {
    // WindowBackend's primitive-only observer protocol. END excludes periodic log formatting.
    static final int WORK=0,PACING=1,COLLECT=2,SETUP=3,READBACK=4,PUBLISH=5,RESTORE=6,SWAP=7,END=8;
    static final int CAPACITY=32,MAX_STALLS=64,MAX_STACKS=32,STACK_DEPTH=16;
    static final long WORK_LIMIT=50_000_000L,FRAME_LIMIT=75_000_000L;
    private final ClientJobProfiler.Capture capture;
    private final ThreadMXBean bean=ManagementFactory.getThreadMXBean();
    private final long anchor=System.nanoTime();
    private final Instant utc=Instant.now();
    private final long[] stages=new long[END];
    private final long[][] pending=new long[CAPACITY][END+5];
    private final boolean cpuAvailable;
    private volatile Thread owner;
    private volatile int phase=-1;
    private volatile long started,frame;
    private long changed,cpuStart,bytesStart,workCpu,workBytes;
    private boolean complete;
    private int size,recorded,omitted,stacks;
    private long lastStackFrame=-1,frames,totalWork,totalCpu,totalBytes,missingCpu,missingBytes;
    private boolean attached;
    ClientFrameProfiler(ClientJobProfiler.Capture capture) {
        this.capture=capture;
        boolean supported=false;
        try{supported=bean.isCurrentThreadCpuTimeSupported();if(supported&&!bean.isThreadCpuTimeEnabled())bean.setThreadCpuTimeEnabled(true);}
        catch(RuntimeException|LinkageError failure){supported=false;}
        cpuAvailable=supported;
    }
    void attach() {
        try {
            Class.forName("wurm.graphics.WindowBackend").getMethod("observeFrames",IntConsumer.class).invoke(null,this);
            attached=true;
            ClientJobProfiler.log("FRAME_OBSERVER_READY workThresholdMs=50 frameThresholdMs=75 maxStalls="+MAX_STALLS+
                " maxStacks="+MAX_STACKS+" stackDepth="+STACK_DEPTH+" pollMs=20 cpu="+cpuAvailable+"; stack samples only during long client work");
        }catch(ReflectiveOperationException|LinkageError failure){ClientJobProfiler.log("FRAME_OBSERVER_UNAVAILABLE reason="+failure.getClass().getSimpleName());}
    }
    void detach() {
        if(attached)try{Class.forName("wurm.graphics.WindowBackend").getMethod("observeFrames",IntConsumer.class).invoke(null,new Object[]{null});}
        catch(ReflectiveOperationException|LinkageError ignored){/* capture deadline still prevents further work */}
        attached=false;owner=null;phase=-1;
    }
    private long cpu(){try{return cpuAvailable?bean.getCurrentThreadCpuTime():-1;}catch(RuntimeException|LinkageError unavailable){return -1;}}
    private static long delta(long before,long after){return before>=0&&after>=before?after-before:-1;}
    @Override public void accept(int next) {
        long now=System.nanoTime();
        if(!capture.accepts(now))return;
        try{mark(next,now);}catch(RuntimeException|LinkageError unavailable){complete=false;phase=-1;capture.cancel();}
    }
    private void mark(int next,long now) {
        if(next==WORK) {
            owner=Thread.currentThread();complete=true;Arrays.fill(stages,0);frame++;
            bytesStart=ClientJobProfiler.allocated();cpuStart=cpu();workCpu=workBytes=-1;
            changed=now;started=now;phase=WORK;return;
        }
        int previous=phase;
        if(!complete||owner!=Thread.currentThread()||next<1||next>END||previous<0||next<=previous){complete=false;phase=-1;return;}
        stages[previous]+=Math.max(0,now-changed);
        if(previous==WORK){workCpu=delta(cpuStart,cpu());workBytes=delta(bytesStart,ClientJobProfiler.allocated());}
        changed=now;phase=next;
        if(next==END){complete=false;finish(now);}
    }
    private synchronized void finish(long end) {
        frames++;totalWork+=stages[WORK];
        if(workCpu>=0)totalCpu+=workCpu;else missingCpu++;
        if(workBytes>=0)totalBytes+=workBytes;else missingBytes++;
        if(stages[WORK]<WORK_LIMIT&&end-started<FRAME_LIMIT)return;
        if(size==CAPACITY||recorded==MAX_STALLS){omitted++;return;}
        long[] row=pending[size++];recorded++;
        row[0]=frame;row[1]=started;row[2]=end;row[3]=workCpu;row[4]=workBytes;
        System.arraycopy(stages,0,row,5,END);
    }
    /** Runs only on the existing observation daemon, bounded to one attempt per long frame. */
    void watch() {
        if(!capture.accepts(System.nanoTime())||stacks>=MAX_STACKS||phase!=WORK)return;
        long f=frame,start=started;Thread target=owner;
        if(f==lastStackFrame||target==null||System.nanoTime()-start<WORK_LIMIT)return;
        lastStackFrame=f;stacks++;
        try {
            ThreadInfo info=bean.getThreadInfo(target.getId(),STACK_DEPTH);
            long sampled=System.nanoTime();
            if(info==null||phase!=WORK||frame!=f)return;
            StringBuilder out=new StringBuilder("STALL_STACK frame=").append(f).append(" time=").append(time(sampled))
                .append(" workElapsedMs=").append(ms(sampled-start)).append(" state=").append(info.getThreadState());
            for(StackTraceElement entry:info.getStackTrace())out.append(" at=").append(ClientJobProfiler.safe(entry.toString(),180));
            ClientJobProfiler.log(out+"; sampled during work; GC pauses may not be sampled; not allocation stacks");
        }catch(RuntimeException|LinkageError unavailable){/* frame/GC timelines remain useful without stacks */}
    }
    private Instant time(long nanos){return utc.plusNanos(nanos-anchor);}
    private static String ms(long nanos){return nanos<0?"unavailable":String.format(Locale.ROOT,"%.3f",nanos/1e6);}
    void emit(boolean last) {
        long[][] rows;long n,w,c,b,mc,mb;int dropped,kept,attempts;
        synchronized(this){
            rows=new long[size][];for(int i=0;i<size;i++)rows[i]=pending[i].clone();size=0;
            n=frames;w=totalWork;c=totalCpu;b=totalBytes;mc=missingCpu;mb=missingBytes;
            frames=totalWork=totalCpu=totalBytes=missingCpu=missingBytes=0;
            dropped=omitted;kept=recorded;attempts=stacks;
        }
        ClientJobProfiler.log("FRAME_WORK frames="+n+" workMs="+ms(w)+" cpuMs="+ms(c)+" allocatedBytes="+b+
            " missingCpu="+mc+" missingAllocation="+mb+" recordedStalls="+kept+" omittedStalls="+dropped+
            " stackAttempts="+attempts+" final="+last+"; per-window work totals; includes callbacks/polling, excludes swap and periodic reporting");
        for(long[] row:rows)ClientJobProfiler.log("FRAME_STALL frame="+row[0]+" start="+time(row[1])+" end="+time(row[2])+
            " frameMs="+ms(row[2]-row[1])+" workMs="+ms(row[5])+" workCpuMs="+ms(row[3])+" workAllocatedBytes="+row[4]+
            " pacingMs="+ms(row[6])+" collectMs="+ms(row[7])+" setupMs="+ms(row[8])+" readbackIssueMs="+ms(row[9])+
            " publishMs="+ms(row[10])+" restoreMs="+ms(row[11])+" swapMs="+ms(row[12])+
            "; elapsed includes GC/scheduling; collect includes buffer wait and prior-frame publication; correlate GC timestamps");
    }
}
