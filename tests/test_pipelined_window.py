"""Window lifecycle around a delayed GPU boundary, including exact input metadata."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_window_frame_stages import SOURCES,ROOT

NATIVE='''package wurm.graphics;import java.nio.*;
class NativeEgl {
 static int width,height,issued,closed,swaps,error;static byte[] pending;static boolean failIssue,failCollect;
 static void open(String b,int w,int h){width=w;height=h;}static void resize(int w,int h){if(pending!=null)throw new AssertionError("resize before drain");width=w;height=h;}
 static boolean readbackOpen(){return true;}static void readbackClose(){if(pending!=null)throw new AssertionError("close before drain");closed++;}
 static void readbackIssue(){if(failIssue)throw new IllegalStateException("issue failure");if(pending!=null)throw new AssertionError("overwrite");issued++;pending=new byte[width*height*4];for(int i=0;i<pending.length;i++)pending[i]=(byte)(i*13+issued);}
 static void readbackCollect(ByteBuffer b){if(failCollect)throw new IllegalStateException("collect failure");if(pending==null)throw new AssertionError("empty");for(int i=0;i<pending.length;i++)b.put(i,pending[i]);pending=null;}
 static int error(){return 0;}static void swap(){}static void close(){pending=null;closed++;}
}'''
POINTER='''package wurm.graphics;class WindowInput{
 static int px=3,py=4,input=17;static boolean visible=true;
 WindowInput(int w,int h){}void resize(int w,int h){}double x(){return px;}double y(){return py;}void cursor(double x,double y){}
 int displayX(){return px;}int displayY(){return py;}boolean visible(){return visible;}int applied(){return input;}void release(){}boolean canApply(String s){return true;}void apply(String s){}
}'''
CHECK='''package wurm.graphics;import java.nio.*;import java.nio.file.*;import java.util.*;
public class PipelineCheck{
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void delivered(Path p,int seq,int w,int x,int y,int v,int a)throws Exception{
  WindowCheck.delivered(p,seq);byte[] saved=Files.readAllBytes(p);var h=ByteBuffer.wrap(saved);
  check(h.getInt(8)==w&&h.getInt(12)==16&&h.getInt(20)==x&&h.getInt(24)==y&&h.getInt(28)==v&&h.getInt(32)==a);
  check(saved.length==36+w*16*4);for(int i=36;i<saved.length;i++)check(saved[i]==(byte)((i-36)*13+seq));
 }
 public static void main(String[] args)throws Exception{
  Path p=Path.of(args[0]);System.setProperty("wurm.graphics.frame",p.toString());System.setProperty("wurm.graphics.fps","60");
  WindowBackend.open(16,16);
  List<Integer> phases=new ArrayList<>();WindowBackend.observeFrames(phases::add);
  if(args.length>1){
   if(args[1].equals("issue"))NativeEgl.failIssue=true;
   else {WindowBackend.swap();NativeEgl.failCollect=true;}
   try{WindowBackend.swap();throw new AssertionError();}catch(IllegalStateException e){check(e.getMessage().equals(args[1]+" failure"));}
   WindowCheck.packed();check(!Files.exists(p));
   try{WindowBackend.close();}catch(IllegalStateException e){check(args[1].equals("collect"));}
   check(NativeEgl.closed==1);System.out.println("PIPELINE_FAILURE_PASS");return;
  }
  WindowBackend.swap();WindowCheck.packed();check(!Files.exists(p));
  check(phases.equals(Arrays.asList(1,2,3,4,5,6,7,8,0)));phases.clear();
  WindowInput.px=8;WindowInput.py=9;WindowInput.visible=false;WindowInput.input=24;
  WindowBackend.swap();WindowCheck.packed();delivered(p,1,16,3,4,1,17);
  check(phases.equals(Arrays.asList(1,2,3,4,5,6,7,8,0)));
  WindowBackend.swap();delivered(p,2,16,8,9,0,24);
  WindowBackend.resize(32,16);delivered(p,3,16,8,9,0,24);check(NativeEgl.closed==1);
  check(phases.get(phases.size()-1)==-1);
  WindowCheck.set("statsStart",System.nanoTime()-6_000_000_000L);
  WindowBackend.swap();WindowCheck.packed();delivered(p,3,16,8,9,0,24);
  WindowBackend.close();delivered(p,4,32,8,9,0,24);check(NativeEgl.closed==2);
  var observer=WindowBackend.class.getDeclaredField("frameObserver");observer.setAccessible(true);check(observer.get(null)==null);
  System.out.println("PIPELINE_WINDOW_PASS");
 }
}'''

class PipelinedWindowTest(unittest.TestCase):
 def test_delayed_pixels_metadata_resize_close_and_failures(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td);paths=[]
   sources=dict(SOURCES);sources['wurm/graphics/NativeEgl.java']=NATIVE;sources['wurm/graphics/WindowInput.java']=POINTER;sources['wurm/graphics/PipelineCheck.java']=CHECK
   for name,code in sources.items():
    path=p/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(code);paths.append(str(path))
   paths += [str(ROOT/'graphics-compat/window/wurm/graphics'/n) for n in ['WindowBackend.java','FramePacer.java','FramePublisher.java']]
   paths += [str(ROOT/'graphics-compat/probe/wurm/graphics/FrameFile.java')]
   subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',td,*paths],check=True,capture_output=True)
   for async_mode in ('false','true'):
    for failure in ('','issue','collect'):
     r=subprocess.run(['java','-Dwurm.graphics.pipelinedReadback=true',f'-Dwurm.graphics.asyncPublication={async_mode}','-cp',td,'wurm.graphics.PipelineCheck',str(p/f'frame-{async_mode}-{failure}'),*([failure] if failure else [])],capture_output=True,text=True,timeout=20)
     self.assertEqual(r.returncode,0,r.stdout+r.stderr)
     self.assertIn('PIPELINE_FAILURE_PASS' if failure else 'PIPELINE_WINDOW_PASS',r.stdout)
     if not failure:self.assertIn('readbackMode=pipelined',r.stdout)
