"""Exercise the window's real swap path against authored GL/EGL boundaries."""
from pathlib import Path
import subprocess,tempfile,unittest,re
ROOT=Path(__file__).resolve().parents[1]
SOURCES={
'org/lwjgl/opengl/GL.java':'''package org.lwjgl.opengl;public class GL{public static void create(String s){}public static GLCapabilities createCapabilities(){return new GLCapabilities();}public static void destroy(){}}''',
'org/lwjgl/opengl/GLCapabilities.java':'package org.lwjgl.opengl;public class GLCapabilities{}',
'org/lwjgl/glfw/GLFW.java':'''package org.lwjgl.glfw;public class GLFW{public static void glfwSetWindowShouldClose(long w,boolean b){}}''',
'org/lwjgl/opengl/GL11.java':'''package org.lwjgl.opengl;import java.nio.*;
public class GL11{
 public static final int GL_VERSION=0x1F02,GL_RENDERER=0x1F01,GL_NO_ERROR=0,GL_PACK_ALIGNMENT=0xD05,GL_PACK_ROW_LENGTH=0xD02,GL_PACK_SKIP_ROWS=0xD03,GL_PACK_SKIP_PIXELS=0xD04,GL_RGBA=0x1908,GL_UNSIGNED_BYTE=0x1401;
 public static final int[] pack=new int[0xD06];static{pack[GL_PACK_ALIGNMENT]=8;pack[GL_PACK_ROW_LENGTH]=9;pack[GL_PACK_SKIP_ROWS]=10;pack[GL_PACK_SKIP_PIXELS]=11;}public static boolean fail;public static int finishes,reads;
 public static String glGetString(int p){return "fixture";}public static int glGetError(){return 0;}
 public static int glGetInteger(int p){return pack[p];}public static void glPixelStorei(int p,int v){pack[p]=v;}
 public static void glReadPixels(int x,int y,int w,int h,int f,int t,ByteBuffer b){
  if(pack[GL_PACK_ALIGNMENT]!=1||pack[GL_PACK_ROW_LENGTH]!=0||pack[GL_PACK_SKIP_ROWS]!=0||pack[GL_PACK_SKIP_PIXELS]!=0)throw new AssertionError("pack setup");
  if(fail)throw new IllegalStateException("read failure");reads++;try{Thread.sleep(3);}catch(InterruptedException e){throw new AssertionError(e);}
  for(int i=0;i<w*h*4;i++)b.put(i,(byte)(i*13));
 }public static void glFlush(){}public static void glFinish(){finishes++;}}
''',
'wurm/graphics/NativeEgl.java':'''package wurm.graphics;class NativeEgl{
 static int swaps,error;static void open(String b,int w,int h){}static void resize(int w,int h){}static void close(){}
 static boolean readbackOpen(){return false;}static void readbackIssue(){}static void readbackCollect(java.nio.ByteBuffer b){}static void readbackClose(){}
 static int error(){return error;}static void swap(){swaps++;try{Thread.sleep(2);}catch(InterruptedException e){throw new AssertionError(e);}}}
''',
'wurm/graphics/WindowInput.java':'''package wurm.graphics;class WindowInput{
 WindowInput(int w,int h){}void resize(int w,int h){}double x(){return 3;}double y(){return 4;}void cursor(double x,double y){}
 int displayX(){return 3;}int displayY(){return 4;}boolean visible(){return true;}int applied(){return 17;}void release(){}boolean canApply(String s){return true;}void apply(String s){}}
''',
'wurm/graphics/WindowCheck.java':'''package wurm.graphics;import java.nio.*;import java.nio.file.*;import java.util.*;import org.lwjgl.opengl.GL11;
public class WindowCheck{
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void set(String key,long value)throws Exception{var f=WindowBackend.class.getDeclaredField(key);f.setAccessible(true);f.setLong(null,value);}
 static long get(String key)throws Exception{var f=WindowBackend.class.getDeclaredField(key);f.setAccessible(true);return f.getLong(null);}
 static void delivered(Path path,int sequence)throws Exception{
  long deadline=System.nanoTime()+5_000_000_000L;
  while(System.nanoTime()<deadline){if(Files.exists(path)&&ByteBuffer.wrap(Files.readAllBytes(path)).getInt(16)==sequence)return;Thread.sleep(1);}
  throw new AssertionError("frame not delivered");
 }
 static void packed(){check(GL11.pack[GL11.GL_PACK_ALIGNMENT]==8&&GL11.pack[GL11.GL_PACK_ROW_LENGTH]==9&&GL11.pack[GL11.GL_PACK_SKIP_ROWS]==10&&GL11.pack[GL11.GL_PACK_SKIP_PIXELS]==11);}
 public static void main(String[] args)throws Exception{
  Path path=Path.of(args[0]);System.setProperty("wurm.graphics.frame",path.toString());System.setProperty("wurm.graphics.fps","60");
  WindowBackend.open(16,16);set("statsStart",System.nanoTime()-6_000_000_000L);set("previousEnd",System.nanoTime()-20_000_000L);
  WindowBackend.swap();packed();check(GL11.finishes==0&&GL11.reads==1&&NativeEgl.swaps==1);
  delivered(path,1);byte[] saved=Files.readAllBytes(path);var header=ByteBuffer.wrap(saved);check(header.getInt()==0x57554746&&header.getInt()==3&&header.getInt()==16&&header.getInt()==16&&header.getInt()==1&&header.getInt()==3&&header.getInt()==4&&header.getInt()==1&&header.getInt()==17);
  check(saved.length==1060);for(int i=0;i<1024;i++)check(saved[36+i]==(byte)(i*13));
  GL11.fail=true;try{WindowBackend.swap();throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().equals("read failure"));}packed();check(Arrays.equals(saved,Files.readAllBytes(path)));GL11.fail=false;
  NativeEgl.error=7;try{WindowBackend.swap();throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().contains("FRAME_READBACK_ERROR"));}packed();check(Arrays.equals(saved,Files.readAllBytes(path)));NativeEgl.error=0;
  WindowBackend.resize(32,16);check(get("previousEnd")==0&&get("published")==0);WindowBackend.swap();packed();delivered(path,2);check(Files.size(path)==36+32*16*4);check(GL11.finishes==0);
  var f=WindowBackend.class.getDeclaredField("events");f.setAccessible(true);((java.util.Queue<String>)f.get(null)).add("FPS 30");WindowBackend.poll();check(get("previousEnd")==0&&get("published")==0);
  WindowBackend.close();check(GL11.finishes==1);System.out.println("WINDOW_STAGES_PASS");
 }}'''
}
class WindowStagesTest(unittest.TestCase):
 def test_swap_preserves_pixels_pack_state_errors_pacing_and_reports_separate_stages(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td);paths=[]
   for name,code in SOURCES.items():
    path=p/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(code);paths.append(str(path))
   paths += [str(ROOT/'graphics-compat/window/wurm/graphics'/n) for n in ['WindowBackend.java','FramePacer.java','FramePublisher.java']]
   paths += [str(ROOT/'graphics-compat/probe/wurm/graphics/FrameFile.java')]
   subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',td,*paths],check=True,capture_output=True)
   r=subprocess.run(['java','-cp',td,'wurm.graphics.WindowCheck',str(p/'frame')],capture_output=True,text=True,timeout=20)
   async_run=subprocess.run(['java','-Dwurm.graphics.asyncPublication=true','-cp',td,'wurm.graphics.WindowCheck',str(p/'async-frame')],capture_output=True,text=True,timeout=20)
   fallback=subprocess.run(['java','-Dwurm.graphics.pipelinedReadback=true','-cp',td,'wurm.graphics.WindowCheck',str(p/'fallback-frame')],capture_output=True,text=True,timeout=20)
   self.assertEqual(fallback.returncode,0,fallback.stdout+fallback.stderr)
   self.assertIn('requested=pipelined active=sync',fallback.stdout)
   self.assertEqual(async_run.returncode,0,async_run.stdout+async_run.stderr)
   self.assertIn('publicationMode=async',async_run.stdout)
   self.assertIn('buffers=2',async_run.stdout)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('WINDOW_STAGES_PASS',r.stdout)
   line=next(x for x in r.stdout.splitlines() if 'FRAME_TIMING' in x)
   fields=dict(re.findall(r'(\w+)=([^ ]+)',line))
   self.assertEqual(fields['targetFps'],'60');self.assertEqual(fields['samples'],'1');self.assertEqual(fields['workSamples'],'1');self.assertEqual(fields['size'],'16x16')
   self.assertGreaterEqual(float(fields['clientWorkMs']),19);self.assertGreaterEqual(float(fields['readbackMs']),2);self.assertGreaterEqual(float(fields['eglSwapMs']),1)
   for name in ['publishMs','pacingMs','captureSetupMs','captureRestoreMs','maxClientWorkMs','maxReadbackMs']:self.assertGreaterEqual(float(fields[name]),0)
