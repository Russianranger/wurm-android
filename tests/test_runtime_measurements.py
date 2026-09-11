"""Real JVM sampling, unavailable proc fields, and observation resource lifetime."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RuntimeMeasurementsTest(unittest.TestCase):
    def test_samples_and_daemon_do_not_leak_descriptors_or_keep_jvm_alive(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            source = home/'Measure.java'
            source.write_text('''package probe;
import java.nio.file.*;
public class Measure {
 public static void main(String[] args) throws Exception {
  Path fixture = Path.of(args[0]);
  Files.writeString(fixture.resolve("status"), "Name: fixture\\nVmRSS:\\t123 kB\\nThreads: 7\\nVmSwap: bad\\n");
  Files.createDirectory(fixture.resolve("fd"));
  Files.createFile(fixture.resolve("fd/1")); Files.createFile(fixture.resolve("fd/2"));
  String sample = RuntimeMeasurements.sample("fixture", fixture);
  for (String expected : new String[]{"role=fixture", "pid=", "heapUsedBytes=", "heapMaxBytes=",
       "directUsedBytes=", "gc[", "VmRSSKiB=123", "Threads=7", "VmSwapKiB=-1", "PssKiB=-1", "fdCount=2"})
   if (!sample.contains(expected)) throw new AssertionError(sample);
  if (!RuntimeMeasurements.sample("missing",fixture.resolve("missing")).contains("fdCount=-1")) throw new AssertionError();
  Path self = Path.of("/proc/self");
  long before = RuntimeMeasurements.countFiles(self.resolve("fd"));
  for (int i=0;i<100;i++) RuntimeMeasurements.sample("repeat",self);
  long after = RuntimeMeasurements.countFiles(self.resolve("fd"));
  if (before >= 0 && after > before+1) throw new AssertionError("descriptor growth "+before+" -> "+after);
  RuntimeMeasurements.start("daemon-fixture");
  Thread.sleep(200);
  Thread sampler = Thread.getAllStackTraces().keySet().stream()
    .filter(t -> t.getName().equals("wurm-memory-daemon-fixture")).findFirst().orElseThrow();
  if (!sampler.isDaemon()) throw new AssertionError("sampler owns JVM lifetime");
  System.out.println("MEASUREMENTS_PASS");
 }
}''')
            subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', tmp,
                str(source), str(ROOT/'runtime-probe/src/probe/RuntimeMeasurements.java')], check=True)
            result = subprocess.run(['java', '-cp', tmp, 'probe.Measure', tmp], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
            self.assertIn('MEASUREMENTS_PASS', result.stdout)
            self.assertEqual(result.stdout.count('role=daemon-fixture'), 1)
