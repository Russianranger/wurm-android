"""Exercise error attribution and failure retention without a GPU or proprietary files."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which('java'), 'Java 17 required')
class GraphicsErrorsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        source = cls.home/'Check.java'
        source.write_text('''import wurm.graphics.GlChecks;
import java.util.*; import java.util.concurrent.atomic.AtomicInteger;
public class Check {
 public static void main(String[] args) {
  List<String> logs = new ArrayList<>();
  Queue<Integer> errors = new ArrayDeque<>();
  GlChecks checks = new GlChecks(() -> errors.isEmpty() ? 0 : errors.remove(), logs::add);
  if (args[0].equals("clean")) {
   if (checks.get("createProgram", () -> 7) != 7) throw new AssertionError("return value lost");
   checks.run("frame1.drawArrays", () -> logs.add("draw executed"));
   if (!logs.equals(Arrays.asList("GL_BEGIN stage=createProgram", "GL_OK stage=createProgram",
       "GL_BEGIN stage=frame1.drawArrays", "draw executed", "GL_OK stage=frame1.drawArrays"))) throw new AssertionError(logs);
  } else if (args[0].equals("draw")) {
   try {
    checks.run("frame1.drawArrays", () -> { errors.add(0x0500); errors.add(0x0502); });
    throw new AssertionError("error accepted");
   } catch (IllegalStateException expected) {
    if (!expected.getMessage().equals("stage=frame1.drawArrays errors=0x0500(GL_INVALID_ENUM),0x0502(GL_INVALID_OPERATION)")) throw new AssertionError(expected);
    if (logs.stream().anyMatch(s -> s.equals("GL_OK stage=frame1.drawArrays"))) throw new AssertionError(logs);
   }
  } else if (args[0].equals("stale")) {
   errors.add(0x0506);
   try { checks.check("createCapabilities"); throw new AssertionError("startup error ignored"); }
   catch (IllegalStateException expected) {
    if (!expected.getMessage().contains("stage=createCapabilities errors=0x0506(GL_INVALID_FRAMEBUFFER_OPERATION)")) throw new AssertionError(expected);
   }
  } else if (args[0].equals("unknown")) {
   errors.add(0x1234);
   try { checks.check("readPixels"); throw new AssertionError("unknown error ignored"); }
   catch (IllegalStateException expected) {
    if (!expected.getMessage().contains("0x1234(UNKNOWN_GL_ERROR)")) throw new AssertionError(expected);
   }
  } else {
   AtomicInteger count = new AtomicInteger();
   checks = new GlChecks(() -> { count.incrementAndGet(); return 0x0507; }, logs::add);
   try { checks.check("readPixels"); throw new AssertionError("persistent error accepted"); }
   catch (IllegalStateException expected) {
    if (count.get() != 16 || !expected.getMessage().contains("error drain limit reached")) throw new AssertionError(expected);
   }
  }
 }
}''')
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.home),
                        str(source), str(ROOT/'graphics-compat/probe/wurm/graphics/GlChecks.java')], check=True)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_case(self, name):
        subprocess.run(['java', '-cp', str(self.home), 'Check', name], check=True, timeout=15)

    def test_success_preserves_return_value_and_logs_operation_order(self): self.run_case('clean')
    def test_draw_error_names_and_codes_are_not_a_pass(self): self.run_case('draw')
    def test_startup_error_is_not_misattributed_to_draw(self): self.run_case('stale')
    def test_unknown_error_keeps_numeric_code(self): self.run_case('unknown')
    def test_persistent_driver_error_cannot_hang_report(self): self.run_case('limit')


if __name__ == '__main__': unittest.main()
