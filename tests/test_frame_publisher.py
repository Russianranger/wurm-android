"""Reusable frame producer: bytes, atomic open-reader lifetime, failure and allocation regression."""
from pathlib import Path
import subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
FIXTURE=r'''
import java.nio.*;import java.nio.file.*;import wurm.graphics.FrameFile;
public class PublisherCheck {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]),a=dir.resolve("baseline"),b=dir.resolve("reused");
  ByteBuffer data=ByteBuffer.allocateDirect(16*16*4+8);data.position(8);
  for(int i=0;i<1024;i++)data.put((byte)(i*17));data.flip().position(8);
  FrameFile.RawWriter writer=new FrameFile.RawWriter(b);
  for(int frame=1;frame<5;frame++){
   FrameFile.writeRgba(a,16,16,frame,data,4,7,true,frame);writer.write(16,16,frame,data,4,7,true,frame);
   check(java.util.Arrays.equals(Files.readAllBytes(a),Files.readAllBytes(b)));check(data.position()==8&&data.limit()==1032);
  }
  byte[] old=Files.readAllBytes(b);
  try(var reader=Files.newInputStream(b)){
   writer.write(16,16,9,data,4,7,true,9);check(java.util.Arrays.equals(old,reader.readAllBytes()));
  }
  byte[] current=Files.readAllBytes(b);
  try{writer.write(16,16,10,data,16,7,true,10);throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(java.util.Arrays.equals(current,Files.readAllBytes(b)));
  // Failed publication cannot replace the current target or leave a partial pending frame.
  Files.createDirectory(dir.resolve("other"));
  var failed=new FrameFile.RawWriter(dir.resolve("other"));
  try{failed.write(16,16,1,data,0,0,false,0);throw new AssertionError();}catch(java.io.IOException expected){}
  check(Files.isDirectory(dir.resolve("other"))&&!Files.exists(dir.resolve("other.pending")));failed.close();
  var other=new Thread(()->{try{writer.write(16,16,1,data,0,0,false,0);throw new AssertionError();}catch(IllegalStateException expected){}catch(Exception e){throw new AssertionError(e);}});
  final Throwable[] failure={null};other.setUncaughtExceptionHandler((t,e)->failure[0]=e);other.start();other.join();check(failure[0]==null);
  // Resize replaces the cached view and honors nonzero position/shortened limits.
  ByteBuffer resized=ByteBuffer.allocateDirect(32*16*4+32);resized.position(16).limit(16+32*16*4);
  writer.write(32,16,10,resized,31,15,false,10);FrameFile.writeRgba(a,32,16,10,resized,31,15,false,10);
  check(java.util.Arrays.equals(Files.readAllBytes(a),Files.readAllBytes(b)));
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
  for(int i=1;i<=300;i++){FrameFile.writeRgba(a,16,16,i,data,0,0,false,0);writer.write(16,16,i,data,0,0,false,0);}
  long before=bean.getCurrentThreadAllocatedBytes();
  for(int i=1;i<=2000;i++)FrameFile.writeRgba(a,16,16,i,data,0,0,false,0);
  long baseline=bean.getCurrentThreadAllocatedBytes()-before;before=bean.getCurrentThreadAllocatedBytes();
  for(int i=1;i<=2000;i++)writer.write(16,16,i,data,0,0,false,0);
  long reused=bean.getCurrentThreadAllocatedBytes()-before;check(reused<baseline*0.85);
  writer.close();try{writer.write(16,16,1,data,0,0,false,0);throw new AssertionError();}catch(IllegalStateException expected){}
  System.out.println("FRAME_PUBLISHER_PASS frames=2000 baselineBytes="+baseline+" reusedBytes="+reused+" atomicReaders=true exactPixels=true");
 }
}
'''
class FramePublisherTest(unittest.TestCase):
 def test_reuse_preserves_atomic_frames_failure_cleanup_resize_ownership_and_reduces_heap(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td);(p/'PublisherCheck.java').write_text(FIXTURE)
   subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',td,str(p/'PublisherCheck.java'),str(ROOT/'graphics-compat/probe/wurm/graphics/FrameFile.java')],check=True)
   r=subprocess.run(['java','-cp',td,'PublisherCheck',td],capture_output=True,text=True,timeout=30)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr);print(r.stdout.strip())
