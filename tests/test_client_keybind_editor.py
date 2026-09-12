"""Live keybind bridge with authored game-thread/catalog fixtures and real atomic file saves."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]

class KeybindEditorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(); cls.home=Path(cls.temp.name)
        sources={
'com/wurmonline/client/options/keybinding/PlayerKeybindCategory.java': '''package com.wurmonline.client.options.keybinding;
public enum PlayerKeybindCategory { MOVEMENT; public String getName(){return "Movement";} }''',
'com/wurmonline/client/options/keybinding/PlayerKeybind.java': '''package com.wurmonline.client.options.keybinding;
public enum PlayerKeybind { MOVE_FORWARD, ACTION, UNBOUND;
 public String getCommand(){return name();} public String getDisplayName(){return "Label "+name();}
 public PlayerKeybindCategory getCategory(){return PlayerKeybindCategory.MOVEMENT;}}
''',
'com/wurmonline/client/options/keybinding/KeybindButtons.java': '''package com.wurmonline.client.options.keybinding;
public enum KeybindButtons { Empty, W, UP, E, RETURN, ENTER;
 public String getCommandName(){return this==Empty ? "" : name();}}
''',
'com/wurmonline/client/WurmClientBase.java': '''package com.wurmonline.client;
public class WurmClientBase {
 private static Thread gameThread=Thread.currentThread(); private static WurmClientBase clientObject=new WurmClientBase();
 public static class Profile { public java.io.File getKeybindsFile(){return com.wurmonline.client.launcherfx.WurmSettingsFX.androidBindingFile();} }
 public static Profile getProfileManager(){return new Profile();}
 public static boolean failReload; public static int reloads;
 private Object startupRenderer; private Hud hud=new Hud();
 public static void loading(){clientObject.startupRenderer=new Object();}
 public static class Hud {
  private Console console=new Console(); public Bar getSelectBar(){return new Bar();} public void updateBinds(boolean value){}
 }
 public static class Bar { public void updateActions(){} }
 public static class Console {
  public void executeKeybinds(){reloads++;if(failReload)throw new IllegalStateException("fixture reload failure");}
  private int translateKeyString(String s){
   String[] p=s.split("[-+]"); String k=p[p.length-1]; int base=switch(k){case "W"->17;case "UP"->200;case "E"->18;case "RETURN","ENTER"->28;default->0;};
   return base+(s.contains("SHIFT")?65536:0)+(s.contains("ALT")?131072:0)+(s.contains("CTRL")?262144:0);
  }
 }
}''',
'Check.java': '''import client.ClientKeybindings; import com.wurmonline.client.launcherfx.WurmSettingsFX; import com.wurmonline.client.WurmClientBase;
import java.nio.file.*;import java.util.*;
public class Check {
 static String id="01234567-0123-0123-0123-012345678901"; static Path response;
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Properties request(String wire) throws Exception {
  ClientKeybindings.command(id+" "+wire); Properties p=new Properties();try(var in=Files.newInputStream(response)){p.load(in);} check(id.equals(p.getProperty("request")));return p;
 }
 static void ok(Properties p){check(p.getProperty("status").equals("ok"));}
 static void bad(Properties p){check(p.getProperty("status").equals("error"));}
 public static void main(String[] args)throws Exception {
  Path dir=Path.of(args[0]), file=dir.resolve("keybindings.txt");response=dir.resolve("reply.properties");
  System.setProperty("wurm.client.keybindReport",response.toString());
  String original="// fixture\\nbind W MOVE_FORWARD\\nbind UP MOVE_FORWARD\\nbind E ACTION\\nbind CTRL-ENTER CUSTOM\\n";
  if(args[1].equals("custom"))original+="exec custom.txt\\n";
  Files.writeString(file,original);WurmSettingsFX.loadAllKeybinds(file.toFile());
  Class.forName("com.wurmonline.client.WurmClientBase");
  Properties p=request("READ");ok(p);String revision=p.getProperty("revision");
  check(p.getProperty("count").equals("4") && p.getProperty("item.2.keys").equals(""));
  switch(args[1]) {
   case "roundtrip":
    p=request("SET "+revision+" 0 W,UP,SHIFT+E");ok(p);check(p.getProperty("notice").contains("applied"));
    check(WurmClientBase.reloads==1);check(p.getProperty("item.1.keys").equals("E"));
    check(Files.readString(dir.resolve("keybindings.txt.android-backup")).equals(original));
    revision=p.getProperty("revision");p=request("SET "+revision+" 0 -");ok(p);check(p.getProperty("item.0.keys").equals(""));
    WurmSettingsFX.loadAllKeybinds(file.toFile());check(WurmSettingsFX.getKeybind("MOVE_FORWARD")==null);break;
   case "conflict":
    bad(request("SET "+revision+" 0 E"));check(Files.readString(file).equals(original));
    bad(request("SET "+revision+" 0 CTRL+RETURN"));check(Files.readString(file).equals(original));
    ok(request("SET "+revision+" 2 SHIFT+E"));String changed=Files.readString(file);
    bad(request("SET "+revision+" 0 -"));check(Files.readString(file).equals(changed));break;
   case "external":
    Files.writeString(file,original+"// external edit\\n");bad(request("SET "+revision+" 0 -"));
    check(Files.readString(file).endsWith("// external edit\\n"));break;
   case "custom":
    check(p.getProperty("editable").equals("false"));bad(request("SET "+revision+" 0 -"));check(Files.readString(file).equals(original));break;
   case "loading":
    WurmClientBase.loading();bad(request("SET "+revision+" 0 -"));check(Files.readString(file).equals(original));break;
   case "reload":
    WurmClientBase.failReload=true;p=request("SET "+revision+" 2 SHIFT+E");ok(p);check(p.getProperty("notice").contains("Restart"));check(Files.readString(file).contains("SHIFT+E UNBOUND"));break;
   case "invalid":
    for(String key:List.of("UNKNOWN","W,W","CTRL+CTRL+W","SHIFT+CTRL+W,CTRL+SHIFT+W"))bad(request("SET "+revision+" 0 "+key));
    check(Files.readString(file).equals(original));break;
   case "thread":
    Thread t=new Thread(()->ClientKeybindings.command(id+" SET "+pRevision+" 0 -"));t.start();t.join();
    Properties reply=new Properties();try(var in=Files.newInputStream(response)){reply.load(in);}bad(reply);check(Files.readString(file).equals(original));break;
  }
 }
 static String pRevision="a".repeat(64);
}'''}
        files=[]
        for name,content in sources.items():
            file=cls.home/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content);files.append(file)
        files += [ROOT/'runtime-probe/src/client/ClientKeybindings.java', ROOT/'client-compat/src/wurm/android/compat/KeybindStore.java',ROOT/'client-compat/src/com/wurmonline/client/launcherfx/WurmSettingsFX.java']
        files += list((ROOT/'client-compat/stubs').rglob('*.java'))
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.home),*map(str,files)],check=True)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def case(self,name):
        directory=Path(tempfile.mkdtemp(dir=self.home))
        r=subprocess.run(['java','-cp',str(self.home),'Check',str(directory),name],capture_output=True,text=True,timeout=20)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertFalse(list(directory.glob('*.pending')))
    def test_live_edit_clear_backup_and_reload(self):self.case('roundtrip')
    def test_conflicts_aliases_and_stale_revision(self):self.case('conflict')
    def test_external_changes_preserved(self):self.case('external')
    def test_custom_console_file_preserved(self):self.case('custom')
    def test_loading_rejected_before_save(self):self.case('loading')
    def test_reload_failure_reports_saved_restart_needed(self):self.case('reload')
    def test_invalid_and_duplicate_keys_rejected(self):self.case('invalid')
    def test_wrong_thread_rejected_before_save(self):self.case('thread')
