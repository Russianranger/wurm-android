"""Prove the actual startup check and an instrumented out-of-bounds write on the host."""
from pathlib import Path
import os
import resource
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class NativeHeapCheckTest(unittest.TestCase):
    def test_sanitizer_startup_and_invalid_write_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            source = home/"probe.c"
            source.write_text("""
#include "wurm_heap_check.h"
#include <stdint.h>
__attribute__((noinline)) static void write_at(volatile char *p, int offset) { p[offset] = 42; }
int main(int argc, char **argv) {
    if (!wurm_heap_check_ready()) return 78;
    char *p = malloc(32);
    if (!p) return 79;
    write_at(p, argc > 1 ? atoi(argv[1]) : 31);
    printf("value=%d\\n", p[31]);
    free(p);
    return 0;
}
""")
            binary = home/"probe"
            subprocess.run(["gcc", "-O1", "-g", "-fsanitize=address", "-fno-omit-frame-pointer",
                "-no-pie", "-I"+str(ROOT/"graphics-compat/native"), str(source), "-o", str(binary)], check=True)
            env = dict(os.environ, ASAN_OPTIONS="detect_leaks=0:abort_on_error=1:symbolize=0")
            def no_core():
                resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
            clean = subprocess.run([str(binary)], env=env, capture_output=True, text=True, preexec_fn=no_core)
            self.assertEqual(clean.returncode, 0, clean.stdout+clean.stderr)
            self.assertIn("ASAN_READY", clean.stderr)
            bad = subprocess.run([str(binary), "32"], env=env, capture_output=True, text=True, preexec_fn=no_core)
            self.assertNotEqual(bad.returncode, 0)
            self.assertIn("ERROR: AddressSanitizer: heap-buffer-overflow", bad.stderr)
            self.assertIn("WRITE of size 1", bad.stderr)
            self.assertIn("allocated by thread", bad.stderr)


if __name__ == "__main__":
    unittest.main()
