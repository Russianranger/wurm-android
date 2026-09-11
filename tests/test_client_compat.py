"""Executable compatibility contracts. All fixtures are handwritten, not game code."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = {
    'com/wurmonline/client/steam/SteamAuthTicket.java': '''package com.wurmonline.client.steam;
public class SteamAuthTicket {
 private final long handle, length; private final byte[] bytes;
 SteamAuthTicket(long h, byte[] b, long n) { handle=h; bytes=b; length=n; }
 public long getAuthTicket() { return handle; }
 public byte[] getTicketArray() { return bytes; }
 public long getTokenLen() { return length; }
}''',
    'com/wurmonline/client/steam/SteamHandler.java': '''package com.wurmonline.client.steam;
public class SteamHandler {
 public enum SteamInitializeResults { InitSuccess, SteamApiFailed }
 private final SteamJni.Steam_api api = new SteamJni.Steam_api(this);
 private SteamAuthTicket ticket;
 public SteamInitializeResults initializeSteam() {
  if(api.SteamAPI_RestartAppIfNecessary(366220) || !api.SteamAPI_Init()) return SteamInitializeResults.SteamApiFailed;
  api.CreateCallback(); api.RequestCurrentStats(); return SteamInitializeResults.InitSuccess;
 }
 public void requestAuthTicket() { ticket=api.GetAuthSessionTicket(); }
 public SteamAuthTicket getAuthTicket() { return ticket; }
 public void shutdownSteam() { api.SteamAPI_ShutDown(); }
 // Broad reflection would load an intentionally absent desktop dependency.
 public void unusedDesktopBrowser(MissingFx value) {}
}''',
    'com/wurmonline/client/steam/MissingFx.java': 'package com.wurmonline.client.steam; public class MissingFx {}',
    'com/wurmonline/client/console/ConsoleListenerClass.java': '''package com.wurmonline.client.console;
public interface ConsoleListenerClass { void consoleOutput(String text); void consoleClosed(); }''',
    'com/wurmonline/client/console/WurmConsoleOutputStream.java': '''package com.wurmonline.client.console;
public class WurmConsoleOutputStream extends java.io.PrintStream {
 public WurmConsoleOutputStream(java.io.PrintStream out) { super(out); }
 public void addCopy(ConsoleListenerClass listener) {}
}''',
    'com/wurmonline/client/settings/Profile.java': '''package com.wurmonline.client.settings;
public class Profile {
 public static class PlayerProfile { public final String name; PlayerProfile(String n) { name=n; } }
 private String player;
 private static final Profile instance=new Profile();
 private static boolean initialized;
 public static Profile getProfile() {
  if(!initialized) { initialized=true; com.wurmonline.client.launcherfx.WurmSettingsFX.loadAllKeybinds(new java.io.File(instance.getConfigDir(),"keybindings.txt")); }
  return instance;
 }
 public java.io.File getConfigDir() { return new java.io.File("configs/default"); }
 public java.io.File getPlayerDir() { return new java.io.File("players/"+player); }
 public void loadPlayer(String name) { player=name; }
 public void associateConfig() {}
 public void storeConfig() { com.wurmonline.client.launcherfx.WurmSettingsFX.saveAllKeybinds(); }
 public PlayerProfile launchProfile() { return new PlayerProfile(player); }
}''',
    'com/wurmonline/client/settings/GlobalData.java': '''package com.wurmonline.client.settings;
public class GlobalData { public static java.io.File getPackDirectory() { return new java.io.File("packs"); } }''',
    'com/wurmonline/client/options/Options.java': '''package com.wurmonline.client.options;
public class Options {
 public static MultiOption keybindingsSource = new MultiOption();
 public static DisplayOption screenSettings = new DisplayOption();
 public static void checkOptionsVersion() {}
}''',
    'com/wurmonline/client/options/DisplayOption.java': '''package com.wurmonline.client.options;
public class DisplayOption {
 public boolean maximized=true, fullscreen, resizable=true;
 public int width=1024, height=768, hz=-1;
 public void set(boolean m, int w, int h, int r, boolean f, boolean s) {
  maximized=m; width=w; height=h; hz=r; fullscreen=f; resizable=s;
  com.wurmonline.client.WurmClientBase.setWindowDirty(true);
 }
}''',
    'com/wurmonline/client/options/MultiOption.java': 'package com.wurmonline.client.options; public class MultiOption { public int value() { return 0; } }',
    'com/wurmonline/client/resources/Resources.java': '''package com.wurmonline.client.resources;
public class Resources { public final java.util.List<String> packs;
 public Resources(java.io.File dir, java.util.List<String> names) { packs=names; } }''',
    'com/wurmonline/client/WurmClientBase.java': '''package com.wurmonline.client;
import com.wurmonline.client.settings.Profile.PlayerProfile;
import com.wurmonline.client.resources.Resources;
public class WurmClientBase {
 public static com.wurmonline.client.steam.SteamHandler steamHandler;
 private static Thread gameThread; private static String username, password; private static boolean windowDirty;
 public static void setUsername(String name) { username=name; }
 public static void setPassword(String value) { password=value; }
 public static void setServerPassword(String value) { if(!value.isEmpty()) throw new AssertionError("server password"); }
 public static void setWindowDirty(boolean dirty) { windowDirty=dirty; }
 public static void launch(PlayerProfile profile, Resources resources, boolean option) {
  if(option || steamHandler==null || !profile.name.equals(username) || !resources.packs.contains("graphics.jar")) throw new AssertionError("launch contract");
  String identity=new SteamJni.Steam_api(steamHandler).GetCSteamIDString();
  if(password==null || password.isEmpty() || !password.equals(identity)) throw new AssertionError("local login credential mismatch");
  steamHandler.requestAuthTicket();
  String ticket=new String(steamHandler.getAuthTicket().getTicketArray(),java.nio.charset.StandardCharsets.US_ASCII);
  if(!ticket.equals("WURM_ANDROID_LOCAL_V1:"+password)) throw new AssertionError("ticket identity mismatch");
  System.out.println("FIXTURE_LOCAL_LOGIN_CREDENTIAL_PASS");
  var screen=com.wurmonline.client.options.Options.screenSettings;
  if(windowDirty || screen.maximized || screen.fullscreen || screen.resizable || screen.width!=Integer.getInteger("fixture.width",960) || screen.height!=Integer.getInteger("fixture.height",540) || screen.hz!=-1) throw new AssertionError("headless viewport");
  if(!com.wurmonline.client.launcherfx.WurmMain.getServerIp().equals("127.0.0.1") || com.wurmonline.client.launcherfx.WurmMain.getServerPort()!=3724) throw new AssertionError("target");
  String[] icons=com.wurmonline.client.launcherfx.WurmStage.getIconNames();
  if(icons.length!=4 || !icons[0].equals("/icon2_128.png") || !icons[3].equals("/icon2_16.png")) throw new AssertionError("icons");
  gameThread=new Thread(() -> {
   try { Thread.sleep(200); } catch(InterruptedException e) { throw new AssertionError(e); }
   if(Boolean.getBoolean("fixture.crash")) throw new UnsatisfiedLinkError("FIXTURE_ASYNC_NATIVE_FAILURE");
   if(Boolean.getBoolean("fixture.reported")) com.wurmonline.client.ErrorReporterPanel.crashed(new UnsatisfiedLinkError("FIXTURE_REPORTED_NATIVE_FAILURE"), "startup");
   System.out.println("FIXTURE_GAME_THREAD_FINISHED");
  }, "fixture-game");
  gameThread.start();
 }
}''',
}


@unittest.skipUnless(shutil.which('java'), 'JDK 17 required')
class ClientCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        cls.compat_classes = cls.home/'compat'
        cls.helper = cls.home/'helper'
        cls.compile(cls.compat_classes, list((ROOT/'client-compat').rglob('*.java')))
        cls.compile(cls.helper, list((ROOT/'runtime-probe/src/client').glob('*.java')) + [ROOT/'runtime-probe/src/probe/RuntimeMeasurements.java'])
        cls.compat = cls.home/'client-compat.jar'
        with zipfile.ZipFile(cls.compat, 'w') as jar:
            for path in cls.compat_classes.rglob('*.class'):
                name = path.relative_to(cls.compat_classes).as_posix()
                if name.startswith(('SteamJni/', 'wurm/android/compat/', 'com/wurmonline/client/launcherfx/')) or name == 'com/wurmonline/client/ErrorReporterPanel.class':
                    jar.write(path, name)
        sources = cls.home/'source'
        for name, text in FIXTURES.items():
            path = sources/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        classes = cls.home/'fixture-classes'
        cls.compile(classes, list(sources.rglob('*.java')), str(cls.compat))
        cls.fixture = cls.home/'fixture.jar'
        with zipfile.ZipFile(cls.fixture, 'w') as jar:
            for path in classes.rglob('*.class'):
                if path.name != 'MissingFx.class':
                    jar.write(path, path.relative_to(classes).as_posix())
            jar.writestr('com/wurmonline/client/client.properties', 'release-mode=true\ntest-client=false\nuses-test-server=false\n')

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @staticmethod
    def compile(dest, sources, cp=None):
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(dest),
                        *(['-cp', cp] if cp else []), *map(str, sources)], check=True)

    def workspace(self):
        home = Path(tempfile.mkdtemp(dir=self.home))
        (home/'packs').mkdir()
        (home/'configs/default').mkdir(parents=True)
        (home/'configs/default/keybindings.txt').write_text('bind w fixture_forward\nbind up fixture_forward\n')
        for name in ['graphics.jar', 'sound.jar', 'pmk.jar', 'test_graphics.jar']:
            with zipfile.ZipFile(home/'packs'/name, 'w') as jar:
                jar.writestr('fixture-resource.txt', 'handwritten test resource')
        return home

    def run_mode(self, home, mode, properties=None):
        values = {'java.awt.headless': 'true', 'user.home': str(home/'user'),
                  'wurm.client.offline': 'true', 'wurm.client.host': '127.0.0.1', 'wurm.client.port': '3724'}
        values.update(properties or {})
        return subprocess.run(['java', *[f'-D{k}={v}' for k, v in values.items()], '-cp',
                               os.pathsep.join(map(str, [self.compat, self.helper, self.fixture])),
                               'client.ClientBootstrap', mode], cwd=home, capture_output=True, text=True, timeout=15)

    def test_typed_contract_does_not_resolve_unused_desktop_types(self):
        home = self.workspace()
        result = self.run_mode(home, 'compat')
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('STEAM_COMPAT_OK importedHandler=true importedTicket=true', result.stdout)
        identity = (home/'user/wurm-local-identity.txt').read_text()
        again = self.run_mode(home, 'compat')
        self.assertEqual(again.returncode, 0, again.stdout)
        self.assertEqual((home/'user/wurm-local-identity.txt').read_text(), identity)
        self.assertNotIn(identity, result.stdout)

    def test_local_shim_refuses_nonlocal_target_and_missing_opt_in(self):
        for props in [{'wurm.client.host': '192.0.2.1'}, {'wurm.client.port': '3725'}, {'wurm.client.offline': 'false'}]:
            with self.subTest(properties=props):
                result = self.run_mode(self.workspace(), 'compat', props)
                self.assertEqual(result.returncode, 42)
                self.assertIn('LOCAL_SHIM_REQUIRES', result.stdout)

    def test_direct_launch_receives_profile_assets_target_and_waits_for_game_thread(self):
        result = self.run_mode(self.workspace(), 'entry', {'wurm.client.player': 'Thortest'})
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('PROFILE_READY', result.stdout)
        self.assertIn('WINDOW_HELPER headless-icons-v1', result.stdout)
        self.assertIn('WINDOW_HELPER headless-errors-v1', result.stdout)
        self.assertIn('ICON_RESOURCES source=imported-client javafx=false', result.stdout)
        self.assertIn('WINDOW_OPTIONS_ANDROID width=960 height=540 maximized=false', result.stdout)
        self.assertIn('SETTINGS_ADAPTER headless-keybinds-v1', result.stdout)
        self.assertIn('KEYBINDS_LOADED actions=1 keys=2', result.stdout)
        self.assertIn('KEYBINDS_PRESERVED unchanged=true', result.stdout)
        self.assertIn('RESOURCE_PACKS_VALIDATED [sound.jar, pmk.jar, graphics.jar]', result.stdout)
        self.assertIn('FIXTURE_GAME_THREAD_FINISHED', result.stdout)
        self.assertIn('FIXTURE_LOCAL_LOGIN_CREDENTIAL_PASS', result.stdout)
        self.assertLess(result.stdout.index('FIXTURE_GAME_THREAD_FINISHED'), result.stdout.index('BOOTSTRAP_EXIT'))

    def test_720p_launch_sets_real_offscreen_size_without_desktop_fullscreen(self):
        result=self.run_mode(self.workspace(),'entry',{'wurm.client.player':'Thorhd','wurm.client.resolution':'1280x720',
                                                     'fixture.width':'1280','fixture.height':'720'})
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('WINDOW_OPTIONS_ANDROID width=1280 height=720 maximized=false fullscreen=false',result.stdout)
        result=self.run_mode(self.workspace(),'entry',{'wurm.client.resolution':'1920x1080'})
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Unsupported Android resolution',result.stdout)

    def test_direct_login_reuses_persisted_ticket_identity_without_logging_it(self):
        home = self.workspace()
        identity_path = home/'user/wurm-local-identity.txt'
        identity = None
        for player in ['Thor', 'Thor', 'Thortest']:
            result = self.run_mode(home, 'entry', {'wurm.client.player': player})
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
            self.assertIn('FIXTURE_LOCAL_LOGIN_CREDENTIAL_PASS', result.stdout)
            self.assertIn('LOGIN_CREDENTIAL_READY source=persisted-local-identity', result.stdout)
            current = identity_path.read_text().strip()
            self.assertRegex(current, r'^7656119[0-9]{10}$')
            if identity is not None:
                self.assertEqual(current, identity)
            identity = current
            self.assertNotIn(identity, result.stdout+result.stderr)

    def test_direct_login_refuses_nonlocal_scope_and_corrupt_identity(self):
        for properties in [{'wurm.client.host': '192.0.2.1'}, {'wurm.client.port': '3725'},
                           {'wurm.client.offline': 'false'}, {}]:
            with self.subTest(properties=properties):
                home = self.workspace()
                identity = home/'user/wurm-local-identity.txt'
                if not properties:
                    identity.parent.mkdir(); identity.write_text('broken')
                result = self.run_mode(home, 'entry', properties)
                self.assertEqual(result.returncode, 42, result.stdout+result.stderr)
                self.assertNotIn('LOGIN_CREDENTIAL_READY', result.stdout)
                self.assertNotIn('ENTRY_INVOKE', result.stdout)
                if not properties:
                    self.assertEqual(identity.read_text(), 'broken')

    def test_async_native_crash_is_reported_instead_of_successful_launch_return(self):
        result = self.run_mode(self.workspace(), 'entry', {'fixture.crash': 'true'})
        self.assertEqual(result.returncode, 42)
        self.assertIn('CLIENT_THREAD_FAILED', result.stdout)
        self.assertIn('FIXTURE_ASYNC_NATIVE_FAILURE', result.stdout)
        self.assertIn('CLIENT_ASYNC_STARTUP_FAILED', result.stdout)

    def test_missing_or_corrupt_packs_do_not_claim_resources_ready(self):
        for corrupt in [False, True]:
            home = self.workspace()
            if corrupt:
                (home/'packs/graphics.jar').write_text('invalid ZIP')
            else:
                shutil.rmtree(home/'packs')
            result = self.run_mode(home, 'entry')
            self.assertEqual(result.returncode, 42)
            self.assertNotIn('RESOURCES_READY', result.stdout)
            self.assertNotIn('ENTRY_INVOKE', result.stdout)

    def test_reported_game_failure_is_not_success_when_game_thread_returns(self):
        result = self.run_mode(self.workspace(), 'entry', {'fixture.reported': 'true'})
        self.assertEqual(result.returncode, 42, result.stdout+result.stderr)
        self.assertIn('CLIENT_REPORTED_FAILURE kind=crash', result.stdout)
        self.assertIn('CLIENT_REPORTED_STARTUP_FAILED', result.stdout)
        first = next(line for line in result.stdout.splitlines() if 'BOOTSTRAP_FAILED' in line)
        self.assertIn('root=java.lang.UnsatisfiedLinkError reason=FIXTURE_REPORTED_NATIVE_FAILURE', first)

    def test_corrupt_identity_is_not_silently_replaced(self):
        home = self.workspace()
        (home/'user').mkdir()
        identity = home/'user/wurm-local-identity.txt'
        identity.write_text('broken')
        result = self.run_mode(home, 'compat')
        self.assertEqual(result.returncode, 42)
        self.assertIn('LOCAL_IDENTITY_FAILED', result.stdout)
        self.assertEqual(identity.read_text(), 'broken')

    def test_no_signature_stubs_in_compat_jar_or_helper(self):
        with zipfile.ZipFile(self.compat) as jar:
            self.assertEqual(set(jar.namelist()), {'SteamJni/Steam_api.class', 'wurm/android/compat/LocalSession.class',
                'wurm/android/compat/ClientHooks.class', 'com/wurmonline/client/launcherfx/WurmMain.class',
                'com/wurmonline/client/launcherfx/WurmMain$1.class', 'com/wurmonline/client/launcherfx/WurmSettingsFX.class',
                'com/wurmonline/client/launcherfx/WurmStage.class', 'com/wurmonline/client/ErrorReporterPanel.class',
                'wurm/android/compat/KeybindStore.class', 'wurm/android/compat/SettingsDispatch.class'})
        self.assertFalse((self.helper/'SteamJni').exists())
        self.assertFalse((self.helper/'com/wurmonline').exists())


if __name__ == '__main__':
    unittest.main()
