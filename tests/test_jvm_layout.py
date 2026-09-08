"""Host dynamic-linker contract tests; Android HotSpot still needs a Thor run."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(sys.platform == "linux" and shutil.which("cc"), "Linux C compiler required")
class JvmLayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory(prefix="wurm-layout-build-")
        cls.addClassCleanup(cls.build.cleanup)
        cls.runner = Path(cls.build.name) / "runner"
        cls.library = Path(cls.build.name) / "fixture.so"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-O2", "-fPIC", "-shared",
                        str(ROOT / "tests/native/jvm_layout_library.c"), "-ldl", "-o", str(cls.library)], check=True)
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-O2", "-fPIE", "-pie",
                        "-Wl,--export-dynamic-symbol=dl_iterate_phdr", "-Wl,--export-dynamic-symbol=dladdr",
                        "-I", str(ROOT / "runtime-probe/native"),
                        str(ROOT / "runtime-probe/native/jvm_layout.c"),
                        str(ROOT / "tests/native/jvm_layout_runner.c"),
                        "-ldl", "-pthread", "-o", str(cls.runner)], check=True)

    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="wurm-layout-test-")
        self.addCleanup(work.cleanup)
        base = Path(work.name)
        self.home = base / "jre"
        self.installed = base / "apk/lib/arm64/libjvm.so"
        self.decoy = base / "other/libjvm.so"
        self.alias = self.home / "lib/server/libjvm.so"
        for path in (self.installed, self.decoy, self.alias):
            path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(self.library, self.installed)
        shutil.copyfile(self.library, self.decoy)
        self.alias.symlink_to(self.installed)
        self.modules = self.home / "lib/modules"
        self.modules.write_bytes(b"synthetic test data, not Java classes")

    def run_fixture(self, enabled=True):
        return subprocess.run([str(self.runner), str(self.home), str(self.installed),
                               str(self.decoy), "enabled" if enabled else "disabled"],
                              capture_output=True, text=True, timeout=10)

    def test_selected_library_gets_image_path_and_other_library_keeps_its_path(self):
        # Negative control: the loader's flat path derives a missing module image.
        old = self.run_fixture(enabled=False)
        self.assertEqual(old.returncode, 0, old.stdout + old.stderr)
        old_names = [line.split("=", 1)[1] for line in old.stdout.splitlines() if line.startswith("PHDR=")]
        self.assertEqual(old_names, [str(self.installed), str(self.decoy)])
        self.assertFalse((Path(old_names[0]).parents[2] / "lib/modules").exists())

        fixed = self.run_fixture()
        self.assertEqual(fixed.returncode, 0, fixed.stdout + fixed.stderr)
        for prefix in ("DLADDR=", "PHDR="):
            names = [line.split("=", 1)[1] for line in fixed.stdout.splitlines() if line.startswith(prefix)]
            self.assertEqual(names, [str(self.alias), str(self.decoy)])
            self.assertEqual(Path(names[0]).parents[2] / "lib/modules", self.modules)
        self.assertEqual(fixed.stdout.count("JVM_IMAGE_PATH_OK"), 1)

    def test_wrong_alias_is_rejected(self):
        self.alias.unlink()
        self.alias.symlink_to(self.decoy)
        self.assertEqual(self.run_fixture().returncode, 76)

    def test_missing_modules_are_rejected(self):
        self.modules.unlink()
        self.assertEqual(self.run_fixture().returncode, 76)


if __name__ == "__main__":
    unittest.main()
