"""Text-buffer ownership/allocation regression; optional execution of exact private rendering classes."""
from pathlib import Path
import os,re,subprocess,tempfile,unittest,zipfile

ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')
EXPORTS=['--add-exports=java.base/sun.nio.ch=ALL-UNNAMED','--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED']
BASE='com/wurmonline/client/'
SOURCES={
'options/BooleanOption.java':'''package com.wurmonline.client.options; public class BooleanOption {public boolean value(){return false;}}''',
'options/GLOption.java':'''package com.wurmonline.client.options; public class GLOption {public static boolean gpu;public boolean disabled(){return !gpu;}public boolean inCore(){return true;}}''',
'options/Options.java':'''package com.wurmonline.client.options; public class Options {public static final boolean USE_DEV_DEBUG=false;public static final BooleanOption debugsEnabled=new BooleanOption(),prettyWeather=new BooleanOption();public static final GLOption useVBO=new GLOption(),useGLSL=new GLOption();}''',
'renderer/backend/Backend.java':'''package com.wurmonline.client.renderer.backend; public class Backend {public static boolean gl;public static int primitiveAllocs,defaultTextureId,lineCount,pointCount,primitiveCount,triangleCount;public static final int[] primitiveCountQueue=new int[64],triangleCountQueue=new int[64];public static boolean isGLThread(){return gl;}}''',
'util/BufferUtil.java':'''package com.wurmonline.client.util; public class BufferUtil {public static int getAllocatedMemory(){return 0;}}''',
'renderer/backend/VertexBuffer.java':'''package com.wurmonline.client.renderer.backend;
import java.nio.*;import java.util.concurrent.atomic.AtomicInteger;import com.wurmonline.client.options.GLOption;
public class VertexBuffer {
 public enum Usage{GUI,OTHER}
 private int numVertex,refCount=1;private boolean allowGPU=useGPU(),boundBufferObject;
 private AtomicInteger isLocked=new AtomicInteger();private FloatBuffer systemBuffer;private ByteBuffer owner;
 private static final sun.misc.Unsafe U;
 static{try{var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(sun.misc.Unsafe)f.get(null);}catch(Exception e){throw new AssertionError(e);}}
 private static boolean useGPU(){return GLOption.gpu;}
 public static VertexBuffer create(Usage u,int n,boolean a,boolean b,boolean c,boolean d,boolean e,int f,int g,boolean h,boolean i){VertexBuffer v=new VertexBuffer();v.numVertex=n;return v;}
 public FloatBuffer lock(){if(!isLocked.compareAndSet(0,2))throw new IllegalStateException();if(systemBuffer==null){owner=ByteBuffer.allocateDirect(numVertex*20).order(ByteOrder.nativeOrder());systemBuffer=owner.asFloatBuffer();}return systemBuffer;}
 public void unlock(){if(!isLocked.compareAndSet(2,0))throw new IllegalStateException();systemBuffer.rewind();}
 public FloatBuffer getSystemBuffer(){return systemBuffer;}
 public VertexBuffer reference(){refCount++;return this;}
 public void delete(){if(--refCount>0)return;if(owner!=null){U.invokeCleaner(owner);owner=null;systemBuffer=null;}}
}''',
'renderer/Matrix.java':'''package com.wurmonline.client.renderer;public class Matrix{}''',
'renderer/backend/RenderState.java':'''package com.wurmonline.client.renderer.backend;public class RenderState{}''',
'renderer/backend/Primitive.java':'''package com.wurmonline.client.renderer.backend;public class Primitive {public VertexBuffer vertex;public boolean destroyBuffers;public int num;}''',
'renderer/backend/Queue.java':'''package com.wurmonline.client.renderer.backend;import com.wurmonline.client.renderer.Matrix;public class Queue {private final Primitive[] queue=new Primitive[128];private int n;public Queue(int size,boolean sort){}public Primitive reservePrimitive(){return new Primitive();}public void queue(Primitive p,Matrix m){queue[n++]=p;}public void clear(){for(int i=0;i<n;i++){Primitive p=queue[i];if(p.destroyBuffers&&p.vertex!=null){p.vertex.delete();p.vertex=null;}}n=0;}}''',
'renderer/gui/text/SimpleTextFont.java':'''package com.wurmonline.client.renderer.gui.text;import com.wurmonline.client.renderer.backend.VertexBuffer;public class SimpleTextFont{public void fixture(){VertexBuffer v=VertexBuffer.create(VertexBuffer.Usage.GUI,60,true,false,false,false,false,2,0,false,false);v.delete();}}''',
'renderer/gui/text/TextFont.java':'''package com.wurmonline.client.renderer.gui.text;public class TextFont{}''',
'renderer/gui/text/GuiTextFont.java':'''package com.wurmonline.client.renderer.gui.text;import java.awt.Font;import com.wurmonline.client.renderer.backend.Queue;public class GuiTextFont {public GuiTextFont(Font f){}public void moveTo(int x,int y){}public int paint(Queue q,String s){return s.length()*7;}public int getWidth(String s){return s.length()*7;}}''',
'renderer/gui/text/FontTexture.java':'''package com.wurmonline.client.renderer.gui.text;import java.awt.Font;import com.wurmonline.client.resources.textures.Texture;
public class FontTexture {public static class CharDetails{public float u0=0.1f,v0=0.2f,u1=0.3f,v1=0.4f;public int width=7,height=12;}
 private final CharDetails glyph=new CharDetails();public FontTexture(Font f,boolean b){}public boolean texturesLoaded(){return true;}public void init(){}public Texture getTexture(){return null;}public CharDetails getCharDetails(char c){return c<128?glyph:null;}public int getAscent(){return 9;}public int getDescent(){return 3;}public int getLeading(){return 0;}public int getMaxHeight(){return 12;}public int getWidth(String s){return s.length()*7;}public int getWidth(char[] c,int o,int n){return n*7;}}''',
'resources/textures/Texture.java':'''package com.wurmonline.client.resources.textures;public class Texture{}''',
'renderer/gui/Renderer.java':'''package com.wurmonline.client.renderer.gui;import com.wurmonline.client.renderer.backend.RenderState;public class Renderer{public static final RenderState stateAlphaBlend=new RenderState();}''',
'renderer/backend/ScissorControl.java':'''package com.wurmonline.client.renderer.backend;public class ScissorControl{public static class ClipRect{public boolean isVisible(){return true;}public void doClip(){}}private final ClipRect clip=new ClipRect();public ClipRect getCurrent(){return clip;}}''',
'renderer/gui/HeadsUpDisplay.java':'''package com.wurmonline.client.renderer.gui;import com.wurmonline.client.renderer.backend.ScissorControl;public class HeadsUpDisplay{public static final ScissorControl scissor=new ScissorControl();}''',
}

SOURCES.update({
'renderer/light/LightManager.java':'''package com.wurmonline.client.renderer.light;public class LightManager {public static void disableAllLights(){}public static void disableAllSecondaryLights(boolean all){}}''',
'util/GLHelper.java':'''package com.wurmonline.client.util;public class GLHelper {public static boolean checkGlError(String label,Object o){return false;}}''',
})

GL_SOURCES={
'org/lwjgl/opengl/GL15.java':'''package org.lwjgl.opengl;import java.nio.*;import java.util.*;
public class GL15 {public static int next,bound,uploads,deleted;public static final Map<Integer,float[]> live=new HashMap<>();
 public static int glGenBuffers(){int id=++next;live.put(id,new float[0]);return id;}
 public static void glBindBuffer(int target,int id){if(id!=0&&!live.containsKey(id))throw new AssertionError("freed VBO");if(target==34962)bound=id;}
 public static void glBufferData(int target,FloatBuffer b,int usage){if(bound==0)throw new AssertionError("no VBO");float[] a=new float[b.remaining()];b.duplicate().get(a);live.put(bound,a);uploads++;}
 public static void glDeleteBuffers(int id){if(live.remove(id)==null)throw new AssertionError("double VBO delete");deleted++;}
 public static float first(int id){return live.get(id)[0];}public static int count(){return live.size();}}
''',
'org/lwjgl/opengl/GL30.java':'''package org.lwjgl.opengl;import java.util.*;public class GL30{public static int next,bound,deleted;public static final Set<Integer> live=new HashSet<>();
 public static int glGenVertexArrays(){int id=++next;live.add(id);return id;}public static void glBindVertexArray(int id){if(id!=0&&!live.contains(id))throw new AssertionError("freed VAO");bound=id;}
 public static void glDeleteVertexArrays(int id){if(!live.remove(id))throw new AssertionError("double VAO delete");deleted++;}}
''',
'org/lwjgl/opengl/GL20.java':'''package org.lwjgl.opengl;public class GL20{public static void glEnableVertexAttribArray(int i){}public static void glDisableVertexAttribArray(int i){}public static void glVertexAttribPointer(int i,int n,int t,boolean normalized,int stride,long offset){if(GL15.bound==0||GL30.bound==0)throw new AssertionError("missing VBO/VAO");}}''',
'org/lwjgl/opengl/GL11.java':(ROOT/'tests/fixtures/text_buffers/org/lwjgl/opengl/GL11.java').read_text(),
'org/lwjgl/opengl/GL13.java':'''package org.lwjgl.opengl;public class GL13{public static void glActiveTexture(int i){}public static void glClientActiveTexture(int i){}}''',
}

class TextBuffersTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.temp.cleanup);cls.home=Path(cls.temp.name);cls.classes=cls.home/'classes';cls.stub=cls.home/'stubs'
  paths=[]
  for name,text in [(BASE+n,t) for n,t in SOURCES.items()]+list(GL_SOURCES.items()):
   p=cls.home/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);paths.append(str(p))
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.stub),*paths],check=True,capture_output=True,text=True)
  sources=[*map(str,(ROOT/'runtime-probe/src/client').glob('*.java')),str(ROOT/'runtime-probe/src/probe/RuntimeMeasurements.java'),str(ROOT/'tests/fixtures/text_buffers/client/TextFixture.java')]
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),*sources],check=True,capture_output=True,text=True)
  cls.original=cls.home/'original.jar'
  with zipfile.ZipFile(cls.original,'w') as z:
   for p in cls.stub.rglob('*.class'):z.write(p,p.relative_to(cls.stub).as_posix())
  cls.overlay=cls.home/'overlay.jar';cls.run_java('patch',str(cls.original),str(cls.overlay),'false')
  if CLIENT:
   cls.private=cls.home/'private-overlay.jar';cls.run_java('patch',CLIENT,str(cls.private),'true')
   cls.boundaries=cls.home/'boundaries.jar'
   excluded={BASE+n for n in ['renderer/backend/VertexBuffer','renderer/backend/Queue','renderer/backend/Primitive','renderer/backend/RenderState','renderer/Matrix','renderer/gui/text/SimpleTextFont','util/BufferUtil']}
   with zipfile.ZipFile(cls.boundaries,'w') as z:
    for p in cls.stub.rglob('*.class'):
     name=p.relative_to(cls.stub).as_posix()
     if not any(name==n+'.class' or name.startswith(n+'$') for n in excluded):z.write(p,name)
 @classmethod
 def run_java(cls,*args,private=False):
  cp=[cls.classes]
  if private:cp += [cls.private,cls.boundaries,Path(CLIENT)]
  else:cp += [cls.overlay,cls.original]
  r=subprocess.run(['java',*EXPORTS,'-Dwurm.client.reuseTextBuffers=true','-cp',os.pathsep.join(map(str,cp)),'client.TextFixture',*args],capture_output=True,text=True,timeout=40)
  if r.returncode:raise AssertionError(r.stdout+r.stderr)
  return r.stdout
 def test_lease_lifetime_shared_references_zeroing_and_expiry(self):
  self.assertIn('TEXT_LIFETIME_PASS',self.run_java('lifetime'))
 def test_rejection_guards_and_bounded_diagnostics(self):
  self.assertIn('TEXT_GUARDS_PASS',self.run_java('guards'))
  if CLIENT:self.assertIn('TEXT_GUARDS_PASS',self.run_java('guards',private=True))
 def test_rejected_returns_do_not_add_material_heap_allocation(self):
  for private in ([False,True] if CLIENT else [False]):
   old=self.run_java('reject-baseline',private=private);new=self.run_java('reject-benchmark',private=private)
   baseline=int(re.search(r'heapBytes=(\d+)',old)[1]);rejected=int(re.search(r'heapBytes=(\d+)',new)[1])
   self.assertLessEqual(rejected,baseline*1.10+4096)
   if private:print(old.strip());print(new.strip())
 def test_count_and_byte_limits_include_active_buffers(self):
  self.assertIn('TEXT_LIMIT_PASS',self.run_java('limits'));self.assertIn('TEXT_BYTE_LIMIT_PASS',self.run_java('bytes'))
 def test_concurrent_borrowers_have_exclusive_buffers(self):
  self.assertIn('TEXT_THREADS_PASS',self.run_java('threads'))
 def test_release_occurs_through_queue_clear(self):
  self.assertIn('TEXT_QUEUE_PASS',self.run_java('queue'))
 def test_warmed_reuse_reduces_creation_and_heap_allocation(self):
  old=self.run_java('baseline');new=self.run_java('benchmark')
  self.assertLess(int(re.search(r'heapBytes=(\d+)',new)[1]),int(re.search(r'heapBytes=(\d+)',old)[1])*0.25)
 @unittest.skipUnless(CLIENT,'requires owner-provided client JAR')
 def test_exact_private_vertex_queue_lifetime_and_allocation(self):
  for mode in ['lifetime','limits','bytes','threads','queue']:self.run_java(mode,private=True)
  old=self.run_java('baseline',private=True);new=self.run_java('benchmark',private=True)
  self.assertLess(int(re.search(r'heapBytes=(\d+)',new)[1]),int(re.search(r'heapBytes=(\d+)',old)[1])*0.25)
  print(old.strip());print(new.strip())
 @unittest.skipUnless(CLIENT,'requires owner-provided client JAR')
 def test_exact_private_gpu_upload_vao_reuse_and_deferred_eviction(self):
  self.assertIn('TEXT_GPU_PASS',self.run_java('gpu',private=True))
 @unittest.skipUnless(CLIENT,'requires owner-provided client JAR')
 def test_full_optional_overlay_with_job_recording_on_and_off(self):
  for reuse,jobs,clips in [(r,j,c) for r in ('true','false') for j in ('true','false') for c in ('true','false')]:
   path=self.home/f'full-{reuse}-{jobs}-{clips}.jar'
   args=['java',*EXPORTS,'-cp',os.pathsep.join(map(str,[self.classes,CLIENT])),'client.TextFixture','prepare-overlay',str(path),reuse,jobs,clips]
   r=subprocess.run(args,capture_output=True,text=True,timeout=40);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
   args[args.index('-cp')+1]=os.pathsep.join(map(str,[self.classes,path,CLIENT]));args[args.index('prepare-overlay')]='verify-overlay'
   r=subprocess.run(args,capture_output=True,text=True,timeout=40);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
   self.assertIn('TEXT_FULL_OVERLAY_PASS',r.stdout)
   self.assertEqual(r.stdout.count('TEXT_BUFFER_PATCH_ACTIVE'),3 if reuse=='true' else 0)
   self.assertEqual(r.stdout.count('CLIP_SNAPSHOT_PATCH_ACTIVE'),1 if clips=='true' else 0)
 @unittest.skipUnless(CLIENT,'requires owner-provided client JAR')
 def test_exact_private_legacy_draw_and_queue_release(self):
  old=self.run_java('font-legacy-baseline',private=True);new=self.run_java('font-legacy',private=True)
  self.assertEqual(re.search(r'geometry=(\w+)',old)[1],re.search(r'geometry=(\w+)',new)[1])
  self.assertEqual(re.search(r'drawHash=(-?\d+)',old)[1],re.search(r'drawHash=(-?\d+)',new)[1])
  self.assertIn('legacy=true draws=120',new);print(new.strip())
 @unittest.skipUnless(CLIENT,'requires owner-provided client JAR')
 def test_exact_private_text_geometry_matches_with_and_without_reuse(self):
  old=self.run_java('font-baseline',private=True);new=self.run_java('font',private=True)
  self.assertEqual(re.search(r'geometry=(\w+)',old)[1],re.search(r'geometry=(\w+)',new)[1])
  print(new.strip())

if __name__=='__main__':unittest.main()
