"""Exercise real pre-main faults and signal restoration, not an exit-code fixture."""
from pathlib import Path
import os
import platform
import re
import resource
import shutil
import signal
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(platform.system() == "Linux" and platform.machine() in ("x86_64", "aarch64")
                     and shutil.which("cc"), "Linux C compiler required")
class StartupCrashTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.work = tempfile.TemporaryDirectory(prefix="wurm-startup-")
        cls.addClassCleanup(cls.work.cleanup)
        cls.path = Path(cls.work.name)
        cls.recorder = cls.path / "recorder.o"
        subprocess.run(["cc", "-O2", "-Wall", "-Wextra", "-Werror", "-fPIC", "-ffreestanding",
                        "-fno-builtin", "-fno-stack-protector", "-c",
                        str(ROOT / "runtime-probe/native/startup_crash.c"), "-o", str(cls.recorder)], check=True)
        cls.fixture = cls.path / "fixture.c"
        cls.fixture.write_text(r'''
#include <stdlib.h>
#include <string.h>
__attribute__((noinline)) static void overflow(int depth) {
    volatile char buffer[1024];
    buffer[0] = (char)depth;
    void (*volatile next)(int) = overflow;
    next(depth + 1);
    buffer[1] = buffer[0];
}
__attribute__((constructor)) static void constructor(void) {
    const char *mode = getenv("FAULT_MODE");
    if (mode && strcmp(mode, "segv") == 0) *(volatile int *)1 = 7;
    if (mode && strcmp(mode, "stack") == 0) overflow(1);
}
void fixture_loaded(void) {}
''')
        cls.library = cls.path / "libstartup_fixture.so"
        subprocess.run(["cc", "-O0", "-g", "-fPIC", "-shared", str(cls.fixture), "-o", str(cls.library)], check=True)
        main = cls.path / "main.c"
        main.write_text(r'''
#define _GNU_SOURCE
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "startup_crash.h"
void fixture_loaded(void);
static char old_stack[65536];
static void prior(int sig) { (void)sig; if (write(1, "PRIOR_HANDLER\n", 14) != 14) _exit(94); }
static void prepare(int argc, char **argv, char **envp) {
    (void)argc; (void)argv;
    int enabled = 0;
    for (char **p = envp; p && *p; ++p) if (!strcmp(*p, "RESTORE_TEST=1")) enabled = 1;
    if (!enabled) return;
    stack_t stack = {.ss_sp = old_stack, .ss_size = sizeof(old_stack)};
    if (sigaltstack(&stack, 0)) _exit(90);
    struct sigaction action = {0}; action.sa_handler = prior; action.sa_flags = SA_ONSTACK;
    if (sigaction(SIGSEGV, &action, 0)) _exit(91);
}
__attribute__((section(".preinit_array"), used))
static void (*const prepare_first)(int, char **, char **) = prepare;
int main(void) {
    fixture_loaded();
    wurm_startup_main();
    if (wurm_startup_finish()) return 92;
    if (getenv("RESTORE_TEST")) {
        stack_t stack;
        if (sigaltstack(0, &stack) || stack.ss_sp != old_stack || stack.ss_size != sizeof(old_stack)) return 93;
        raise(SIGSEGV);
    }
    puts("MAIN_PASS");
    return 0;
}
''')
        cls.runner = cls.path / "runner"
        subprocess.run(["cc", "-O2", "-I", str(ROOT / "runtime-probe/native"), str(main), str(cls.recorder),
                        "-L", str(cls.path), "-lstartup_fixture", "-Wl,-rpath," + str(cls.path),
                        "-o", str(cls.runner)], check=True)

    def run_fixture(self, mode="none", enabled=True, restore=False):
        env = dict(os.environ, FAULT_MODE=mode)
        env.pop("WURM_STARTUP_TRACE", None)
        if enabled: env["WURM_STARTUP_TRACE"] = "1"
        if restore: env["RESTORE_TEST"] = "1"
        def limits():
            resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
            resource.setrlimit(resource.RLIMIT_STACK, (512 * 1024, 512 * 1024))
        return subprocess.run([str(self.runner)], env=env, text=True, capture_output=True,
                              timeout=5, preexec_fn=limits)

    def test_recorder_has_no_library_dependencies(self):
        self.assertEqual(subprocess.check_output(["nm", "-u", str(self.recorder)], text=True).strip(), "")

    def test_constructor_fault_has_registers_and_module_map_before_main(self):
        result = self.run_fixture("segv")
        self.assertEqual(result.returncode, -signal.SIGSEGV, result.stderr)
        self.assertIn("CAPTURE_READY preinit", result.stderr)
        self.assertIn("[startup-crash] SIGNAL signal=0x000000000000000b", result.stderr)
        self.assertIn("address=0x0000000000000001", result.stderr)
        self.assertIn("phase=0x0000000000000001", result.stderr)
        pc = int(re.search(r"REGISTERS pc=(0x[0-9a-f]+)", result.stderr)[1], 16)
        mappings = re.findall(r"MAP ([0-9a-f]+)-([0-9a-f]+) (r-xp) .*libstartup_fixture.so", result.stderr)
        self.assertTrue(any(int(start, 16) <= pc < int(end, 16) for start, end, _ in mappings), result.stderr)
        self.assertIn("CAPTURE_END", result.stderr)
        self.assertNotIn("MAIN_ENTERED", result.stderr)
        self.assertNotIn("MAIN_PASS", result.stdout)

    def test_exhausted_stack_still_produces_bounded_capture(self):
        result = self.run_fixture("stack")
        self.assertEqual(result.returncode, -signal.SIGSEGV, result.stderr)
        self.assertIn("REGISTERS pc=", result.stderr)
        self.assertIn("CAPTURE_END", result.stderr)
        self.assertLess(len(result.stderr), 120000)
        self.assertNotIn("MAIN_ENTERED", result.stderr)

    def test_disabled_capture_leaves_constructor_fault_untouched(self):
        result = self.run_fixture("segv", enabled=False)
        self.assertEqual(result.returncode, -signal.SIGSEGV)
        self.assertNotIn("startup-crash", result.stderr)

    def test_success_restores_previous_handler_and_alternate_stack(self):
        result = self.run_fixture(restore=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("MAIN_ENTERED", result.stderr)
        self.assertIn("CAPTURE_RELEASED before Java", result.stderr)
        self.assertIn("PRIOR_HANDLER", result.stdout)
        self.assertIn("MAIN_PASS", result.stdout)
        self.assertNotIn("[startup-crash] SIGNAL", result.stderr)


if __name__ == "__main__":
    unittest.main()
