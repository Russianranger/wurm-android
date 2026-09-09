"""Execute authored legacy ABI fixtures against real Java 17 direct allocations."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXPORTS = ['--add-exports=java.base/sun.nio.ch=ALL-UNNAMED',
           '--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED']


class ClientBuffersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.classes = cls.root/'classes'
        sources = {
            'fixture/DirectCleanup.java': '''package fixture;
import java.nio.Buffer;
import sun.nio.ch.DirectBuffer;
import jdk.internal.ref.Cleaner;
public class DirectCleanup {
 public static void release(Buffer value) {
  if (!(value instanceof DirectBuffer)) return;
  DirectBuffer direct=(DirectBuffer)value;
  Cleaner cleanup=direct.cleaner();
  if(cleanup==null) cleanup=((DirectBuffer)direct.attachment()).cleaner();
  cleanup.clean();
 }
 public static long unrelated() { return 12345678912345L; }
}''',
            'client/BufferFixture.java': '''package client;
import java.nio.*;
import java.nio.file.*;
import java.lang.management.*;
import java.lang.reflect.*;
public class BufferFixture {
 public static void main(String[] args) throws Exception {
  byte[] modern=Files.readAllBytes(Path.of(args[0]));
  byte[] legacy=ClientBuffers.relocate(modern,ClientGraphicsPatch.sha(modern),true);
  byte[] fixed=ClientBuffers.relocate(legacy,ClientGraphicsPatch.sha(legacy),false);
  if(!java.util.Arrays.equals(modern,fixed)) throw new AssertionError("round trip changed other bytes");
  class Loader extends ClassLoader { Class<?> define(byte[] b) { return defineClass(null,b,0,b.length); } }
  Class<?> old=new Loader().define(legacy), updated=new Loader().define(fixed);
  Method release=updated.getMethod("release",Buffer.class);
  ByteBuffer rejected=ByteBuffer.allocateDirect(1024);
  try { old.getMethod("release",Buffer.class).invoke(null,rejected); throw new AssertionError("old ABI unexpectedly works"); }
  catch(InvocationTargetException error) { if(!(error.getCause() instanceof NoSuchMethodError)) throw error; }
  finally { release.invoke(null,rejected); }
  if(!old.getMethod("unrelated").invoke(null).equals(updated.getMethod("unrelated").invoke(null))) throw new AssertionError("unrelated");
  BufferPoolMXBean pool=ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream().filter(p->p.getName().equals("direct")).findFirst().orElseThrow();
  for(int kind=0;kind<4;kind++) {
   long count=pool.getCount(),bytes=pool.getMemoryUsed();
   ByteBuffer owner=ByteBuffer.allocateDirect(8192);
   Buffer view=switch(kind){case 0->owner;case 1->owner.asFloatBuffer();case 2->owner.asIntBuffer();default->owner.asDoubleBuffer();};
   if(pool.getCount()!=count+1) throw new AssertionError("allocation not measured");
   release.invoke(null,view);
   if(pool.getCount()!=count || pool.getMemoryUsed()!=bytes) throw new AssertionError("native allocation leaked kind="+kind);
  }
  release.invoke(null,new Object[]{null}); release.invoke(null,ByteBuffer.allocate(32));
  try { ClientBuffers.relocate(legacy,"bad-hash",false); throw new AssertionError("hash not checked"); } catch(java.io.IOException expected) {}
  for(String name:ClientBuffers.ORIGINALS.keySet()) {
   try { ClientBuffers.prepare(name,legacy); throw new AssertionError("unknown class accepted"); } catch(java.io.IOException expected) {}
   try { ClientBuffers.verify(name,fixed); throw new AssertionError("tampered class accepted"); } catch(java.io.IOException expected) {}
  }
  System.out.println("BUFFER_ABI_FIXTURE_PASS old=NoSuchMethodError kinds=4 nativeAllocationsFreed=true reverse=byte-exact hashes=enforced");
 }
}'''
        }
        paths = []
        for name, text in sources.items():
            p = cls.root/name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text); paths.append(p)
        result = subprocess.run(['java','com.sun.tools.javac.Main','-source','17','-target','17',*EXPORTS,
            '-d',str(cls.classes),*map(str,(ROOT/'runtime-probe/src/client').glob('*.java')),*map(str,paths)],capture_output=True,text=True)
        if result.returncode: raise AssertionError(result.stdout+result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_relocation_corrects_legacy_abi_and_releases_real_direct_allocations(self):
        result = subprocess.run(['java',*EXPORTS,'-cp',str(self.classes),'client.BufferFixture',
            str(self.classes/'fixture/DirectCleanup.class')],capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('BUFFER_ABI_FIXTURE_PASS',result.stdout)

    def test_missing_each_export_is_reported_before_loading_user_classes(self):
        for flags, missing in [([], 'sun.nio.ch'),([EXPORTS[0]],'jdk.internal.ref'),([EXPORTS[1]],'sun.nio.ch')]:
            with self.subTest(missing=missing,flags=flags):
                result = subprocess.run(['java',*flags,'-cp',str(self.classes),'client.ClientBootstrap','buffers'],capture_output=True,text=True,timeout=10)
                self.assertEqual(result.returncode,42,result.stdout+result.stderr)
                self.assertIn('CLIENT_BUFFER_EXPORT_REQUIRED --add-exports=java.base/'+missing+'=ALL-UNNAMED',result.stdout)
                self.assertNotIn('ClassNotFoundException',result.stdout)


if __name__ == '__main__':
    unittest.main()
