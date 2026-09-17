"""Native pixel-pack buffer ownership, bounds, fallback and fault restoration."""
from pathlib import Path
import subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
class NativeReadbackTest(unittest.TestCase):
 def test_ownership_exact_bytes_state_restoration_and_failure_paths(self):
  with tempfile.TemporaryDirectory() as td:
   exe=Path(td)/'check'
   subprocess.run(['cc','-std=c11','-O2','-Wall','-Wextra','-Werror','-I'+str(ROOT/'tests/fixtures/readback'),'-I'+str(ROOT/'graphics-compat/native'),str(ROOT/'tests/native/pipelined_readback_faults.c'),'-o',str(exe)],check=True,capture_output=True)
   r=subprocess.run([str(exe)],capture_output=True,text=True,timeout=20)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('READBACK_NATIVE_FAULTS_PASS',r.stdout)
