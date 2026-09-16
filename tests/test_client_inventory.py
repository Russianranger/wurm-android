"""Execute the actual invocation transform and compare numeric alignment semantics/allocation."""
import os,re,subprocess,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')
PANEL='com/wurmonline/client/renderer/gui/WurmTreeList$TreeListPanel.class'
class InventoryTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.tmp.cleanup);cls.home=Path(cls.tmp.name);cls.classes=cls.home/'classes'
  fixture=cls.home/'NumericTable.java'
  fixture.write_text(r'''package fixture;public class NumericTable {
   public static boolean render(String value,char separator){return value.matches("\\d*\\"+separator+"?\\d*");}
   public static boolean other(String value){return value.matches("keep");}
  }''')
  subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(cls.home),str(fixture)],check=True)
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),*map(str,(ROOT/'runtime-probe/src').rglob('*.java')),str(ROOT/'tests/fixtures/inventory/client/InventoryFixture.java')],check=True)
 def run_java(self,*args,cp=None):
  r=subprocess.run(['java','-Xverify:all','-cp',cp or str(self.classes),'client.InventoryFixture',*map(str,args)],capture_output=True,text=True,timeout=60)
  self.assertEqual(r.returncode,0,r.stdout+r.stderr);return r.stdout
 def test_patched_alignment_preserves_locales_exceptions_concurrency_and_reduces_allocations(self):
  out=subprocess.run(['java','com.sun.tools.javap.Main','-c','-classpath',str(self.home),'fixture.NumericTable'],capture_output=True,text=True,check=True).stdout
  offset=re.search(r'(\d+): invokevirtual.*java/lang/String.matches',out)[1]
  print(self.run_java('fixture',self.home/'fixture/NumericTable.class',offset).strip())
 @unittest.skipUnless(CLIENT,'exact owner-provided client required')
 def test_private_panel_exact_reversal_integrity_and_jvm_verification(self):
  with zipfile.ZipFile(CLIENT) as z:original=z.read(PANEL)
  src=self.home/'private.class';src.write_bytes(original);overlay=self.home/'overlay';target=overlay/PANEL
  self.assertIn('INVENTORY_PRIVATE_PATCH_PASS',self.run_java('private',src,target))
  self.assertIn('INVENTORY_PRIVATE_VERIFY_PASS',self.run_java('verify',cp=os.pathsep.join(map(str,[self.classes,overlay,CLIENT]))))
