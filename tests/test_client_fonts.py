"""Real headless Java 2D with an empty native fontconfig; no proprietary fixtures."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SYSTEM = Path('/usr/share/fonts/truetype/dejavu')


@unittest.skipUnless(shutil.which('java') and (SYSTEM/'DejaVuSans.ttf').is_file(), 'JDK 17 and DejaVu fonts required')
class ClientFontsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        cls.classes = cls.home/'classes'
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.classes),
                        *map(str, (ROOT/'runtime-probe/src/client').glob('*.java'))], check=True)
        cls.empty = cls.home/'empty-fontconfig.xml'
        cls.empty.write_text('<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd"><fontconfig></fontconfig>')

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def workspace(self, complete=True):
        work = Path(tempfile.mkdtemp(dir=self.home))
        fonts = work/'system fonts'
        fonts.mkdir()
        pairs = [('DejaVuSans.ttf', 'Roboto-Regular.ttf')]
        if complete:
            pairs += [('DejaVuSans-Bold.ttf', 'Roboto-Bold.ttf'), ('DejaVuSerif.ttf', 'NotoSerif-Regular.ttf'),
                      ('DejaVuSansMono.ttf', 'DroidSansMono.ttf')]
        for source, name in pairs:
            shutil.copyfile(SYSTEM/source, fonts/name)
        return work

    def run_fonts(self, work, mode='fonts'):
        return subprocess.run(['java', '-Djava.awt.headless=true', f'-Duser.home={work}/user',
            f'-Dwurm.client.fontDir={work}/system fonts', f'-Dwurm.client.fontConfig={work}/fontconfig.properties',
            '-cp', str(self.classes), 'client.ClientBootstrap', mode], capture_output=True, text=True, timeout=20,
            env=dict(os.environ, FONTCONFIG_FILE=str(self.empty), FONTCONFIG_PATH=str(work/'absent')))

    def test_rasterizes_all_twenty_logical_styles_without_system_fontconfig(self):
        work = self.workspace()
        before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (work/'system fonts').iterdir()}
        result = self.run_fonts(work)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertEqual(result.stdout.count('FONT_RASTER_OK '), 20)
        self.assertIn('FONT_PREFLIGHT_PASS logicalStyles=20', result.stdout)
        self.assertIn('sansserif.bold=Roboto-Bold.ttf', result.stdout)
        self.assertEqual(before, {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (work/'system fonts').iterdir()})
        self.assertNotIn('ENTRY_INITIALIZE', result.stdout)

    def test_missing_optional_faces_use_visible_fallback_and_synthetic_styles(self):
        result = self.run_fonts(self.workspace(False))
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('FONT_FALLBACK monospaced=sans', result.stdout)
        self.assertIn('FONT_FALLBACK serif=sans', result.stdout)
        self.assertIn('FONT_PREFLIGHT_PASS logicalStyles=20', result.stdout)

    def test_missing_required_font_stops_entry_before_engine_initialization(self):
        work = self.workspace(False)
        (work/'system fonts/Roboto-Regular.ttf').unlink()
        result = self.run_fonts(work, 'entry')
        self.assertEqual(result.returncode, 42)
        self.assertIn('ANDROID_FONTS_MISSING', result.stdout)
        self.assertNotIn('ENTRY_INITIALIZE', result.stdout)
        self.assertFalse((work/'fontconfig.properties').exists())

    def test_corrupt_font_fails_visibly_without_claiming_font_readiness(self):
        work = self.workspace(False)
        (work/'system fonts/Roboto-Regular.ttf').write_bytes(b'invalid font data'*8)
        result = self.run_fonts(work)
        self.assertEqual(result.returncode, 42, result.stdout)
        self.assertIn('FontFormatException', result.stdout)
        self.assertNotIn('FONT_PREFLIGHT_PASS', result.stdout)

    def test_existing_configuration_is_not_overwritten(self):
        work = self.workspace()
        target = work/'fontconfig.properties'
        target.write_text('existing user configuration')
        result = self.run_fonts(work)
        self.assertEqual(result.returncode, 42)
        self.assertEqual(target.read_text(), 'existing user configuration')
        self.assertIn('FileAlreadyExistsException', result.stdout)

    def test_oversized_font_is_rejected_before_reading_or_configuring(self):
        work = self.workspace(False)
        with (work/'system fonts/Roboto-Regular.ttf').open('wb') as out:
            out.truncate(32*1024*1024+1)
        result = self.run_fonts(work)
        self.assertEqual(result.returncode, 42)
        self.assertIn('FONT_FILE_SIZE_INVALID', result.stdout)
        self.assertFalse((work/'fontconfig.properties').exists())


if __name__ == '__main__':
    unittest.main()
