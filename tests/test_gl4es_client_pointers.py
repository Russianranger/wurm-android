"""Compile the actual pinned FPE setters against upstream types; no Wurm assets."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("gl4es_patch", ROOT / "scripts/patch-gl4es.py")
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


@unittest.skipUnless(os.environ.get("WURM_GL4ES_ARCHIVE"), "pinned public GL4ES archive required")
class Gl4esClientPointersTest(unittest.TestCase):
    def test_internal_addresses_are_not_rebased_by_the_bound_vbo(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            archive = Path(os.environ["WURM_GL4ES_ARCHIVE"])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                             "475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed")
            with tarfile.open(archive) as tar:
                tar.extractall(home, filter="data")
            src = home / "gl4es-81547d986798e876de8b434193920b606a72363f"
            original = (src / "src/gl/fpe.c").read_text()
            # Use the driver's actual realization expression as well as its setters.
            expression = next(line.strip() for line in original.splitlines()
                              if "void * ptr = (void*)((uintptr_t)w->pointer" in line)
            harness = home / "probe.c"
            harness.write_text('''#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "src/gl/glstate.h"
#include "src/glx/hardext.h"
hardext_t hardext = {.maxvattrib = 16};
glstate_t *glstate;
int main(int argc, char **argv) {
    glstate_t state = {0}; glvao_t vao = {0}; glbuffer_t buffer = {0};
    float vertices[384] = {0}, backing[384] = {0};
    glstate = &state; state.vao = &vao; state.texture.client = 1;
    buffer.data = backing; buffer.real_buffer = 9;
    int failures = 0;
    for (int bound = 0; bound < 2; ++bound) {
        vao.vertex = bound ? &buffer : NULL;
        fpe_glVertexPointer(4, GL_FLOAT, 0, vertices);
        fpe_glColorPointer(4, GL_FLOAT, 0, vertices);
        fpe_glSecondaryColorPointer(4, GL_FLOAT, 0, vertices);
        fpe_glNormalPointer(GL_FLOAT, 0, vertices);
        fpe_glFogCoordPointer(GL_FLOAT, 0, vertices);
        fpe_glTexCoordPointer(4, GL_FLOAT, 0, vertices);
        int slots[] = {ATT_VERTEX, ATT_COLOR, ATT_SECONDARY, ATT_NORMAL,
                       ATT_FOGCOORD, ATT_MULTITEXCOORD0 + 1};
        for (int i = 0; i < 6; ++i) {
            vertexattrib_t *w = &vao.vertexattrib[slots[i]];
            REALIZE_EXPRESSION
            if (ptr != vertices || w->real_buffer != 0 || w->real_pointer != 0)
                ++failures;
        }
        // Internal setup must not unbind the application's buffer.
        if (vao.vertex != (bound ? &buffer : NULL)) return 90;
    }
    printf("misaddressed_attributes=%d\\n", failures);
    return failures;
}
'''.replace("REALIZE_EXPRESSION", expression))
            for fixed in (False, True):
                if fixed:
                    patch.apply_draw(src, ROOT / "graphics-compat/native/wurm_draw_trace.h")
                exe = home / ("fixed" if fixed else "original")
                build = subprocess.run(["gcc", "-std=gnu99", "-O1", "-DNOX11", "-DNO_GBM", "-DEGL_NO_X11",
                                "-ffunction-sections", "-fdata-sections", "-I" + str(src / "include"),
                                "-I" + str(src), str(harness), str(src / "src/gl/fpe.c"),
                                "-Wl,--gc-sections", "-lm", "-o", str(exe)],
                               capture_output=True, text=True)
                self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
                result = subprocess.run([str(exe)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0 if fixed else 6, result.stdout + result.stderr)
            with self.assertRaises(ValueError):
                patch.apply_draw(src, ROOT / "graphics-compat/native/wurm_draw_trace.h")


if __name__ == "__main__":
    unittest.main()
