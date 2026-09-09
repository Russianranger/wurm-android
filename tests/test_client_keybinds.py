"""Headless binding storage contracts. Authored fixtures; no game or JavaFX."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which('java'), 'Java 17 required')
class ClientKeybindTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        source = cls.home/'Check.java'
        source.write_text('''import wurm.android.compat.KeybindStore;
import java.nio.file.*; import java.util.*;
public class Check {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  Path file=Path.of(args[0]); String mode=args[1];
  String original="# fixture comment\\r\\nbind w move_forward\\r\\nbind up move_forward\\r\\nbind e action\\r\\n";
  Files.writeString(file,original); KeybindStore store=new KeybindStore();store.load(file);
  switch(mode) {
   case "preserve":
    check(store.keyCount()==3);check("W".equals(store.get("MOVE_FORWARD",0)));check("UP".equals(store.get("move_forward",1)));
    check(store.get("absent",0)==null && store.get("action",5)==null);
    store.save(file);check(original.equals(Files.readString(file)));check(!Files.exists(file.resolveSibling(file.getFileName()+".android-backup")));break;
   case "roundtrip":
    store.add("jump","w",true);check("UP".equals(store.get("move_forward",0)));check("JUMP".equals(store.owner("w")));
    store.add("jump","space",false);store.save(file);
    check(original.equals(Files.readString(file.resolveSibling(file.getFileName()+".android-backup"))));
    KeybindStore again=new KeybindStore();again.load(file);check("W".equals(again.get("jump",0)));check("SPACE".equals(again.get("jump",1)));
    check(again.remove("jump"));check("SPACE".equals(again.get("jump",0)));again.save(file);store.load(file);check("SPACE".equals(store.get("jump",0)));break;
   case "conflict":
    store.add("jump","space",false);Files.writeString(file,"external change");
    try { store.save(file); throw new AssertionError("overwrite"); }catch(java.io.IOException expected) {check(expected.getMessage().contains("FILE_CHANGED"));}
    check("external change".equals(Files.readString(file)));break;
   case "quoted":
    Files.writeString(file,"bind x say \\\"MiXeD; text\\\"; bind y other\\n");store.load(file);
    check("X".equals(store.get("say \\\"MiXeD; text\\\"",0)));check("Y".equals(store.get("OTHER",0)));
    store.add("jump","space",false);store.save(file);store.load(file);check("X".equals(store.get("say \\\"MiXeD; text\\\"",0)));break;
   case "unknown":
    String custom="bind w move_forward\\nexec custom.txt\\n";Files.writeString(file,custom);store.load(file);store.save(file);check(custom.equals(Files.readString(file)));
    store.add("jump","space",false);
    try {store.save(file);throw new AssertionError();} catch(java.io.IOException expected) {check(expected.getMessage().contains("CUSTOM_COMMANDS"));}
    check(custom.equals(Files.readString(file)));break;
   case "malformed":
    for(String invalid:List.of("bind w", "bind\\tw", "bind a say \\\"open", "bind space\\n")) {
     Files.writeString(file,invalid);
     try {store.load(file);throw new AssertionError("invalid accepted");} catch(java.io.IOException | IllegalArgumentException expected) {}
     check("W".equals(store.get("move_forward",0)));check(invalid.equals(Files.readString(file)));
    } break;
   case "bounds":
    Files.write(file,new byte[1024*1024+1]);
    try {store.load(file);throw new AssertionError();} catch(java.io.IOException expected) {}
    check("W".equals(store.get("move_forward",0)));
    try {store.add("jump\\nexec x","x",false);throw new AssertionError();} catch(IllegalArgumentException expected) {}
    try {store.add("jump;exec x","x",false);throw new AssertionError();} catch(IllegalArgumentException expected) {}
    break;
   default: throw new AssertionError(mode);
  }
 }
}''')
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.home), str(source),
                        str(ROOT/'client-compat/src/wurm/android/compat/KeybindStore.java')], check=True)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_case(self, name):
        directory = Path(tempfile.mkdtemp(dir=self.home))
        result = subprocess.run(['java', '-cp', str(self.home), 'Check', str(directory/'keybindings.txt'), name],
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertFalse(list(directory.glob('*.pending')))

    def test_unchanged_file_is_byte_identical(self): self.run_case('preserve')
    def test_remap_secondary_unbind_atomic_backup_and_reload(self): self.run_case('roundtrip')
    def test_external_edit_is_not_overwritten(self): self.run_case('conflict')
    def test_quoted_text_and_multiple_bindings_are_preserved(self): self.run_case('quoted')
    def test_other_console_commands_are_not_executed_or_rewritten(self): self.run_case('unknown')
    def test_failed_parse_leaves_previous_bindings_intact(self): self.run_case('malformed')
    def test_input_bounds_and_newline_injection(self): self.run_case('bounds')
