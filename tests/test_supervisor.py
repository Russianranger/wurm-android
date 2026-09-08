"""Linux-only protocol tests; never invokes su or loads Wurm files."""
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest


class SupervisorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="wurm-supervisor-test-")
        self.root = Path(self.temp.name)
        self.runtime = self.root / "world's test runtime"
        self.runtime.mkdir()
        for directory in ("poc-lib", "lib", "Adventure"):
            (self.runtime / directory).mkdir()
        for jar in ("wurm-arm64-poc.jar", "server.jar", "common.jar",
                    "poc-lib/sqlite-jdbc-3.53.2.1.jar",
                    "poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar"):
            (self.runtime / jar).touch()
        self.prefix = self.root / "prefix"
        (self.prefix / "bin").mkdir(parents=True)
        (self.prefix / "tmp").mkdir()
        (self.prefix / "bin/flock").symlink_to(shutil.which("flock"))
        self.java = self.root / "fake java"
        self.java.write_text(f"#!{sys.executable}\n" + '''
import json, os, signal, sys, time
with open("argv.json", "w") as output:
    json.dump(sys.argv[1:], output)
def stop(signum, frame):
    print("FAKE_SHUTDOWN", flush=True)
    sys.exit(0)
signal.signal(signal.SIGTERM, stop)
print("FAKE_STARTED", flush=True)
if os.environ.get("FAKE_EXIT"):
    sys.exit(7)
while True:
    time.sleep(0.1)
''')
        self.java.chmod(0o700)
        source = (Path(__file__).resolve().parents[1] /
                  "app/src/main/assets/server-supervisor.sh").read_text()
        # Replace Android-only tool paths and UID check in the host fixture only.
        source = source.replace("$(/system/bin/id -u)", '${TEST_UID:-0}')
        source = source.replace("prefix=/data/data/com.termux/files/usr",
                                "prefix=" + self.quote(str(self.prefix)))
        source = source.replace("/system/bin/sleep", shutil.which("sleep"))
        self.script = source
        self.processes = []

    @staticmethod
    def quote(value):
        return "'" + value.replace("'", "'\"'\"'") + "'"

    def launch(self, **environment):
        process = subprocess.Popen(
            ["bash", "--noprofile", "--norc", "-c", self.script,
             "wurm-launcher", str(self.runtime), str(self.java), "test"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True,
            env={**os.environ, **environment})
        lines = queue.Queue()
        def collect():
            for line in process.stdout:
                lines.put(line.rstrip())
        thread = threading.Thread(target=collect, daemon=True)
        thread.start()
        self.processes.append((process, thread))
        return process, lines

    def expect(self, lines, text):
        deadline = time.monotonic() + 8
        observed = []
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=0.2)
                observed.append(line)
                if text in line:
                    return line
            except queue.Empty:
                pass
        self.fail(f"Never saw {text!r}; output: {observed!r}")

    def start(self, process, lines):
        self.expect(lines, ":READY")
        process.stdin.write("start\n")
        process.stdin.flush()
        self.expect(lines, "FAKE_STARTED")

    def tearDown(self):
        for process, thread in self.processes:
            if not process.stdin.closed:
                process.stdin.close()
            process.wait(timeout=8)
            thread.join(timeout=2)
            process.stdout.close()
        self.temp.cleanup()

    def test_exact_arguments_and_targeted_stop(self):
        process, lines = self.launch()
        self.start(process, lines)
        self.assertEqual(json.loads((self.runtime / "argv.json").read_text()), [
            "-Xms512m", "-Xmx4g", "-Djava.awt.headless=true", "-cp",
            "wurm-arm64-poc.jar:poc-lib/sqlite-jdbc-3.53.2.1.jar:"
            "poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar:server.jar:common.jar:lib/*",
            "poc.AndroidServerMain", "Adventure"])
        process.stdin.write("stop\n")
        process.stdin.flush()
        self.expect(lines, "FAKE_SHUTDOWN")
        self.assertEqual(process.wait(timeout=8), 0)

    def test_eof_stops_owned_child(self):
        process, lines = self.launch()
        self.start(process, lines)
        process.stdin.close()
        self.expect(lines, "FAKE_SHUTDOWN")
        self.assertEqual(process.wait(timeout=8), 0)

    def test_cancel_before_ack_never_starts_java(self):
        process, lines = self.launch()
        self.expect(lines, ":READY")
        process.stdin.close()
        self.assertNotEqual(process.wait(timeout=8), 0)
        self.assertFalse((self.runtime / "argv.json").exists())

    def test_duplicate_lock_and_restart_after_exit(self):
        first, lines = self.launch()
        self.start(first, lines)
        duplicate, duplicate_lines = self.launch()
        self.expect(duplicate_lines, "already locked")
        self.assertNotEqual(duplicate.wait(timeout=8), 0)
        self.assertIsNone(first.poll())
        first.stdin.close()
        self.assertEqual(first.wait(timeout=8), 0)
        restarted, restarted_lines = self.launch()
        self.start(restarted, restarted_lines)

    def test_missing_jar_fails_before_launch(self):
        (self.runtime / "server.jar").unlink()
        process, lines = self.launch()
        self.expect(lines, "Missing or unreadable: server.jar")
        self.assertNotEqual(process.wait(timeout=8), 0)
        self.assertFalse((self.runtime / "argv.json").exists())

    def test_non_root_rejected(self):
        process, lines = self.launch(TEST_UID="10000")
        self.expect(lines, "Root was not granted")
        self.assertNotEqual(process.wait(timeout=8), 0)

    def test_unexpected_jvm_exit_propagates(self):
        process, lines = self.launch(FAKE_EXIT="1")
        self.start(process, lines)
        self.expect(lines, ":EXIT:7")
        self.assertEqual(process.wait(timeout=8), 7)


if __name__ == "__main__":
    unittest.main()
