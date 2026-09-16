"""Per-component observation through patched bytecode; no game implementation in fixtures."""
import os,re,subprocess,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')
BASE='com/wurmonline/client/'
SOURCES={
'job/Job.java':'package com.wurmonline.client.job; public interface Job {void execute(Object o);}',
'renderer/backend/Queue.java':'package com.wurmonline.client.renderer.backend; public class Queue {}',
'renderer/gui/WurmComponent.java':'''package com.wurmonline.client.renderer.gui;
import com.wurmonline.client.renderer.backend.Queue;
public class WurmComponent {public int calls;public float fraction;public boolean allocate=true;public Throwable failure;static volatile Object sink;
 public final void render(Queue q,float f)throws Throwable {calls++;fraction=f;if(failure!=null)throw failure;if(allocate)sink=new byte[4096];}}''',
'renderer/gui/Renderer.java':'''package com.wurmonline.client.renderer.gui;
import com.wurmonline.client.renderer.backend.Queue;
public class Renderer {public int calls,lastX,lastY;public WurmComponent component=new WurmComponent();
 private void renderSpyglassDistance(Queue q){calls++;}private void renderCrosshair(Queue q){calls++;}
 private void renderHoverInfo(Queue q,int x,int y){calls++;lastX=x;lastY=y;}private void renderOnscreenMessageViewer(Queue q){calls++;}
 public void execute(Object arg)throws Throwable{Queue q=(Queue)arg;renderSpyglassDistance(q);renderCrosshair(q);renderCrosshair(q);component.render(q,0.5f);renderHoverInfo(q,17,29);renderOnscreenMessageViewer(q);}}'''
}
class GuiProfilingTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.tmp.cleanup);cls.home=Path(cls.tmp.name);cls.stubs=cls.home/'stubs';cls.classes=cls.home/'classes';paths=[]
  for name,code in SOURCES.items():
   p=cls.home/'src'/BASE/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(str(p))
  # Java 8 emits the same invokespecial private dispatch used by the imported renderer.
  subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(cls.stubs),*paths],check=True)
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',str(cls.stubs),'-d',str(cls.classes),*map(str,(ROOT/'runtime-probe/src').rglob('*.java')),str(ROOT/'tests/fixtures/gui_profiling/client/GuiFixture.java')],check=True)
  inspect=subprocess.run(['java','com.sun.tools.javap.Main','-p','-c','-classpath',str(cls.stubs),'com.wurmonline.client.renderer.gui.Renderer'],capture_output=True,text=True,check=True).stdout
  execute=inspect.split('public void execute(')[1]
  offsets=re.findall(r'\s(\d+):\s+invoke(?:special|virtual).*// Method (?:renderSpyglassDistance|renderCrosshair|com/wurmonline/client/renderer/gui/WurmComponent.render|renderHoverInfo|renderOnscreenMessageViewer):',execute)
  if len(offsets)!=6:raise AssertionError(inspect)
  path=cls.stubs/(BASE+'renderer/gui/Renderer.class');original=cls.home/'original.class';original.write_bytes(path.read_bytes())
  cls.launch('patch',original,path,','.join(offsets),'183')
 @classmethod
 def launch(cls,*args,cp=None):
  r=subprocess.run(['java','-Xverify:all','-cp',cp or os.pathsep.join(map(str,[cls.classes,cls.stubs])),'client.GuiFixture',*map(str,args)],capture_output=True,text=True,timeout=30)
  if r.returncode:raise AssertionError(r.stdout+r.stderr)
  return r.stdout
 def test_component_overlay_dispatch_errors_bounds_recording_and_no_per_call_heap(self):
  out=self.launch('dispatch');print(next(x for x in out.splitlines() if 'GUI_DISPATCH_PASS' in x));self.assertIn('GUI_SCOPES',out)
 @unittest.skipUnless(CLIENT,'exact owner-provided client required')
 def test_private_renderer_patch_reverses_exactly_and_passes_jvm_verifier(self):
  with zipfile.ZipFile(CLIENT) as z:source=z.read(BASE+'renderer/gui/Renderer.class')
  original=self.home/'private.class';original.write_bytes(source);overlay=self.home/'overlay';target=overlay/(BASE+'renderer/gui/Renderer.class');target.parent.mkdir(parents=True,exist_ok=True)
  self.assertIn('GUI_PATCH_PASS',self.launch('patch',original,target))
  self.assertIn('GUI_PRIVATE_VERIFY_PASS',self.launch('verify-class',cp=os.pathsep.join(map(str,[self.classes,overlay,CLIENT]))))
