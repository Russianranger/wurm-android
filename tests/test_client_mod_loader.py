"""Actual client loader 0.15/Javassist with authored game ABI fixtures, never a desktop game.
Checks its real ModComm/ModClient/ModConsole/ModPacks hooks as well as our Android entry.
Native Android rendering and Live Map gameplay still require a physical device.
"""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOADER = os.environ.get('WURM_CLIENT_MOD_LOADER_JAR', '')
JAVASSIST = os.environ.get('WURM_MOD_JAVASSIST_JAR', '')


@unittest.skipUnless(LOADER and JAVASSIST, 'pinned client loader fixtures required')
class ClientModLoaderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert hashlib.sha256(Path(LOADER).read_bytes()).hexdigest() == 'c82b57c119f7b73b57310b3a9ee1114d7eed6ea0b60b5978e35e2749f76d8a72'
        assert hashlib.sha256(Path(JAVASSIST).read_bytes()).hexdigest() == 'eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf'
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-client-mod-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        cls.classes = cls.root/'classes'
        cls.cp = os.pathsep.join([str(cls.classes), LOADER, JAVASSIST])
        sources = {}

        def source(name, body):
            package, simple = name.rsplit('.', 1)
            sources[name.replace('.', '/')+'.java'] = 'package '+package+'; '+body.replace('$NAME', simple)

        source('com.wurmonline.shared.constants.SteamVersion', 'public class $NAME { public static String getCurrentVersion(){return "1.9.2.7";} }')
        source('com.wurmonline.communication.SocketConnection', 'public class $NAME {}')
        source('com.wurmonline.client.comm.SimpleServerConnectionClass', '''public class $NAME {
 private com.wurmonline.communication.SocketConnection connection;
 public void reallyHandle(int n, java.nio.ByteBuffer bb){byte cmd=bb.get();}
 public void reallyHandleCmdMessage(java.nio.ByteBuffer bb){String title="other", message="fixture";textMessage(title,message);}
 public void textMessage(String title,String message){}
}''')
        source('com.wurmonline.client.game.World', '''public class $NAME {
 public com.wurmonline.client.comm.SimpleServerConnectionClass getServerConnection(){return null;}
 public com.wurmonline.client.renderer.cell.CellRenderer getCellRenderer(){return null;}
}''')
        source('com.wurmonline.client.renderer.gui.HeadsUpDisplay', '''public class $NAME {
 public String value="overlay";
 void packageOnly(){value += ":package";}
 public void init(int w,int h){}
}''')
        source('com.wurmonline.client.console.WurmConsole', '''public class $NAME {
 public String last=""; public void handleInput2(String input,boolean silent){last=input;}
}''')
        source('com.wurmonline.client.WurmClientBase', '''public class $NAME {
 private static WurmClientBase clientObject=new WurmClientBase();
 private com.wurmonline.client.game.World world=new com.wurmonline.client.game.World();
 private com.wurmonline.client.renderer.gui.HeadsUpDisplay hud=new com.wurmonline.client.renderer.gui.HeadsUpDisplay();
 public void runGameLoop(){}
 public static com.wurmonline.client.resources.Resources getResourceManager(){return null;}
}''')
        source('com.wurmonline.client.resources.ResourceUrl', 'public class $NAME { public ResourceUrl derive(String name){return this;} }')
        source('com.wurmonline.client.resources.Resources', '''public class $NAME {
 private java.util.Map resolvedResources=new java.util.HashMap(); private java.util.Set unresolvedResources=new java.util.HashSet();
 private java.util.List packs=new java.util.ArrayList(); public ResourceUrl getResource(String name){return null;}
}''')
        source('com.wurmonline.client.resources.Pack', '''public class $NAME {
 void init(Resources r){} ResourceUrl getResource(String n){return null;}
}''')
        source('com.wurmonline.client.resources.JarPack', '''public class $NAME extends Pack {
 private java.util.jar.JarFile jarFile; public JarPack(java.io.File file){}
}''')
        source('com.wurmonline.client.resources.PackResourceUrl', 'public class $NAME extends ResourceUrl { private Pack pack; }')
        source('com.wurmonline.client.resources.textures.PlayerTextureBuilderGL', '''public class $NAME {
 public void generateTexture(){new com.wurmonline.client.resources.ResourceUrl().derive("texture");}
}''')
        source('com.wurmonline.client.renderer.cell.PlayerTexture', '''public class $NAME {
 public static class PlayerBodyTextureLoader implements Runnable {
 public void run(){new com.wurmonline.client.resources.ResourceUrl().derive("texture");} }
}''')
        source('com.wurmonline.client.renderer.cell.CellRenderable', 'public class $NAME {}')
        source('com.wurmonline.client.renderer.cell.CellRenderer', 'public class $NAME { private java.util.List tickRenderables; }')
        source('com.wurmonline.client.renderer.cell.PlayerCellRenderable', 'public class $NAME extends CellRenderable { private boolean textureDirty; }')
        source('com.wurmonline.client.renderer.PlayerBodyRenderable', 'public class $NAME { private boolean textureDirty; }')
        source('org.lwjgl.FixtureOwner', 'public class $NAME {}')
        source('wurm.graphics.FixtureBridge', '''public class $NAME {
 public static Class<?> game() throws Exception {return Class.forName("com.wurmonline.client.WurmClientBase");}
}''')
        source('client.ClientGraphicsPatch', '''public class $NAME {
 public static void verifySelected(){if(Boolean.getBoolean("fixture.badOverlay"))throw new IllegalStateException("bad overlay");}
}''')
        source('client.ClientBootstrap', '''public class $NAME {
 public static void main(String[] args) throws Exception {
  ClassLoader loader=ClientBootstrap.class.getClassLoader();
  if(loader==ClassLoader.getSystemClassLoader() || loader!=Thread.currentThread().getContextClassLoader())throw new AssertionError("loader identity");
  if(wurm.graphics.FixtureBridge.class.getClassLoader()!=loader || org.lwjgl.FixtureOwner.class.getClassLoader()!=loader)throw new AssertionError("split native owner");
  if(wurm.graphics.FixtureBridge.game()!=com.wurmonline.client.WurmClientBase.class)throw new AssertionError("split game state");
  var hud=new com.wurmonline.client.renderer.gui.HeadsUpDisplay(); hud.init(1280,720);
  if(!hud.value.equals(System.getProperty("fixture.expected","overlay")))throw new AssertionError(hud.value);
  final boolean[] ran={false}; org.gotti.wurmunlimited.modsupport.ModClient.runTask(()->ran[0]=true);
  org.gotti.wurmunlimited.modsupport.ModClient.getClientInstance().runGameLoop();
  if(!ran[0])throw new AssertionError("client task hook missing");
  var console=new com.wurmonline.client.console.WurmConsole();
  org.gotti.wurmunlimited.modsupport.console.ModConsole.addConsoleListener((input,silent)->input.equals("fixture"));
  console.handleInput2("fixture",false);if(!console.last.isEmpty())throw new AssertionError("console hook missing");
  console.handleInput2("ordinary",false);if(!console.last.equals("ordinary"))throw new AssertionError("console swallowed original input");
  System.out.println("CLIENT_FIXTURE_PASS "+hud.value);
 }
}''')
        source('fixture.Shared', r'''public class $NAME implements org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod, org.gotti.wurmunlimited.modloader.interfaces.Initable {
 public void init(){try{
  if(Boolean.getBoolean("fixture.fail"))throw new IllegalStateException("fixture mod failed");
  org.gotti.wurmunlimited.modloader.classhooks.HookManager.getInstance().getClassPool()
   .get("com.wurmonline.client.renderer.gui.HeadsUpDisplay").getDeclaredMethod("init")
   .insertAfter("value += \":shared\"; new com.wurmonline.client.renderer.gui.FixtureWindow().touch(this);");
 }catch(Exception e){throw new RuntimeException(e);}}
}''')
        source('com.wurmonline.client.renderer.gui.FixtureWindow', '''public class $NAME {
 public void touch(HeadsUpDisplay hud){hud.packageOnly();}
}''')
        source('fixture.Isolated', r'''public class $NAME implements org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod, org.gotti.wurmunlimited.modloader.interfaces.Initable {
 public void init(){try{
  org.gotti.wurmunlimited.modloader.classhooks.HookManager.getInstance().getClassPool()
   .get("com.wurmonline.client.renderer.gui.HeadsUpDisplay").getDeclaredMethod("init").insertAfter("value += \":isolated\";");
 }catch(Exception e){throw new RuntimeException(e);}}
}''')
        paths=[]
        for name, body in sources.items():
            path=cls.root/name; path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);paths.append(str(path))
        paths += [str(ROOT/f'runtime-probe/src/client/{n}.java') for n in ['ClientModBootstrap','ClientModLaunch']]
        result=subprocess.run(['java','com.sun.tools.javac.Main','-g','--release','17','-cp',LOADER+os.pathsep+JAVASSIST,'-d',str(cls.classes),*paths],text=True,capture_output=True)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
        for mod,names in [('shared',['fixture/Shared','com/wurmonline/client/renderer/gui/FixtureWindow']),('isolated',['fixture/Isolated'])]:
            with zipfile.ZipFile(cls.root/f'{mod}.jar','w') as jar:
                for name in names:
                    p=cls.classes/f'{name}.class';jar.write(p,f'{name}.class');p.unlink()

    def launch(self, mods=(), flags=(), mode='entry'):
        with tempfile.TemporaryDirectory(dir=self.root) as temp:
            work=Path(temp)
            for name in mods:
                folder=work/'mods'/name;folder.mkdir(parents=True)
                shutil.copy(self.root/f'{name}.jar',folder/f'{name}.jar')
                extra='sharedClassLoader=true\n' if name=='shared' else 'depend.requires=shared\n'
                (work/'mods'/f'{name}.properties').write_text(f'classname=fixture.{name.title()}\nclasspath={name}.jar\n'+extra)
            return subprocess.run(['java',*flags,'-cp',self.cp,'client.ClientModBootstrap',mode],cwd=work,text=True,capture_output=True,timeout=20)

    def test_loader_alone_runs_real_client_support_hooks_and_single_android_owner(self):
        r=self.launch();self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertIn('CLIENT_LOADER_READY count=0',r.stdout);self.assertIn('CLIENT_FIXTURE_PASS overlay',r.stdout)

    def test_shared_gui_package_and_isolated_mod_preserve_overlay_and_order(self):
        r=self.launch(['shared','isolated'],['-Dfixture.expected=overlay:shared:package:isolated'])
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertIn('CLIENT_MOD_READY shared',r.stdout);self.assertIn('CLIENT_MOD_READY isolated',r.stdout)
        self.assertIn('CLIENT_FIXTURE_PASS overlay:shared:package:isolated',r.stdout)

    def test_failure_never_enters_game_or_falls_back(self):
        for mods,flags in [(['shared'],['-Dfixture.fail=true']),([],['-Dfixture.badOverlay=true'])]:
            r=self.launch(mods,flags);self.assertEqual(r.returncode,42,r.stdout+r.stderr)
            self.assertIn('CLIENT_LOADER_FAILED',r.stderr);self.assertNotIn('CLIENT_FIXTURE_PASS',r.stdout)

    def test_mod_loader_rejects_diagnostic_stage(self):
        r=self.launch(mode='prepare-graphics');self.assertEqual(r.returncode,42,r.stdout+r.stderr)
        self.assertNotIn('CLIENT_LOADER_BEGIN',r.stdout)
