"""Bounded asynchronous frame ownership, FIFO delivery, backpressure and lifecycle."""
from pathlib import Path
import subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
FIXTURE=r'''
package wurm.graphics;
import java.nio.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class AsyncPublisherCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void fill(ByteBuffer b,int value){for(int i=0;i<b.capacity();i++)b.put(i,(byte)value);}
 public static void main(String[] args)throws Exception {
  Path dir=Path.of(args[0]);Thread owner=Thread.currentThread();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  List<Integer> order=new ArrayList<>();AtomicBoolean closed=new AtomicBoolean();
  var pub=new FramePublisher(16,16,new FramePublisher.Sink(){
   public void write(int w,int h,int seq,ByteBuffer bytes,int x,int y,boolean v,int a)throws Exception {
    check(Thread.currentThread()!=owner&&w==16&&h==16&&x==seq&&y==seq+1&&v&&a==seq*10);
    if(seq==1){entered.countDown();check(release.await(5,TimeUnit.SECONDS));}
    for(int i=0;i<1024;i++)check(bytes.get(i)==(byte)seq);
    order.add(seq);
   }
   public void close(){check(Thread.currentThread()!=owner);closed.set(true);}
  });
  ByteBuffer first=pub.acquire();fill(first,1);pub.submit(1,1,2,true,10);check(entered.await(5,TimeUnit.SECONDS));
  ByteBuffer second=pub.acquire();check(second!=first);fill(second,2);pub.submit(2,2,3,true,20);check(pub.stats().pending()==2);
  AtomicReference<Throwable> error=new AtomicReference<>();
  Thread unblock=new Thread(()->{try{
   long deadline=System.nanoTime()+5_000_000_000L;
   while(owner.getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<deadline)Thread.yield();
   check(owner.getState()==Thread.State.TIMED_WAITING);check(first.get(0)==1&&second.get(0)==2);
  }catch(Throwable e){error.set(e);}finally{release.countDown();}});unblock.start();
  ByteBuffer third=pub.acquire();check(third==first||third==second);fill(third,3);pub.submit(3,3,4,true,30);
  pub.close();unblock.join();check(error.get()==null&&closed.get()&&order.equals(List.of(1,2,3))&&pub.stats().frames()==3&&pub.stats().pending()==0);
  var slots=FramePublisher.class.getDeclaredField("slots");slots.setAccessible(true);for(var slot:(FramePublisher.Slot[])slots.get(pub))check(slot.pixels==null);
  try{pub.acquire();throw new AssertionError();}catch(java.io.IOException expected){}
  // Actual atomic V3 files and old-reader lifetime, with fast producer backpressure.
  Path frame=dir.resolve("frame"),baseline=dir.resolve("baseline");var real=new FramePublisher(frame,16,16);
  for(int i=1;i<=40;i++){fill(real.acquire(),i);real.submit(i,4,7,true,i);}
  real.close();ByteBuffer bytes=ByteBuffer.allocateDirect(1024);fill(bytes,40);FrameFile.writeRgba(baseline,16,16,40,bytes,4,7,true,40);
  check(Arrays.equals(Files.readAllBytes(frame),Files.readAllBytes(baseline))&&!Files.exists(dir.resolve("frame.pending")));
  try(var reader=Files.newInputStream(frame)) {
   byte[] old=Files.readAllBytes(frame);var replacement=new FramePublisher(frame,32,16);fill(replacement.acquire(),51);replacement.submit(51,31,15,false,1);
   Thread.currentThread().interrupt();replacement.close();check(Thread.interrupted());
   check(Arrays.equals(old,reader.readAllBytes())&&Files.size(frame)==36+32*16*4);
  }
  // Publication failure is observable, drains no bad frame and stops the worker.
  Path bad=dir.resolve("directory");Files.createDirectory(bad);var failed=new FramePublisher(bad,16,16);fill(failed.acquire(),1);failed.submit(1,1,2,false,0);
  try{failed.close();throw new AssertionError();}catch(java.io.IOException expected){check(expected.getMessage().contains("FRAME_PUBLICATION_FAILED"));}
  check(Files.isDirectory(bad)&&!Files.exists(dir.resolve("directory.pending")));
  var own=new FramePublisher(dir.resolve("own"),16,16);
  Thread wrong=new Thread(()->{try{own.acquire();error.set(new AssertionError("wrong owner accepted"));}catch(IllegalStateException expected){}catch(Exception e){error.set(e);}});wrong.start();wrong.join();check(error.get()==null);own.close();
  System.out.println("ASYNC_FRAME_PASS buffers=2 fifo=true backpressure=true exactPixels=true atomicReaders=true closeDrained=true errorsPropagated=true");
 }
}
'''
class AsyncFrameTest(unittest.TestCase):
 def test_owned_buffers_fifo_backpressure_exact_publication_errors_resize_and_close(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td);src=p/'AsyncPublisherCheck.java';src.write_text(FIXTURE)
   subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',td,str(src),str(ROOT/'graphics-compat/window/wurm/graphics/FramePublisher.java'),str(ROOT/'graphics-compat/probe/wurm/graphics/FrameFile.java')],check=True)
   r=subprocess.run(['java','-cp',td,'wurm.graphics.AsyncPublisherCheck',td],capture_output=True,text=True,timeout=30)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr);print(r.stdout.strip())
