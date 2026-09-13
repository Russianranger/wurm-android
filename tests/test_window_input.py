"""Run authored input routing against the LWJGL queue boundary, without a GPU."""
from pathlib import Path
import shutil
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(shutil.which("java"), "Java 17 required")
class WindowInputTest(unittest.TestCase):
    def test_position_buttons_controller_deltas_reset_and_validation(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            sink = home / "org/lwjgl/input/GLFWInputImplementation.java"
            sink.parent.mkdir(parents=True)
            sink.write_text("""package org.lwjgl.input;
import java.util.*;
public class GLFWInputImplementation {
 public static final GLFWInputImplementation singleton = new GLFWInputImplementation();
 public boolean grab;
 public final List<String> events = new ArrayList<>();
 public final List<String> text = new ArrayList<>();
 public boolean canQueueInput(int keyboard, int mouse) { return true; }
 public void putKeyboardEvent(int k, byte s, int c, long n, boolean r) { events.add("K "+k+" "+s); text.add(k+" "+s+" "+c+" "+r); }
 public void putMouseEventWithCoords(byte b, byte s, int x, int y, int z, long n) { events.add("M "+b+" "+s+" "+x+" "+y+" "+z); }
}""")
            test = home / "Check.java"
            test.write_text("""import wurm.graphics.WindowInput;
import org.lwjgl.input.GLFWInputImplementation;
public class Check {
 static void check(boolean b) { if(!b) throw new AssertionError(); }
 public static void main(String[] args) {
  var s=GLFWInputImplementation.singleton; var p=new WindowInput(960,540);
  p.apply("POINT 0.25 0.75"); p.apply("BUTTON 0 1"); p.apply("BUTTON 0 0");
  check(s.events.get(0).equals("M -1 0 239 404 0"));
  check(s.events.get(1).equals("M 0 1 239 404 0"));
  check(s.events.get(2).equals("M 0 0 239 404 0"));
  p.apply("POINT 1 1"); p.apply("MOVE 999 -999");
  check(p.x()==959 && p.y()==539);
  p.apply("POINT 0 0"); p.apply("MOVE 0.25 -0.25"); p.apply("MOVE 0.25 -0.25");
  check(p.x()==0.5 && p.y()==0.5); // Slow controller movement must retain fractional pixels.
  p.apply("KEY 17 1"); p.apply("BUTTON 1 1"); p.apply("RESET");
  check(s.events.contains("K 17 0") && s.events.contains("M 1 0 0 0 0"));
  p.apply("TEXT 233"); p.apply("KEYCHAR 30 65 0"); p.apply("KEYCHAR 30 65 1");
  check(s.text.contains("0 1 233 false") && s.text.contains("30 1 65 false") && s.text.contains("30 1 65 true"));
  p.apply("RESET"); check(s.text.contains("30 0 0 false"));
  p.apply("KEYCHAR 28 13 0"); p.apply("KEY 28 0");
  check(s.text.contains("28 1 13 false") && s.text.contains("28 0 0 false"));
  s.grab=true; check(!p.visible()); p.apply("MOVE -100 -100"); check(p.x()<0 && p.displayX()==0);
  int count=p.applied(), queued=s.events.size();
  for(String invalid:new String[]{"POINT NaN 0","POINT 0 Infinity","POINT -0.01 0","POINT 1.01 0","POINT 0 2","BUTTON 8 1","TEXT 31","TEXT 65536","KEYCHAR 256 65 0","KEYCHAR 30 65 2"}) {
   try {p.apply(invalid); throw new AssertionError(invalid);} catch(IllegalArgumentException ok) {}
  }
  check(p.applied()==count && s.events.size()==queued);
  s.grab=false; p.resize(320,240); p.apply("POINT 1 1");
  check(p.x()==319 && p.y()==239 && p.visible());
 }
}""")
            subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(home),
                            str(sink), str(test), str(ROOT/"runtime-probe/src/client/DesktopInput.java"),
                            str(ROOT/"graphics-compat/window/wurm/graphics/WindowInput.java")], check=True)
            subprocess.run(["java", "-cp", str(home), "Check"], check=True, timeout=15)

    @unittest.skipUnless(os.environ.get("WURM_INPUT_API_JAR"), "optional packaged LWJGL API required")
    def test_real_pinned_lwjgl_queue_preserves_click_position_and_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            display = home / "org/lwjgl/opengl/Display.java"
            display.parent.mkdir(parents=True)
            # Only the desktop window dimensions are stubbed; queue and input adapter are the shipped classes.
            display.write_text("package org.lwjgl.opengl; public class Display { public static int getHeight() { return 540; } }")
            check = home / "Check.java"
            check.write_text("""import java.nio.*;
import org.lwjgl.input.GLFWInputImplementation;
import wurm.graphics.WindowInput;
public class Check {
 public static void main(String[] args) {
  var p=new WindowInput(960,540); var sink=GLFWInputImplementation.singleton;
  p.apply("POINT 0.25 0.75"); p.apply("BUTTON 0 1"); p.apply("BUTTON 0 0");
  ByteBuffer data=ByteBuffer.allocate(1024); sink.readMouse(data); data.flip();
  if(data.remaining()!=66) throw new AssertionError("three Mouse events required");
  for(int i=0;i<3;i++) {
   int button=data.get(), state=data.get(), x=data.getInt(), y=data.getInt(), wheel=data.getInt(); data.getLong();
   if(button!=(i==0?-1:0) || state!=(i==1?1:0) || x!=239 || y!=136 || wheel!=0) throw new AssertionError("click coordinates/order");
  }
  if(sink.mouse_buffer[0]!=0) throw new AssertionError("stuck click");
  p.apply("KEY 17 1"); p.apply("BUTTON 1 1"); p.apply("RESET");
  if(sink.key_down_buffer[17]!=0 || sink.mouse_buffer[1]!=0) throw new AssertionError("stuck held input");
  ByteBuffer keys=ByteBuffer.allocate(1024); sink.readKeyboard(keys); keys.clear();
  p.apply("TEXT 233"); p.apply("KEYCHAR 30 65 0"); p.apply("KEY 30 0");
  sink.readKeyboard(keys); keys.flip();
  if(keys.remaining()!=72) throw new AssertionError("four keyboard queue events expected");
  if(keys.getInt()!=0 || keys.get()!=1 || keys.getInt()!=233) throw new AssertionError("text character lost");
  keys.getLong(); keys.get(); keys.position(36);
  if(keys.getInt()!=30 || keys.get()!=1 || keys.getInt()!=65) throw new AssertionError("hardware character lost");
  if(sink.key_down_buffer[30]!=0) throw new AssertionError("hardware release lost");
  // The real queue has room for only 200 events. Deliver a maximum composer
  // draft with backpressure, followed by the newline and key release.
  keys.clear(); sink.readKeyboard(keys);
  var pending=new java.util.ArrayDeque<String>();
  for(int i=0;i<240;i++) pending.add("TEXT "+(65+i%26));
  pending.add("KEYCHAR 28 13 0"); pending.add("KEY 28 0");
  var chars=new StringBuilder(); int frames=0;
  while(!pending.isEmpty()) {
   if(++frames>10) throw new AssertionError("input did not drain");
   while(!pending.isEmpty() && p.canApply(pending.peek())) p.apply(pending.remove());
   keys=ByteBuffer.allocate(200*18); sink.readKeyboard(keys); keys.flip();
   while(keys.hasRemaining()) {
    int k=keys.getInt(), down=keys.get(), c=keys.getInt(); keys.getLong(); keys.get();
    if(down==1 && c!=0) chars.append((char)c);
   }
  }
  StringBuilder expected=new StringBuilder(); for(int i=0;i<240;i++) expected.append((char)(65+i%26)); expected.append((char)13);
  if(!chars.toString().equals(expected.toString()) || frames<3 || sink.key_down_buffer[28]!=0)
   throw new AssertionError("paste/submit lost, reordered or stuck");
 }
}""")
            api = os.environ["WURM_INPUT_API_JAR"]
            subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-cp", api, "-d", str(home),
                            str(display), str(check), str(ROOT/"runtime-probe/src/client/DesktopInput.java"),
                            str(ROOT/"graphics-compat/window/wurm/graphics/WindowInput.java")], check=True)
            subprocess.run(["java", "-cp", str(home)+":"+api, "Check"], check=True, timeout=15)

if __name__ == "__main__":
    unittest.main()
