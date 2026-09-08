"""Native POSIX record-lock exclusion and release, including process death."""
import fcntl
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("cc"), "C compiler required")
class WorldLockTest(unittest.TestCase):
    def test_excludes_other_owner_and_releases_on_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "hold.c"
            source.write_text('#include "world_lock.h"\n#include <stdio.h>\nint main(int n,char**v) { if(n!=2)return 2; setvbuf(stdout,0,_IONBF,0); if(wurm_world_lock(v[1])<0)return 77; getchar(); return 0; }')
            runner = root / "hold"
            subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I", str(ROOT / "runtime-probe/native"), str(source),
                            str(ROOT / "runtime-probe/native/world_lock.c"), "-o", str(runner)], check=True)
            lock = root / "server.lock"
            with lock.open("w+") as parent:
                fcntl.lockf(parent, fcntl.LOCK_EX | fcntl.LOCK_NB)
                blocked = subprocess.run([str(runner), str(lock)], input="", capture_output=True, text=True, timeout=5)
                self.assertEqual(blocked.returncode, 77)
                fcntl.lockf(parent, fcntl.LOCK_UN)
                child = subprocess.Popen([str(runner), str(lock)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
                try:
                    self.assertIn("WORLD_LOCK_OK", child.stdout.readline())
                    with self.assertRaises(BlockingIOError): fcntl.lockf(parent, fcntl.LOCK_EX | fcntl.LOCK_NB)
                finally:
                    child.kill(); child.communicate(timeout=5)
                fcntl.lockf(parent, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(parent, fcntl.LOCK_UN)


if __name__ == "__main__":
    unittest.main()
