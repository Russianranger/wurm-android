"""Check actual Android compiler TLS output, including the former build mode."""
import importlib.util
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('asan_build', ROOT / 'scripts/build-asan-runtime.py')
asan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asan)


@unittest.skipUnless(os.environ.get('ANDROID_NDK_HOME'), 'pinned Android NDK required')
class AsanTlsTest(unittest.TestCase):
    def test_binary_gate_distinguishes_native_emulated_and_allocating_tls(self):
        compiler = Path(os.environ['ANDROID_NDK_HOME']) / 'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang++'
        objdump = compiler.parent / 'llvm-objdump'
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            source = home / 'counter.cpp'
            # Same declaration/access model as compiler-rt's lsan_common_linux.cpp.
            template = '''namespace __lsan {
              ATTRIBUTE __thread int disable_counter;
              bool DisabledInThisThread() { return disable_counter > 0; }
            }'''
            for mode in ('native', 'emulated', 'dynamic'):
                with self.subTest(mode=mode):
                    source.write_text(template.replace('ATTRIBUTE',
                        '' if mode == 'dynamic' else '__attribute__((tls_model("initial-exec")))'))
                    library = home / (mode + '.so')
                    flags = shlex.split(asan.RUNTIME_FLAGS)
                    if mode == 'emulated': flags.append('-femulated-tls')
                    subprocess.run([str(compiler), *flags, '-O2', '-fPIC', '-shared', '-nostdlib',
                                    str(source), '-o', str(library)], check=True)
                    if mode == 'native':
                        asan.verify_tls(library, objdump)
                    else:
                        with self.assertRaisesRegex(ValueError, 'emulated TLS|allocating TLS resolver'):
                            asan.verify_tls(library, objdump)


if __name__ == '__main__':
    unittest.main()
