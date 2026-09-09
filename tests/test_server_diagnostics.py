"""JUL and JVM-exit evidence using authored fixtures, without proprietary server classes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("java"), "JDK 17 needed")
class ServerDiagnosticsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="wurm-server-diagnostics-")
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        fixture = cls.root / "com/wurmonline/server/LoginHandler.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text('''package com.wurmonline.server;
import java.util.logging.*;
public class LoginHandler {
 public static void main(String[] args) throws Exception {
  server.ServerDiagnostics.install();
  if (System.getProperty("java.util.logging.config.file") == null) throw new AssertionError("fallback selected");
  Logger logger = Logger.getLogger("com.wurmonline.server.LoginHandler");
  if (args[0].equals("logging")) {
   logger.log(Level.FINE, "Login queue count {0}", 4);
   logger.info("Login response pending");
   LogManager.getLogManager().readConfiguration();
   logger.info("Configuration reloaded");
   server.ServerDiagnostics.capture();
  } else if (args[0].equals("bounded")) {
   logger.log(Level.WARNING, "password={0}", "fixture-sensitive-value");
   logger.log(Level.WARNING, "Request failed", new IllegalStateException("ticket=fixture-auth-bytes"));
   logger.info("Long diagnostic " + "x".repeat(10000) + "\\nforged-line");
  } else if (args[0].equals("stop")) {
   server.ServerDiagnostics.stopRequested();
  }
  finishFixture();
 }
 static void finishFixture() { System.exit(0); }
}''')
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(cls.root),
                        str(fixture), str(ROOT / "runtime-probe/src/server/ServerDiagnostics.java"),
                        str(ROOT / "runtime-probe/src/server/ServerLogHandler.java")], check=True, capture_output=True)

    def launch(self, mode):
        with tempfile.TemporaryDirectory(prefix="wurm-log-session-") as folder:
            result = subprocess.run(["java", "-Djava.io.tmpdir=" + folder, "-cp", str(self.root),
                                     "com.wurmonline.server.LoginHandler", mode],
                                    text=True, capture_output=True, timeout=10, cwd=folder)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual([p.name for p in Path(folder).iterdir()], ["wurm-server-logging.properties"])
            return result.stderr

    def test_existing_jul_calls_and_reload_reach_managed_console(self):
        output = self.launch("logging")
        self.assertIn("LOG_CONFIG_READY", output)
        self.assertEqual(output.count("Login queue count 4"), 1)
        self.assertEqual(output.count("Login response pending"), 1)
        self.assertEqual(output.count("Configuration reloaded"), 1)
        self.assertIn("handlers=[server.ServerLogHandler]", output)
        self.assertIn("SNAPSHOT_END", output)

    def test_unrequested_exit_preserves_zero_and_captures_real_exit_caller(self):
        output = self.launch("exit")
        self.assertIn("JVM_SHUTDOWN_BEGIN requestedStop=false", output)
        self.assertIn("exitCaller=true", output)
        self.assertIn("com.wurmonline.server.LoginHandler.finishFixture", output)
        self.assertIn("JVM_SHUTDOWN_END", output)

    def test_requested_stop_is_distinguished_without_claiming_a_save(self):
        output = self.launch("stop")
        self.assertIn("JVM_SHUTDOWN_BEGIN requestedStop=true", output)
        self.assertIn("does not prove a save", output)

    def test_messages_and_exceptions_are_bounded_and_sensitive_parameters_omitted(self):
        output = self.launch("bounded")
        self.assertNotIn("fixture-sensitive-value", output)
        self.assertNotIn("fixture-auth-bytes", output)
        self.assertNotIn("forged-line", output)
        self.assertIn("[credential/ticket message omitted]", output)
        self.assertIn("CAUSE java.lang.IllegalStateException", output)
        self.assertLess(max(map(len, output.splitlines())), 1500)


if __name__ == "__main__":
    unittest.main()
