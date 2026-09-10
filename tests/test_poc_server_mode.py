"""Exercise the source and packaged POC against a launcher that owns server mode.

Optional private validation executes the supplied launcher's original runServer
method until an authored startRunning sentinel. No game world is opened.
"""
import base64
import hashlib
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SERVER = os.environ.get('WURM_TEST_SERVER_JAR')


def isolated_run_server(data):
    """Keep original fields and the complete, byte-identical (ZZ)V method only."""
    pos, index, utf = 10, 1, {}
    count = struct.unpack_from('>H', data, 8)[0]
    while index < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]; pos += 2
            utf[index] = data[pos:pos+size]; pos += size
        else:
            pos += {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            if tag in (5, 6): index += 1
        index += 1
    pos += 6
    interfaces = struct.unpack_from('>H', data, pos)[0]; pos += 2+interfaces*2

    def member():
        nonlocal pos
        start = pos
        name, descriptor, attrs = struct.unpack_from('>HHH', data, pos+2); pos += 8
        for _ in range(attrs):
            size = struct.unpack_from('>I', data, pos+2)[0]; pos += 6+size
        return utf[name], utf[descriptor], data[start:pos]

    fields = struct.unpack_from('>H', data, pos)[0]; pos += 2
    for _ in range(fields): member()
    header = data[:pos]
    methods = struct.unpack_from('>H', data, pos)[0]; pos += 2
    selected = [body for name, descriptor, body in (member() for _ in range(methods))
                if name == b'runServer' and descriptor == b'(ZZ)V']
    assert len(selected) == 1
    return header + b'\x00\x01' + selected[0] + b'\x00\x00'


class PocServerModeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-poc-mode-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        sources = {
            'com/wurmonline/server/Server.java': '''package com.wurmonline.server;
public class Server {
 private static final Server INSTANCE=new Server(); private boolean personal;
 public final com.wurmonline.server.steam.SteamHandler steamHandler=new com.wurmonline.server.steam.SteamHandler();
 public static Server getInstance(){return INSTANCE;}
 public void setIsPS(boolean value){personal=value;}
 public boolean isPS(){return personal;}
 public void startRunning(){throw new AssertionError("PRIVATE_START_BOUNDARY");}
 public void shutDown(String reason,Throwable failure){throw new AssertionError(reason,failure);}
}''',
            'com/wurmonline/server/steam/SteamHandler.java': '''package com.wurmonline.server.steam;
public class SteamHandler {public boolean offline;public void setIsOfflienServer(boolean value){offline=value;}}''',
            'com/wurmonline/server/ServerDirInfo.java': '''package com.wurmonline.server;
public class ServerDirInfo {public static void setPath(java.nio.file.Path p){}}''',
            'com/wurmonline/server/gui/folders/GameFolder.java': '''package com.wurmonline.server.gui.folders;
public class GameFolder {private java.nio.file.Path path;
 public static GameFolder fromPath(java.nio.file.Path path){GameFolder g=new GameFolder();g.path=path;return g;}
 public java.nio.file.Path getPath(){return path;}}''',
            'com/wurmonline/server/gui/folders/Folders.java': '''package com.wurmonline.server.gui.folders;
public class Folders {public static boolean loadDist(){return true;} public static boolean setCurrent(GameFolder g){return true;}}''',
            'com/wurmonline/server/ServerLauncher.java': '''package com.wurmonline.server;
public class ServerLauncher {
 public void runServer(boolean personal,boolean offline) {
  Server s=Server.getInstance();s.setIsPS(personal);s.steamHandler.setIsOfflienServer(offline);
  if(!s.isPS()||!s.steamHandler.offline)throw new AssertionError("WRONG_MODE_AT_START");
  if(Boolean.getBoolean("fixture.loseMode"))s.setIsPS(false);
 }
}''',
            'ModeFixture.java': '''public class ModeFixture {
 public static void main(String[] args) throws Exception {
  if(args[0].equals("poc")) {
   Thread.currentThread().interrupt();
   try{poc.AndroidServerMain.main(new String[]{"Adventure"});throw new AssertionError("keepalive missing");}
   catch(InterruptedException expected){System.out.println("POC_KEEPALIVE_REACHED");}
   return;
  }
  Class<?> type=Class.forName("com.wurmonline.server.ServerLauncher");
  Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
  Object launcher=unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
  for(boolean personal:new boolean[]{false,true})for(boolean offline:new boolean[]{false,true}) {
   var server=com.wurmonline.server.Server.getInstance();server.setIsPS(!personal);server.steamHandler.offline=!offline;
   try{type.getMethod("runServer",boolean.class,boolean.class).invoke(launcher,personal,offline);throw new AssertionError("start boundary missing");}
   catch(java.lang.reflect.InvocationTargetException failure){
    if(!(failure.getCause() instanceof AssertionError)||!"PRIVATE_START_BOUNDARY".equals(failure.getCause().getMessage()))throw failure;
   }
   if(server.isPS()!=personal||server.steamHandler.offline!=offline)throw new AssertionError("original argument contract");
  }
  System.out.println("PRIVATE_LAUNCHER_ARGUMENTS_PASS combinations=4; world not opened");
 }
}'''
        }
        paths = []
        for name, text in sources.items():
            p = cls.root/name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text); paths.append(p)
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.root),
                        *map(str, paths), str(ROOT/'poc/src/poc/AndroidServerMain.java')],
                       check=True, capture_output=True)
        cls.artifact = cls.root/'packaged-poc.jar'
        cls.artifact.write_bytes(base64.b64decode((ROOT/'poc/artifacts/wurm-arm64-poc.jar.base64').read_bytes()))

    def run_fixture(self, prefix=(), mode='poc', props=()):
        return subprocess.run(['java', *props, '-cp', os.pathsep.join(map(str, [*prefix, self.root])),
                               'ModeFixture', mode], capture_output=True, text=True, timeout=10)

    def test_source_and_packaged_poc_retain_personal_offline_mode_and_keepalive(self):
        for prefix in [(), (self.artifact,)]:
            with self.subTest(packaged=bool(prefix)):
                result = self.run_fixture(prefix)
                self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
                self.assertIn('SERVER_MODE_ACTIVE personal=true', result.stdout)
                self.assertIn('POC_KEEPALIVE_REACHED', result.stdout)

    def test_mode_loss_is_reported_before_keepalive(self):
        for prefix in [(), (self.artifact,)]:
            result = self.run_fixture(prefix, props=['-Dfixture.loseMode=true'])
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('SERVER_MODE_ACTIVE personal=false', result.stdout)
            self.assertIn('Personal-server mode was not retained', result.stderr)
            self.assertNotIn('POC_KEEPALIVE_REACHED', result.stdout)

    @unittest.skipUnless(SERVER, 'Optional: legally owned pinned server.jar')
    def test_private_original_launcher_overwrites_both_modes_before_start(self):
        path = Path(SERVER)
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(),
                         '9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06')
        with zipfile.ZipFile(path) as jar:
            original = jar.read('com/wurmonline/server/ServerLauncher.class')
        private = self.root/'private-launcher'; private.mkdir(exist_ok=True)
        target = private/'com/wurmonline/server/ServerLauncher.class'; target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(isolated_run_server(original))
        result = self.run_fixture((private,), mode='private')
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('PRIVATE_LAUNCHER_ARGUMENTS_PASS', result.stdout)


if __name__ == '__main__':
    unittest.main()
