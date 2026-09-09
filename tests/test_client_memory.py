"""Run both real host JVM collectors without Wurm, a renderer or proprietary inputs."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientMemoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-client-memory-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.root),
                        *map(str,(ROOT/'runtime-probe/src/client').glob('*.java'))],check=True)

    def launch(self, collector, expected=None, extra_cp=None):
        return subprocess.run(['java','-Xms32m','-Xmx256m',
            '-XX:+UseG1GC' if collector=='g1' else '-XX:+UseSerialGC',
            '-Dwurm.client.expectedGc='+(expected or collector),
            '-Xlog:gc=info,gc+init=info,safepoint=info:stdout:utctime,pid,tid,tags',
            '-cp',str(self.root)+((':'+str(extra_cp)) if extra_cp else ''),
            'client.ClientBootstrap','memory-'+collector],capture_output=True,text=True,timeout=90)

    def test_g1_collector_allocations_jit_and_reclamation(self):
        result=self.launch('g1')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('expected=g1 actual=[G1 Young Generation, G1 Old Generation]',result.stdout)
        self.assertIn('MEMORY_PROBE_PASS collector=g1',result.stdout)
        self.assertIn('PROGRESS round=16',result.stdout)
        self.assertIn('Using G1',result.stdout)
        self.assertNotIn('ENTRY_INITIALIZE',result.stdout)

    def test_serial_collector_allocations_jit_and_reclamation(self):
        result=self.launch('serial')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('expected=serial actual=[Copy, MarkSweepCompact]',result.stdout)
        self.assertIn('MEMORY_PROBE_PASS collector=serial',result.stdout)
        self.assertIn('Using Serial',result.stdout)
        self.assertNotIn('ENTRY_INITIALIZE',result.stdout)

    def test_requested_collector_mismatch_fails_before_memory_or_game_work(self):
        result=self.launch('g1','serial')
        self.assertEqual(result.returncode,42,result.stdout+result.stderr)
        self.assertIn('CLIENT_COLLECTOR_MISMATCH',result.stdout)
        self.assertNotIn('[memory] BEGIN',result.stdout)

    def test_accidental_game_or_graphics_classpath_is_rejected_before_loading(self):
        for name in ['com/wurmonline/client/WurmClientBase.class','org/lwjgl/opengl/GL20.class']:
            with tempfile.TemporaryDirectory() as folder:
                p=Path(folder)/name;p.parent.mkdir(parents=True);p.write_bytes(b'authored non-class sentinel')
                result=self.launch('serial',extra_cp=folder)
                self.assertEqual(result.returncode,42,result.stdout+result.stderr)
                self.assertIn('MEMORY_PROBE_CLASSPATH_NOT_ISOLATED',result.stdout)
                self.assertNotIn('[memory] BEGIN',result.stdout)


if __name__=='__main__': unittest.main()
