package wurm.graphics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Two owned pixel buffers, FIFO publication and backpressure; no frame dropping or GPU calls. */
public final class FramePublisher implements AutoCloseable {
    private static final long TIMEOUT_NANOS=10_000_000_000L;
    private final Thread owner=Thread.currentThread(),worker;
    private final int width,height;
    private final Slot[] slots;
    private final int[] queue=new int[2];
    private int head,count;
    private Slot acquired;
    private boolean closing,finished;
    private Throwable failure;
    private long written,nanos;
    private final Sink sink;
    static final class Slot {
        ByteBuffer pixels;
        int state,sequence,x,y,applied;
        boolean visible;
        Slot(int bytes){pixels=ByteBuffer.allocateDirect(bytes);}
    }
    // Package-private injection tests ownership, blocked writes and failure propagation.
    interface Sink extends AutoCloseable {
        void write(int width,int height,int sequence,ByteBuffer pixels,int x,int y,boolean visible,int applied)throws Exception;
        default void close()throws Exception { }
    }
    public record Stats(long frames,long nanos,int pending) { }
    public FramePublisher(Path target,int width,int height) {
        this(width,height,new Sink(){
            private FrameFile.RawWriter writer;
            public void write(int w,int h,int s,ByteBuffer p,int x,int y,boolean v,int a)throws IOException {
                if(writer==null)writer=new FrameFile.RawWriter(target);
                writer.write(w,h,s,p,x,y,v,a);
            }
            public void close(){if(writer!=null){writer.close();writer=null;}}
        });
    }
    FramePublisher(int width,int height,Sink sink) {
        if(width<16||height<16||width>1280||height>1024)throw new IllegalArgumentException("Invalid frame dimensions");
        this.width=width;this.height=height;this.sink=sink;
        slots=new Slot[]{new Slot(width*height*4),new Slot(width*height*4)};
        worker=new Thread(this::run,"wurm-frame-publisher");worker.setDaemon(true);worker.start();
    }
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Frame publisher not owned");}
    private void healthy()throws IOException {
        if(failure!=null)throw new IOException("FRAME_PUBLICATION_FAILED",failure);
        if(closing||finished)throw new IOException("Frame publisher is closed");
    }
    private void await(long deadline)throws IOException {
        long remaining=deadline-System.nanoTime();
        if(remaining<=0)throw new IOException("FRAME_PUBLICATION_TIMEOUT");
        try{wait(Math.max(1,Math.min(1000,remaining/1_000_000)));}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Frame publication interrupted",e);}
    }
    public synchronized ByteBuffer acquire()throws IOException {
        owned();long deadline=System.nanoTime()+TIMEOUT_NANOS;
        for(;;) {
            healthy();if(acquired!=null)return acquired.pixels;
            for(Slot slot:slots)if(slot.state==0){slot.state=1;acquired=slot;return slot.pixels.clear();}
            await(deadline);
        }
    }
    public synchronized void submit(int sequence,int x,int y,boolean visible,int applied)throws IOException {
        owned();healthy();
        if(acquired==null)throw new IllegalStateException("No captured frame");
        if(sequence<1||x<0||x>=width||y<0||y>=height||applied<0)throw new IllegalArgumentException("Invalid frame metadata");
        Slot slot=acquired;slot.sequence=sequence;slot.x=x;slot.y=y;slot.visible=visible;slot.applied=applied;
        slot.pixels.clear();slot.state=2;queue[(head+count)%2]=slot==slots[0]?0:1;count++;acquired=null;notifyAll();
    }
    public synchronized Stats stats() {owned();return new Stats(written,nanos,count+(slots[0].state==3||slots[1].state==3?1:0));}
    private void run() {
        try {
            for(;;) {
                Slot slot;
                synchronized(this) {
                    while(count==0&&!closing)wait();
                    if(count==0)break;
                    slot=slots[queue[head]];head=(head+1)%2;count--;slot.state=3;
                }
                long start=System.nanoTime();
                sink.write(width,height,slot.sequence,slot.pixels,slot.x,slot.y,slot.visible,slot.applied);
                synchronized(this){written++;nanos+=System.nanoTime()-start;slot.state=0;notifyAll();}
            }
        } catch(Throwable e) {synchronized(this){failure=e;closing=true;notifyAll();}}
        finally {
            try{sink.close();}catch(Throwable e){synchronized(this){if(failure==null)failure=e;}}
            synchronized(this){finished=true;count=0;acquired=null;for(Slot slot:slots){slot.pixels=null;slot.state=0;}notifyAll();}
        }
    }
    public void close()throws IOException {
        owned();boolean interrupted=false;
        synchronized(this){closing=true;notifyAll();}
        // Drain submitted frames before resize/close. Never reuse a buffer still being written.
        long deadline=System.nanoTime()+TIMEOUT_NANOS;
        synchronized(this){
            while(!finished) {
                long remaining=deadline-System.nanoTime();
                if(remaining<=0){if(interrupted)Thread.currentThread().interrupt();throw new IOException("FRAME_PUBLICATION_CLOSE_TIMEOUT");}
                try{wait(Math.max(1,Math.min(1000,remaining/1_000_000)));}catch(InterruptedException e){interrupted=true;}
            }
            if(interrupted)Thread.currentThread().interrupt();
            if(failure!=null)throw new IOException("FRAME_PUBLICATION_FAILED",failure);
        }
    }
}
