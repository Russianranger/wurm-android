"""Run the real Java network preflight on localhost without Wurm/SQLite inputs."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("java"), "JDK 17 needed")
class NetworkProbeTest(unittest.TestCase):
    def test_native_network_calls_and_local_tcp_round_trip(self):
        with tempfile.TemporaryDirectory(prefix="wurm-network-test-") as directory:
            subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", directory,
                            str(ROOT / "runtime-probe/src/probe/NetworkProbe.java")], check=True, capture_output=True)
            result = subprocess.run(["java", "-cp", directory, "probe.NetworkProbe"], cwd=directory,
                                    text=True, capture_output=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("[network] ENUMERATE_BEGIN", result.stdout)
            self.assertIn("[network] LOCALHOST_OK", result.stdout)
            self.assertIn("[network] NETWORK_OK: localhost resolution and TCP loopback exchange", result.stdout)
            self.assertFalse(list(Path(directory).glob("*.db")))


if __name__ == "__main__":
    unittest.main()
