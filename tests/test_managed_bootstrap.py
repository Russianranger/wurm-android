"""Control-protocol tests with handwritten fake APIs, never proprietary Wurm files."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("java"), "JDK 17 needed")
class ManagedBootstrapTest(unittest.TestCase):
    def fixture(self, shutdown=True, startup_failure=False):
        folder = tempfile.TemporaryDirectory(prefix="wurm-control-test-")
        self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        server = root / "com/wurmonline/server/Server.java"
        server.parent.mkdir(parents=True)
        method = '''public void shutDown() throws Exception {
            java.nio.file.Files.writeString(java.nio.file.Path.of("synthetic-save"), "saved");
        }''' if shutdown else ""
        server.write_text("package com.wurmonline.server; public class Server { public static Server getInstance() { return new Server(); }" + method + "}")
        poc = root / "poc/AndroidServerMain.java"
        poc.parent.mkdir()
        body = '''new Thread(() -> { while(true) { try { Thread.sleep(1000); } catch (Exception ignored) {} } }).start();
            throw new IllegalStateException("synthetic startup failure");''' if startup_failure else 'System.out.println("FIXTURE_STARTED"); while(true) Thread.sleep(1000);'
        poc.write_text('package poc; public class AndroidServerMain { public static void main(String[] args) throws Exception {' + body + '}}')
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(root), str(server), str(poc),
                        *map(str, (ROOT / "runtime-probe/src/server").glob("*.java")),
                        *map(str, (ROOT / "runtime-probe/src/probe").glob("*.java"))], check=True, capture_output=True)
        return root

    def launch(self, root, command="STOP\n"):
        return subprocess.run(["java", "-Djava.io.tmpdir=" + str(root), "-cp", str(root), "server.ManagedServerMain", "Adventure"],
                              input=command, text=True, capture_output=True, cwd=root, timeout=10)

    def test_stop_calls_resolved_api_and_exits_despite_poc_keepalive(self):
        root = self.fixture()
        result = self.launch(root)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((root / "synthetic-save").read_text(), "saved")
        self.assertIn("SHUTDOWN_RETURNED", result.stdout)

    def test_missing_shutdown_api_prevents_poc_start(self):
        root = self.fixture(shutdown=False)
        result = self.launch(root)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("FIXTURE_STARTED", result.stdout)
        self.assertFalse((root / "synthetic-save").exists())

    def test_inspection_does_not_consume_or_block_stop_command(self):
        root = self.fixture()
        result = self.launch(root, "DIAGNOSE\nINSPECT\nSTOP\n")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((root / "synthetic-save").read_text(), "saved")

    def test_startup_exception_exits_instead_of_leaving_partial_server_alive(self):
        result = self.launch(self.fixture(startup_failure=True), command="")
        self.assertEqual(result.returncode, 1)
        self.assertIn("synthetic startup failure", result.stderr)


if __name__ == "__main__":
    unittest.main()
