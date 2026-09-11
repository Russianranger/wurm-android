"""Reproduce the Thor shader-relink overflow in GL4ES's actual cleanup function."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('gl4es_patch', ROOT / 'scripts/patch-gl4es.py')
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

HARNESS = r'''
// Include the production translation unit to exercise its private cleanup.
#include "src/gl/program.c"
#include <assert.h>
int main(void) {
    program_t p = {0};
    p.uniform = kh_init(uniformlist);
    p.attribloc = kh_init(attribloclist);
    kh_resize(uniformlist, p.uniform, 128);
    assert(p.uniform->n_buckets == 128); // flags occupy 32 bytes, as on Thor
    int ret;
    khint_t slot = kh_put(uniformlist, p.uniform, 129, &ret);
    assert(ret > 0 && slot < p.uniform->n_buckets);
    uniform_t *u = calloc(1, sizeof(*u));
    u->id = 129; u->name = strdup("sparseUniform");
    kh_value(p.uniform, slot) = u;
    // Old cleanup passes key 129 to kh_del: flags[8] reads past 32 bytes.
    clear_program(&p);
    assert(kh_size(p.uniform) == 0 && p.uniform->n_occupied == 0);
    // Refill with colliding and sparse IDs, then clear and reuse repeatedly.
    const unsigned int locations[] = {0, 128, 256, 129, 65537, 1};
    const unsigned int attributes[] = {0, 4, 8, 12, 1};
    p.cache.cap = 256; p.cache.cache = malloc(p.cache.cap);
    for (int cycle = 0; cycle < 20; ++cycle) {
        for (unsigned int i = 0; i < sizeof(locations)/sizeof(*locations); ++i) {
            slot = kh_put(uniformlist, p.uniform, locations[i], &ret);
            assert(ret > 0);
            u = calloc(1, sizeof(*u)); u->name = strdup("uniform");
            u->id = locations[i]; kh_value(p.uniform, slot) = u;
        }
        for (unsigned int i = 0; i < sizeof(attributes)/sizeof(*attributes); ++i) {
            slot = kh_put(attribloclist, p.attribloc, attributes[i], &ret);
            assert(ret > 0);
            attribloc_t *a = calloc(1, sizeof(*a));
            a->name = strdup("attribute"); a->glname = a->name;
            kh_value(p.attribloc, slot) = a;
        }
        p.num_uniform = 6; p.cache.size = 128;
        clear_program(&p);
        assert(kh_size(p.uniform) == 0 && p.uniform->n_occupied == 0);
        assert(kh_size(p.attribloc) == 0 && p.attribloc->n_occupied == 0);
        assert(p.num_uniform == 0 && p.cache.size == 0 && p.cache.cap == 256);
        for (unsigned int i = 0; i < sizeof(locations)/sizeof(*locations); ++i)
            assert(kh_get(uniformlist, p.uniform, locations[i]) == kh_end(p.uniform));
        clear_program(&p); // empty/repeated cleanup must also be safe
    }
    free(p.cache.cache);
    kh_destroy(uniformlist, p.uniform);
    kh_destroy(attribloclist, p.attribloc);
    program_t absent = {0}; clear_program(&absent);
    puts("PROGRAM_CLEANUP_PASS sparse locations, collisions, repeated cleanup and reuse");
    return 0;
}
'''


@unittest.skipUnless(os.environ.get('WURM_GL4ES_ARCHIVE'), 'pinned public GL4ES archive required')
class Gl4esProgramCleanupTest(unittest.TestCase):
    def test_relink_clears_values_without_deleting_keys_as_bucket_indices(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            archive = Path(os.environ['WURM_GL4ES_ARCHIVE'])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                             '475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed')
            with tarfile.open(archive) as tar:
                tar.extractall(home, filter='data')
            src = home / 'gl4es-81547d986798e876de8b434193920b606a72363f'
            harness = home / 'probe.c'
            harness.write_text(HARNESS)
            for fixed in (False, True):
                if fixed: patch.apply_program_cleanup(src)
                exe = home / ('fixed' if fixed else 'original')
                result = subprocess.run(['gcc', '-std=gnu99', '-O1', '-g', '-fsanitize=address',
                    '-fno-omit-frame-pointer', '-no-pie', '-DNOX11', '-DNO_GBM', '-DEGL_NO_X11',
                    '-ffunction-sections', '-fdata-sections', '-I'+str(src/'include'), '-I'+str(src),
                    str(harness), '-Wl,--gc-sections', '-lm', '-o', str(exe)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                result = subprocess.run([str(exe)], capture_output=True, text=True,
                    env=dict(os.environ, ASAN_OPTIONS='detect_leaks=0:halt_on_error=1'))
                if fixed:
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn('PROGRAM_CLEANUP_PASS', result.stdout)
                else:
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn('ERROR: AddressSanitizer: heap-buffer-overflow', result.stderr)
                    self.assertIn('kh_del_uniformlist', result.stderr)
                    self.assertIn('0 bytes after 32-byte region', result.stderr)
            with self.assertRaises(ValueError): patch.apply_program_cleanup(src)


if __name__ == '__main__':
    unittest.main()
