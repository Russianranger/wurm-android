"""Policy tests use allocator fixtures; Android allocator behavior needs the Thor."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("cc"), "C compiler required")
class HeapCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.work = tempfile.TemporaryDirectory(prefix="wurm-heap-policy-")
        cls.addClassCleanup(cls.work.cleanup)
        cls.runner = Path(cls.work.name) / "heap-policy"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-O2", "-I", str(ROOT / "runtime-probe/native"),
                        str(ROOT / "runtime-probe/native/heap_compat.c"), str(ROOT / "tests/native/heap_compat_runner.c"),
                        "-o", str(cls.runner)], check=True)

    def run_policy(self, policy, accepted=1, before=180, after=0):
        return subprocess.run([str(self.runner), policy, str(accepted), str(before), str(after)],
                              text=True, capture_output=True, timeout=5)

    def test_unrequested_policy_leaves_allocator_alone(self):
        result = self.run_policy("unset")
        self.assertEqual(result.returncode, 0)
        self.assertIn("samples=0 disables=0", result.stdout)
        self.assertNotIn("HEAP_TAGGING_OFF", result.stdout)

    def test_explicit_optout_requires_success_and_untagged_new_allocation(self):
        result = self.run_policy("off")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("before=0xb4 after=0x00", result.stdout)
        self.assertIn("samples=2 disables=1", result.stdout)

    def test_unknown_policy_allocator_rejection_or_failed_probe_blocks_java(self):
        for args in [("typo", 1, 180, 0), ("off", 0, 180, 0), ("off", 1, -1, 0),
                     ("off", 1, 180, 180), ("off", 1, 180, -1)]:
            with self.subTest(args=args):
                result = self.run_policy(*args)
                self.assertEqual(result.returncode, 78)
                self.assertNotIn("JAVA_LOAD_ALLOWED", result.stdout)
                self.assertNotIn("HEAP_TAGGING_OFF", result.stdout)


if __name__ == "__main__":
    unittest.main()
