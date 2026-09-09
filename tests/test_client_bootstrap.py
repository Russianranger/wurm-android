"""Handwritten fixtures only: validate client ABI discovery and observable failures."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("java"), "JDK 17 required")
class ClientBootstrapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        cls.helper = cls.home / "helper"
        cls.helper.mkdir()
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(cls.helper),
                        *map(str, (ROOT / "runtime-probe/src/client").glob("*.java"))], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def fixture(self, engine, extra=None):
        dest = Path(tempfile.mkdtemp(dir=self.home))
        sources = {"com/wurmonline/client/WurmClientBase.java": "package com.wurmonline.client; " + engine}
        sources.update(extra or {})
        for name, source in sources.items():
            path = dest / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(dest), *[str(dest / name) for name in sources]], check=True)
        jar = dest / "client.jar"
        with zipfile.ZipFile(jar, "w") as z:
            for f in dest.rglob("*.class"):
                # Deliberately remove this dependency to prove metadata works without resolution.
                if f.name != "Missing.class":
                    z.write(f, f.relative_to(dest))
        return jar

    def run_mode(self, mode, jar=None, events=""):
        cp = str(self.helper) + (":" + str(jar) if jar else "")
        return subprocess.run(["java", "-cp", cp, "client.ClientBootstrap", mode],
                              input=events, capture_output=True, text=True, timeout=15)

    def test_inventory_does_not_initialize_or_resolve_missing_dependencies(self):
        jar = self.fixture('public class WurmClientBase { static { if(true) throw new Error("NO_INIT"); } public static void launch(Missing m, String host, int port) {} }',
                           {"com/wurmonline/client/Missing.java": "package com.wurmonline.client; public class Missing {}",
                            "SteamJni/SteamClientApi.java": "package SteamJni; public class SteamClientApi { public static native byte[] getAuthSessionTicket(long id); }"})
        result = self.run_mode("inventory", jar)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("launch(Lcom/wurmonline/client/Missing;Ljava/lang/String;I)V", result.stdout)
        self.assertIn("getAuthSessionTicket(J)[B", result.stdout)
        self.assertNotIn("NO_INIT", result.stdout)
        entry = self.run_mode("entry", jar)
        self.assertEqual(entry.returncode, 42)
        self.assertIn("NO_INIT", entry.stdout)

    def test_parameterized_launch_is_reported_without_guessed_arguments(self):
        jar = self.fixture('public class WurmClientBase { public static void launch(String host, int port) { throw new Error("MUST_NOT_GUESS"); } }')
        result = self.run_mode("entry", jar)
        self.assertEqual(result.returncode, 42)
        self.assertIn("ENTRY_ABI_REQUIRED", result.stdout)
        self.assertNotIn("MUST_NOT_GUESS", result.stdout)

    def test_verified_zero_argument_launch_invokes_real_method(self):
        jar = self.fixture('public class WurmClientBase { public static void launch() { System.out.println("FIXTURE_LAUNCHED"); } }')
        result = self.run_mode("entry", jar)
        self.assertEqual(result.returncode, 0)
        self.assertIn("FIXTURE_LAUNCHED", result.stdout)

    def test_nested_failure_exposes_root_cause_in_first_status_line(self):
        jar = self.fixture('public class WurmClientBase { public static void launch() { throw new InternalError(new java.lang.reflect.InvocationTargetException(new RuntimeException("FONT_ROOT_CAUSE\\nsecond line"))); } }')
        result = self.run_mode("entry", jar)
        self.assertEqual(result.returncode, 42)
        first = next(line for line in result.stdout.splitlines() if 'BOOTSTRAP_FAILED' in line)
        self.assertIn('root=java.lang.RuntimeException reason=FONT_ROOT_CAUSE second line', first)

    def test_lwjgl_native_failure_is_an_actual_attempt_and_visible(self):
        jar = self.fixture('public class WurmClientBase {}', {"org/lwjgl/opengl/Display.java":
            'package org.lwjgl.opengl; public class Display { public static void create() { throw new UnsatisfiedLinkError("FIXTURE_NATIVE_BLOCKER"); } public static void destroy() {} }'})
        result = self.run_mode("graphics", jar)
        self.assertEqual(result.returncode, 42)
        self.assertIn("GRAPHICS_ATTEMPT", result.stdout)
        self.assertIn("UnsatisfiedLinkError", result.stdout)
        self.assertIn("FIXTURE_NATIVE_BLOCKER", result.stdout)

    def test_input_protocol_rejects_invalid_data_and_releases_on_eof(self):
        events = "KEY 17 1\nBUTTON 0 1\nMOVE NaN 0\nKEY 256 1\nBUTTON 0 2\nWHEEL -2147483648\n" + "x" * 161 + "\nRESET\nKEY 30 1\n"
        result = self.run_mode("input", events=events)
        self.assertEqual(result.returncode, 0)
        self.assertIn("INPUT_READY protocol=1 sink=diagnostic", result.stdout)
        self.assertEqual(result.stdout.count("INPUT_REJECTED"), 5)
        self.assertIn("held=2", result.stdout)
        self.assertIn("INPUT_CLOSED events=4 held=0", result.stdout)

    def test_corrupt_class_fails_inventory_explicitly(self):
        jar = self.home / "corrupt.jar"
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("com/wurmonline/client/WurmClientBase.class", b"bad class bytes")
        result = self.run_mode("inventory", jar)
        self.assertEqual(result.returncode, 42)
        self.assertIn("Not a class file", result.stdout)


if __name__ == "__main__":
    unittest.main()
