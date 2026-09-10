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
 public void putKeyboardEvent(int k, byte s, int c, long n, boolean r) { events.add("K "+k+" "+s); }
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
  s.grab=true; check(!p.visible()); p.apply("MOVE -100 -100"); check(p.x()<0 && p.displayX()==0);
  int count=p.applied(), queued=s.events.size();
  for(String invalid:new String[]{"POINT NaN 0","POINT 0 Infinity","POINT -0.01 0","POINT 1.01 0","POINT 0 2","BUTTON 8 1"}) {
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
 }
}""")
            api = os.environ["WURM_INPUT_API_JAR"]
            subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-cp", api, "-d", str(home),
                            str(display), str(check), str(ROOT/"runtime-probe/src/client/DesktopInput.java"),
                            str(ROOT/"graphics-compat/window/wurm/graphics/WindowInput.java")], check=True)
            subprocess.run(["java", "-cp", str(home)+":"+api, "Check"], check=True, timeout=15)

if __name__ == "__main__":
    unittest.main()
