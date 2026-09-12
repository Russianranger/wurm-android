"""Actual Ago discovery/ordering/classloader on Java 17, with authored game/lifecycle fixtures.
No Wurm runtime or world is distributed/started. Real game hooks still require device testing.
"""
from pathlib import Path
import hashlib
import os
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOADER = os.environ.get('WURM_MOD_LOADER_JAR', '')
JAVASSIST = os.environ.get('WURM_MOD_JAVASSIST_JAR', '')

@unittest.skipUnless(LOADER and JAVASSIST, 'pinned upstream loader fixtures required')
class ServerModLoaderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert hashlib.sha256(Path(LOADER).read_bytes()).hexdigest() == '44f7c9adc2dfbe2de45d7bc22a6ef8448549cde3f1f164b5c59a094948e6bacf'
        assert hashlib.sha256(Path(JAVASSIST).read_bytes()).hexdigest() == 'eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf'
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-mod-loader-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        cls.classes = cls.root / 'classes'
        cls.cp = os.pathsep.join([str(cls.classes), LOADER, JAVASSIST])
        sources = {
            'org/gotti/wurmunlimited/modloader/ModLoader.java': '''package org.gotti.wurmunlimited.modloader;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
public class ModLoader extends ModLoaderShared<WurmServerMod> {
 public ModLoader(){ super(WurmServerMod.class); }
 protected void modcommInit(){} protected void preInit(){} protected void init(){}
 public String getGameVersion(){return "fixture";}
 public String getVersion(){return "0.47";}
}''',
            'org/gotti/wurmunlimited/modloader/server/ServerHook.java': '''package org.gotti.wurmunlimited.modloader.server;
import java.util.List;
public class ServerHook {
 public static ServerHook createServerHook(){return new ServerHook();}
 public void addMods(List<?> mods){if(mods.size()!=2) throw new AssertionError("two mods expected");}
 public void addVersionHandler(String version,String game,List<?> mods){}
}''',
            'game/Target.java': '''package game;
public class Target { public static String value(){return "overlay";} }''',
            'org/sqlite/FixtureDriver.java': 'package org.sqlite; public class FixtureDriver {}',
            'server/ManagedServerMain.java': '''package server;
public class ManagedServerMain {
 public static void main(String[] args) throws Exception {
  if(!"Adventure".equals(args[0])) throw new AssertionError("world argument");
  ClassLoader transforming=ManagedServerMain.class.getClassLoader();
  if(transforming==ClassLoader.getSystemClassLoader()) throw new AssertionError("parent loaded game");
  if(transforming!=Thread.currentThread().getContextClassLoader()) throw new AssertionError("context loader");
  if(org.sqlite.FixtureDriver.class.getClassLoader()!=ClassLoader.getSystemClassLoader()) throw new AssertionError("duplicate driver");
  if(ServerDiagnostics.class.getClassLoader()!=ClassLoader.getSystemClassLoader()) throw new AssertionError("duplicate diagnostics");
  if(!"overlay:first:second".equals(game.Target.value())) throw new AssertionError(game.Target.value());
  System.out.println("FIXTURE_MOD_BOOTSTRAP_PASS");
 }
}''',
            'fixture/First.java': '''package fixture;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
public class First implements WurmServerMod, PreInitable {
 public void preInit(){ try {
  HookManager.getInstance().getClassPool().get("game.Target").getDeclaredMethod("value").insertAfter("$_ = $_ + \\":first\\";");
 } catch(Exception e){throw new RuntimeException(e);} }
}''',
            'fixture/Second.java': '''package fixture;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
public class Second implements WurmServerMod, PreInitable {
 public void preInit(){ try {
  if(Boolean.getBoolean("fixture.fail")) throw new IllegalStateException("fixture init failure");
  HookManager.getInstance().getClassPool().get("game.Target").getDeclaredMethod("value").insertAfter("$_ = $_ + \\":second\\";");
 } catch(Exception e){throw new RuntimeException(e);} }
}''',
        }
        paths=[]
        for name, text in sources.items():
            path=cls.root/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text); paths.append(str(path))
        for name in ['ServerModBootstrap','ServerModLaunch','ServerDiagnostics','ServerLogHandler']:
            paths.append(str(ROOT / f'runtime-probe/src/server/{name}.java'))
        result=subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',os.pathsep.join([LOADER,JAVASSIST]),'-d',str(cls.classes),*paths],text=True,capture_output=True)
        if result.returncode: raise AssertionError(result.stderr)
        # Isolated mod class loaders must find mod entry classes in their own JARs.
        for name, java in [('first','First'),('second','Second')]:
            path=cls.root/f'mods/{name}'; path.mkdir(parents=True)
            with zipfile.ZipFile(path/f'{name}.jar','w') as z:
                z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nImplementation-Version: 1.0\n\n')
                z.write(cls.classes/f'fixture/{java}.class',f'fixture/{java}.class')
            (cls.classes/f'fixture/{java}.class').unlink()
            (cls.root/f'mods/{name}.properties').write_text(f'classname=fixture.{java}\nclasspath={name}.jar\n'+('depend.requires=first\n' if name=='second' else ''))

    def launch(self, *args):
        return subprocess.run(['java','-Djava.io.tmpdir='+str(self.root),*args,'-cp',self.cp,'server.ServerModBootstrap','Adventure'],cwd=self.root,text=True,capture_output=True,timeout=20)

    def test_two_mods_transform_same_class_in_dependency_order_before_game_load(self):
        result=self.launch()
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('SERVER_MOD_READY first',result.stdout)
        self.assertIn('SERVER_MOD_READY second',result.stdout)
        self.assertIn('SERVER_LOADER_READY count=2',result.stdout)
        self.assertIn('FIXTURE_MOD_BOOTSTRAP_PASS',result.stdout)
        self.assertTrue((self.root/'mods/first/first.jar').is_file())

    def test_failed_mod_initialization_never_falls_back_to_vanilla(self):
        result=self.launch('-Dfixture.fail=true')
        self.assertEqual(result.returncode,1,result.stdout+result.stderr)
        self.assertIn('SERVER_LOADER_FAILED',result.stderr)
        self.assertIn('fixture init failure',result.stderr)
        self.assertNotIn('FIXTURE_MOD_BOOTSTRAP_PASS',result.stdout)
