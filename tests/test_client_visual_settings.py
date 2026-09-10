"""Execute owned settings callbacks and reversible presets without game or JavaFX binaries."""
from pathlib import Path
import shutil
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientVisualSettingsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        cls.classes = cls.home/'classes'
        sources = {
            'javafx/application/Platform.java': 'package javafx.application; public class Platform { public static void runLater(Runnable r) { r.run(); } }',
            'javafx/event/ActionEvent.java': 'package javafx.event; public class ActionEvent {}',
            'com/wurmonline/client/launcherfx/WurmSettingsFX.java': '''package com.wurmonline.client.launcherfx;
public class WurmSettingsFX {
 public static WurmSettingsFX getInstance(boolean b) { return null; }
 public void show() {} public void restart() {} public void restartAndClose(javafx.event.ActionEvent e) {}
}''',
            'fixture/Hud.java': '''package fixture;
import com.wurmonline.client.launcherfx.WurmSettingsFX;
public class Hud {
 public static void open() { javafx.application.Platform.runLater(() -> { WurmSettingsFX.getInstance(false).restart(); WurmSettingsFX.getInstance(false).show(); }); }
 public static void close() { javafx.application.Platform.runLater(() -> WurmSettingsFX.getInstance(false).restartAndClose(null)); }
}''',
        }
        cls.compile(sources)
        # Replace only the desktop signature fixture with the owned production adapter.
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),
                        *map(str,(ROOT/'client-compat').rglob('*.java'))],check=True)
        shutil.rmtree(cls.classes/'javafx')
        cls.compile({
            'com/wurmonline/client/options/Options.java': '''package com.wurmonline.client.options;
public class Options {
 public static class Multi {
  public String[] options; public int v; public boolean fail;
  public Multi(int v,String... labels) { this.v=v; options=labels; }
  public int value() { return v; }
  public void set(int n) { if(fail) { fail=false; throw new IllegalStateException("fixture failure"); } v=n; }
 }
 public static class Bool { public boolean v=true; public boolean value(){return v;} public void set(boolean b){v=b;} }
 public static Multi waterDetail=new Multi(2,"Low","Medium","High"), reflections=new Multi(3,"Disabled","Sky","Sky & Terrain","Sky, Terrain & Trees","Almost Everything"),
  treeRenderingDistance=new Multi(4,"Very Short","Short","Medium","Far","Extreme"),
  structureRenderingDistance=new Multi(3,"Very Short","Short","Medium","Far","Extreme"),
  itemCreatureRenderingDistance=new Multi(2,"Very Short","Short","Medium","Far","Extreme");
 public static Bool prettyTrees=new Bool(),prettyWeather=new Bool(),renderSunGlare=new Bool();
 public static Multi caveDetail=new Multi(2,"Low","Medium","High"),
  shadowLevel=new Multi(3,"Disabled","Simple Objects","Objects","Objects & Structures","Everything"),
  shadowMapSize=new Multi(2,"Small","Medium","Large","Huge"),lod=new Multi(1,"Short","Normal","Far"),maxDynamicLights=new Multi(8);
 public static Bool useBloom=new Bool(),useVignette=new Bool(),useFXAA=new Bool(),limitDynamicLights=new Bool();
 public static String extra() {return caveDetail.v+","+shadowLevel.v+","+shadowMapSize.v+","+lod.v+","+useBloom.v+","+useVignette.v+","+useFXAA.v+","+limitDynamicLights.v+","+maxDynamicLights.v;}
 public static String state() {return waterDetail.v+","+reflections.v+","+treeRenderingDistance.v+","+structureRenderingDistance.v+","+itemCreatureRenderingDistance.v+","+prettyTrees.v+","+prettyWeather.v+","+renderSunGlare.v;}
}''',
            'client/SettingsCheck.java': '''package client;
import java.nio.file.*; import com.wurmonline.client.options.Options;
import com.wurmonline.client.launcherfx.WurmSettingsFX;
public class SettingsCheck {
 static void check(boolean b) {if(!b) throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  if(args[0].equals("bridge")) {
   byte[] original=Files.readAllBytes(Path.of(args[1]));
   class Loader extends ClassLoader { Class<?> load(byte[] b) { return defineClass(null,b,0,b.length); } }
   try { new Loader().load(original).getMethod("open").invoke(null); throw new AssertionError("JavaFX unexpectedly present"); }
   catch(java.lang.reflect.InvocationTargetException expected) {check(expected.getCause() instanceof NoClassDefFoundError);}
   byte[] patched=ClientGraphicsPatch.redirect(original,ClientGraphicsPatch.sha(original),"javafx/application/Platform","wurm/android/compat/SettingsDispatch");
   patched=ClientGraphicsPatch.redirect(patched,ClientGraphicsPatch.sha(patched),"(Ljavafx/event/ActionEvent;)V","(Ljava/lang/Object;)V");
   Class<?> hud=new Loader().load(patched);
   for(int i=0;i<2;i++) {
    hud.getMethod("open").invoke(null); check(WurmSettingsFX.getInstance(false).closeSettings);
    hud.getMethod("close").invoke(null); check(!WurmSettingsFX.getInstance(false).closeSettings);
   }
   try {ClientSettingsPatch.prepare(original);throw new AssertionError("uninspected HUD accepted");} catch(java.io.IOException expected) {}
   return;
  }
  String baseline=Options.state(), extra=Options.extra();
  if(args[0].equals("custom")) {
   ClientVisualOptions.apply("performance:2,1,-1,2,0,1,-1,0,0,0,1,0,0,0,0,1,4");
   check(Options.state().equals("2,1,1,2,0,true,false,false"));
   check(Options.extra().equals("0,0,1,0,false,false,false,true,4"));
   ClientVisualOptions.apply("performance"); check(extra.equals(Options.extra()));
   ClientVisualOptions.apply("imported"); check(baseline.equals(Options.state()) && extra.equals(Options.extra()));
   return;
  }
  if(args[0].equals("invalid")) {
   String all="2,1,1,2,0,1,0,0,0,0,1,0,0,0,0,1,";
   for(String command:new String[]{"performance:","imported:0","unknown:"+all+"4","performance:"+all+"0","performance:"+all+"17","performance:"+all+"-2","performance:"+all+"NaN","performance:"+all+"4:extra","performance:3,1,1,2,0,1,0,0,0,0,1,0,0,0,0,1,4","imported:"+all+"4,"}) {
    try {ClientVisualOptions.apply(command);throw new AssertionError(command);}catch(IllegalArgumentException expected) {}
    check(baseline.equals(Options.state()) && extra.equals(Options.extra()));
   }
   return;
  }
  if(args[0].equals("late-abi")) {
   Options.shadowLevel.options[4]="Changed";
   try {ClientVisualOptions.apply("performance");throw new AssertionError();}catch(IllegalStateException expected) {}
   check(baseline.equals(Options.state()) && extra.equals(Options.extra())); return;
  }
  if(args[0].equals("late-rollback")) {
   Options.maxDynamicLights.fail=true;
   try {ClientVisualOptions.apply("performance:2,1,1,2,0,1,0,0,0,0,1,0,0,0,0,1,4");throw new AssertionError();}catch(java.lang.reflect.InvocationTargetException expected) {}
   check(baseline.equals(Options.state()) && extra.equals(Options.extra())); return;
  }
  if(args[0].equals("abi")) {
   Options.structureRenderingDistance.options[1]="Changed";
   try {ClientVisualOptions.apply("performance");throw new AssertionError();}catch(IllegalStateException expected) {}
   check(baseline.equals(Options.state())); return;
  }
  if(args[0].equals("rollback")) {
   Options.treeRenderingDistance.fail=true;
   try {ClientVisualOptions.apply("performance");throw new AssertionError();}catch(java.lang.reflect.InvocationTargetException expected) {}
   check(baseline.equals(Options.state())); return;
  }
  try {ClientVisualOptions.apply("unknown");throw new AssertionError();}catch(IllegalArgumentException expected) {}
  check(baseline.equals(Options.state()));
  ClientVisualOptions.apply("performance"); check(Options.state().equals("0,0,1,1,1,false,false,false"));
  ClientVisualOptions.apply("imported"); check(baseline.equals(Options.state()));
  ClientVisualOptions.apply("performance"); ClientVisualOptions.apply("imported"); check(baseline.equals(Options.state()));
 }
}'''
        }, list((ROOT/'runtime-probe/src/client').glob('*.java')))

    @classmethod
    def compile(cls, sources, extra=()):
        paths=[]
        for name, text in sources.items():
            p=cls.home/'source'/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text); paths.append(p)
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',str(cls.classes),'-d',str(cls.classes),
                        *map(str,paths),*map(str,extra)],check=True)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_case(self, name):
        result=subprocess.run(['java','-cp',str(self.classes),'client.SettingsCheck',name,str(self.classes/'fixture/Hud.class')],
                              capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        return result.stdout

    def test_desktop_open_and_close_callbacks_work_repeatedly_without_javafx(self):
        self.assertEqual(self.run_case('bridge').count('[client-ui] OPEN_GRAPHICS_SETTINGS'),2)

    def test_custom_values_inherit_base_and_restore_full_imported_profile(self): self.run_case('custom')
    def test_invalid_commands_are_rejected_before_any_option_changes(self): self.run_case('invalid')
    def test_new_option_abi_is_checked_before_changing_legacy_options(self): self.run_case('late-abi')
    def test_last_setter_failure_restores_all_prior_options(self): self.run_case('late-rollback')
    def test_android_and_jvm_option_wire_order_matches(self):
        android=(ROOT/'app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsOptions.kt').read_text()
        jvm=(ROOT/'runtime-probe/src/client/ClientVisualOptions.java').read_text()
        self.assertEqual(re.findall(r'Option\("(\w+)"',android),re.findall(r'new Spec\("(\w+)"',jvm))

    def test_live_preset_restores_exact_imported_values(self): self.run_case('preset')
    def test_unknown_option_labels_do_not_partially_change_graphics(self): self.run_case('abi')
    def test_failing_setter_rolls_back_previous_changes(self): self.run_case('rollback')


class FramePacerTest(unittest.TestCase):
    def test_targets_account_for_work_and_do_not_burst_after_stalls(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'PacerCheck.java'
            source.write_text('''import wurm.graphics.FramePacer;
public class PacerCheck {
 static void eq(long a,long b) { if(a!=b) throw new AssertionError(a+" != "+b); }
 public static void main(String[] ignored) {
  FramePacer p=new FramePacer(30); long interval=1_000_000_000L/30, now=1_000_000_000L;
  eq(p.delay(now),0); p.presented(now);
  eq(p.delay(now+10_000_000L),interval-10_000_000L);
  now+=interval; p.presented(now); eq(p.delay(now+20_000_000L),interval-20_000_000L);
  now+=10*interval; eq(p.delay(now),0); p.presented(now); eq(p.delay(now),interval);
  p.setFps(15); eq(p.delay(now),0); p.presented(now); eq(p.delay(now+20_000_000L),1_000_000_000L/15-20_000_000L);
  try {p.setFps(60);throw new AssertionError();}catch(IllegalArgumentException expected) {}
  eq(p.fps(),15);
 }
}''')
            subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',temp,str(source),
                            str(ROOT/'graphics-compat/window/wurm/graphics/FramePacer.java')],check=True)
            subprocess.run(['java','-cp',temp,'PacerCheck'],check=True,timeout=15)
