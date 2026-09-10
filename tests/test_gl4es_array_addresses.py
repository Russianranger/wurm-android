"""Guard-page regression using actual public GL4ES setters and array converters."""
import hashlib
import importlib.util
import os
from pathlib import Path
import signal
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("gl4es_patch", ROOT / "scripts/patch-gl4es.py")
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

HARNESS = r'''
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "src/gl/glstate.h"
#include "src/gl/array.h"
#include "src/glx/hardext.h"
hardext_t hardext = {.maxvattrib = 16};
glstate_t *glstate;
// Only unsupported-type logging is outside the linked production units.
const char *PrintEnum(GLenum value) { return "unexpected enum"; }
void LogPrintf(const char *format, ...) { abort(); }
#define CHECK(c) do { if (!(c)) { fprintf(stderr, "failed line %d\n", __LINE__); return 1; } } while (0)
int main(int argc, char **argv) {
    glstate_t state = {0}; glvao_t vao = {0}; glbuffer_t buffer = {0};
    glstate = &state; state.vao = &vao;
    long page = sysconf(_SC_PAGESIZE);
    char *mapping = mmap(NULL, page*2, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    CHECK(mapping != MAP_FAILED);
    CHECK(mprotect(mapping+page, page, PROT_NONE) == 0);
    // Three 64-byte records. Normal at byte 28 occupies 12 bytes. Doubling
    // its offset makes the final copy read four bytes into the guard page.
    char *records = mapping + page - 3*64;
    memset(records, 0, 3*64);
    for (int i=0; i<3; ++i) {
        float normal[] = {i+1.0f, i+2.0f, i+3.0f};
        memcpy(records+i*64+28, normal, sizeof(normal));
    }
    buffer.data = records; buffer.size = 3*64; buffer.real_buffer = 9;
    vao.vertex = &buffer;
    gl4es_glNormalPointer(GL_FLOAT, 64, (void*)28);
    vertexattrib_t *normal = &vao.vertexattrib[ATT_NORMAL];
    float *copy = copy_gl_pointer_raw(normal, 3, 0, 3);
    CHECK(copy && copy[0] == 1 && copy[8] == 5);
    free(copy);
    // The fixed build continues with zero offsets, skips, client pointers,
    // generic attributes, no-allocation copies and replacement allocations.
    CHECK(normal->pointer == (void*)28 && normal->buffer == &buffer);
    float dest[9] = {0};
    copy_gl_pointer_raw_noalloc(dest, normal, 3, 1, 3);
    CHECK(dest[0] == 2 && dest[5] == 5);
    CHECK(gl_pointer_index(normal, 2)[2] == 5);
    copy = copy_gl_pointer(normal, 3, 1, 3);
    CHECK(copy && copy[0] == 2 && copy[5] == 5); free(copy);
    gl4es_glVertexPointer(3, GL_FLOAT, 64, NULL);
    CHECK(gl4es_pointer_address(&vao.vertexattrib[ATT_VERTEX]) == records);
    // All six legacy setters must capture the buffer with the original offset.
    gl4es_glColorPointer(4, GL_FLOAT, 64, (void*)12);
    gl4es_glSecondaryColorPointer(3, GL_FLOAT, 64, (void*)12);
    gl4es_glTexCoordPointer(4, GL_FLOAT, 64, (void*)40);
    gl4es_glFogCoordPointer(GL_FLOAT, 64, (void*)28);
    int slots[] = {ATT_VERTEX, ATT_COLOR, ATT_SECONDARY, ATT_NORMAL, ATT_MULTITEXCOORD0, ATT_FOGCOORD};
    int offsets[] = {0, 12, 12, 28, 40, 28};
    for (int i=0; i<6; ++i) {
        vertexattrib_t *p = &vao.vertexattrib[slots[i]];
        CHECK(p->buffer == &buffer && (uintptr_t)p->pointer == offsets[i]);
        CHECK(gl4es_pointer_address(p) == records+offsets[i]);
    }
    vertexattrib_t generic = {.pointer=(void*)28, .buffer=&buffer, .real_pointer=(void*)28,
                             .size=3, .type=GL_FLOAT, .stride=64};
    copy = copy_gl_pointer_raw(&generic, 3, 0, 3);
    CHECK(copy && copy[8] == 5); free(copy);
    char replacement[3*64]; memcpy(replacement, records, sizeof(replacement));
    buffer.data = replacement;
    CHECK(mprotect(mapping, page, PROT_NONE) == 0);
    copy = copy_gl_pointer_raw(normal, 3, 0, 3);
    CHECK(copy && copy[8] == 5); free(copy);
    // Unbinding for a new host pointer must clear a stale captured buffer.
    vao.vertex = NULL;
    float host[] = {6,7,8};
    gl4es_glNormalPointer(GL_FLOAT, 0, host);
    CHECK(normal->buffer == NULL && normal->real_pointer == NULL);
    // Compiled client arrays may have a GLES offset; it is not a CPU offset.
    normal->real_pointer = (void*)28; normal->real_buffer = 10;
    copy = copy_gl_pointer_raw(normal, 3, 0, 1);
    CHECK(copy && copy[0] == 6 && copy[2] == 8); free(copy);
    CHECK(munmap(mapping, page*2) == 0);
    puts("ARRAY_ADDRESSES_PASS guard-page, skips, zero-offset, reallocation, client and generic pointers");
    return 0;
}
'''


@unittest.skipUnless(os.environ.get("WURM_GL4ES_ARCHIVE"), "pinned public GL4ES archive required")
class Gl4esArrayAddressesTest(unittest.TestCase):
    def test_normal_copy_does_not_add_vbo_offset_twice(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            archive = Path(os.environ["WURM_GL4ES_ARCHIVE"])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                             "475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed")
            with tarfile.open(archive) as tar:
                tar.extractall(home, filter="data")
            src = home / "gl4es-81547d986798e876de8b434193920b606a72363f"
            # The original build only needs to reach the reproducing copy;
            # fixed-only assertions use the new address helper.
            for fixed in (False, True):
                if fixed:
                    patch.apply_array_addresses(src)
                harness = home / "probe.c"
                code = HARNESS if fixed else HARNESS[:HARNESS.index("    // The fixed build")] + "return 0; }"
                harness.write_text(code)
                exe = home / ("fixed" if fixed else "original")
                build = subprocess.run(["gcc", "-std=gnu99", "-O1", "-DNOX11", "-DNO_GBM", "-DEGL_NO_X11",
                    "-ffunction-sections", "-fdata-sections", "-I"+str(src/"include"), "-I"+str(src),
                    str(harness), str(src/"src/gl/gl4es.c"), str(src/"src/gl/array.c"),
                    "-Wl,--gc-sections", "-lm", "-o", str(exe)], capture_output=True, text=True)
                self.assertEqual(build.returncode, 0, build.stdout+build.stderr)
                result = subprocess.run([str(exe)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0 if fixed else -signal.SIGSEGV,
                                 result.stdout+result.stderr)
            with self.assertRaises(ValueError):
                patch.apply_array_addresses(src)


if __name__ == "__main__":
    unittest.main()
