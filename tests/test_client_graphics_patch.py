"""Execute authored call-site fixtures; never distribute or require a game JAR."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class ClientGraphicsPatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.classes = cls.root/'classes'
        sources = {
            'fixture/LegacyCaps.java': 'package fixture; public class LegacyCaps { public static int getCapabilities() { return 0; } }',
            'fixture/FboCaps.java': 'package fixture; public class FboCaps { public static int getCapabilities() { return 1; } }',
            'fixture/OtherCaps.java': 'package fixture; public class OtherCaps { public static int getCapabilities() { return 10; } }',
            'com/wurmonline/client/WurmClientBase.java': '''package com.wurmonline.client;
public class WurmClientBase {
 public static int check() { return fixture.LegacyCaps.getCapabilities()+fixture.OtherCaps.getCapabilities(); }
 public static long constant() { return 123456789012345L; }
 public static String text() { return "untouched \\u0000 \\ud83d\\ude00"; }
}''',
            'client/PatchFixture.java': '''package client;
import java.nio.file.*;
public class PatchFixture {
 public static void main(String[] args) throws Exception {
  byte[] original=Files.readAllBytes(Path.of(args[0]));
  String hash=ClientGraphicsPatch.sha(original);
  byte[] patched=ClientGraphicsPatch.redirect(original,hash,"fixture/LegacyCaps","fixture/FboCaps");
  class Loader extends ClassLoader { Class<?> load(byte[] b) { return defineClass(null,b,0,b.length); } }
  Class<?> before=new Loader().load(original), after=new Loader().load(patched);
  if(!before.getMethod("check").invoke(null).equals(10) || !after.getMethod("check").invoke(null).equals(11)) throw new AssertionError("dispatch");
  for(String method:new String[]{"constant","text"})
   if(!before.getMethod(method).invoke(null).equals(after.getMethod(method).invoke(null))) throw new AssertionError("unrelated data");
  byte[] restored=ClientGraphicsPatch.redirect(patched,ClientGraphicsPatch.sha(patched),"fixture/FboCaps","fixture/LegacyCaps");
  if(!java.util.Arrays.equals(original,restored)) throw new AssertionError("not byte exact after reverse");
  for(String[] rule:new String[][]{{"invalid-hash","fixture/LegacyCaps"},{hash,"fixture/Missing"}}) {
   try { ClientGraphicsPatch.redirect(original,rule[0],rule[1],"fixture/FboCaps"); throw new AssertionError("invalid input accepted"); }
   catch(java.io.IOException expected) {}
  }
  System.out.println("PATCH_CONTRACT_PASS dispatch=one unrelatedData=identical reverse=byte-exact unsupported=rejected");
 }
}'''
        }
        paths = []
        for name, text in sources.items():
            p = cls.root/name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text); paths.append(p)
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),
                        *map(str, (ROOT/'runtime-probe/src/client').glob('*.java')), *map(str, paths)], check=True)
        cls.engine = cls.classes/'com/wurmonline/client/WurmClientBase.class'

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_only_selected_owner_changes_and_reverse_is_byte_exact(self):
        result = subprocess.run(['java','-cp',str(self.classes),'client.PatchFixture',str(self.engine)], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('PATCH_CONTRACT_PASS', result.stdout)

    def run_stage(self, stage, overlay):
        return subprocess.run(['java',f'-Dwurm.client.offscreenOverlay={overlay}','-cp',str(self.classes),
                               'client.ClientBootstrap',stage],capture_output=True,text=True,timeout=10)

    def test_production_prepare_rejects_uninspected_engine_without_output(self):
        target = self.root/'unsupported.jar'
        result = self.run_stage('prepare-graphics', target)
        self.assertEqual(result.returncode, 42, result.stdout+result.stderr)
        self.assertIn('CLIENT_GRAPHICS_PATCH_UNSUPPORTED engineSha256=', result.stdout)
        self.assertFalse(target.exists())
        self.assertFalse(target.with_suffix('.jar.pending').exists())

    def test_entry_rejects_tampered_or_missing_overlay_before_engine_initialization(self):
        target = self.root/'tampered.jar'
        with zipfile.ZipFile(target,'w') as jar:
            jar.write(self.engine,'com/wurmonline/client/WurmClientBase.class')
        for overlay in [target, self.root/'absent.jar']:
            with self.subTest(overlay=overlay.name):
                result = self.run_stage('entry',overlay)
                self.assertEqual(result.returncode,42,result.stdout+result.stderr)
                self.assertNotIn('ENTRY_INITIALIZE ',result.stdout)
                self.assertIn('BOOTSTRAP_FAILED stage=entry',result.stdout)


if __name__ == '__main__':
    unittest.main()
