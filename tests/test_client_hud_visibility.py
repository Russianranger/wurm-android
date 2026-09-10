"""Exercise HUD recovery on an authored engine fixture; no game or JavaFX classes."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]


class HudVisibilityTest(unittest.TestCase):
    def test_recovery_is_idempotent_observation_is_read_only_and_startup_is_preserved(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            source=root/'com/wurmonline/client/WurmClientBase.java';source.parent.mkdir(parents=True)
            source.write_text('''package com.wurmonline.client;
public class WurmClientBase {
 private static Thread gameThread=Thread.currentThread();
 private static WurmClientBase clientObject=new WurmClientBase();
 private Object startupRenderer;
 private final Hud hud=new Hud();
 public static class Hud { private boolean visible=false; public void setVisible(boolean v){visible=v;} }
 public static void main(String[] args) throws Exception {
  client.ClientHudVisibility.command("observe");
  if(clientObject.hud.visible) throw new AssertionError("observer mutated HUD");
  clientObject.startupRenderer=new Object();
  client.ClientHudVisibility.command("restore-focus");
  if(clientObject.hud.visible) throw new AssertionError("startup UI overridden");
  clientObject.startupRenderer=null;
  client.ClientHudVisibility.command("restore-focus");
  client.ClientHudVisibility.command("restore-button");
  if(!clientObject.hud.visible) throw new AssertionError("repeated restore toggled HUD off");
  clientObject.hud.visible=false;
  java.util.concurrent.atomic.AtomicReference<Throwable> result=new java.util.concurrent.atomic.AtomicReference<>();
  Thread other=new Thread(() -> {try{client.ClientHudVisibility.command("restore-button");}catch(Throwable t){result.set(t);}});
  other.start();other.join();
  if(!(result.get() instanceof IllegalStateException) || clientObject.hud.visible) throw new AssertionError("wrong thread accepted");
  try{client.ClientHudVisibility.command("toggle");throw new AssertionError();}catch(IllegalArgumentException expected){}
  clientObject=null;client.ClientHudVisibility.command("restore-button");
 }
}''')
            subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',temp,str(source),
                str(ROOT/'runtime-probe/src/client/ClientHudVisibility.java')],check=True)
            result=subprocess.run(['java','-cp',temp,'com.wurmonline.client.WurmClientBase'],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('HUD_STATE visible=false',result.stdout)
            self.assertIn('reason=restore-focus before=false after=true',result.stdout)
            self.assertIn('reason=restore-button before=true after=true',result.stdout)
            self.assertEqual(result.stdout.count('HUD_RESTORE_SKIPPED'),2)
