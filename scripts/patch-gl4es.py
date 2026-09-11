#!/usr/bin/env python3
"""Checked downstream fixes for the pinned GL4ES source."""
from pathlib import Path
import hashlib

def patch_error_origin(header: str, getter: str):
    replacements = [
        ('static inline void errorGL() {', 'static inline void wurm_original_errorGL() {'),
        ('static inline void errorShim(GLenum error) {', 'static inline void wurm_original_errorShim(GLenum error) {'),
        ('static inline void noerrorShim() {', '''#include "wurm_error_trace.h"
#define errorGL() do { wurm_error_driver(__func__, __LINE__); wurm_original_errorGL(); } while (0)
#define errorShim(error) do { \\
    GLenum wurm_error_value = (error); \\
    if (glstate->shim_error == GL_NO_ERROR && wurm_error_value != GL_NO_ERROR) wurm_error_shim(__func__, __LINE__); \\
    wurm_original_errorShim(wurm_error_value); \\
} while (0)
static inline void noerrorShim() {'''),
    ]
    for old, new in replacements:
        if header.count(old) != 1: raise ValueError('Pinned GL4ES error helper changed')
        header = header.replace(old, new)
    start = getter.index('GLenum APIENTRY_GL4ES gl4es_glGetError(void) {')
    end = getter.index('\nAliasExport(GLenum,glGetError', start)
    body = getter[start:end]
    for old, new in [
        ('GLenum err = GL_NO_ERROR;', 'GLenum err = GL_NO_ERROR;\n    int wurm_from_driver = 0;'),
        ('err = gles_glGetError();', 'err = gles_glGetError();\n        wurm_from_driver = (err != GL_NO_ERROR);'),
        ('return err;', 'wurm_error_observed(err, wurm_from_driver);\n\treturn err;'),
    ]:
        if body.count(old) != 1: raise ValueError('Pinned GL4ES error getter changed')
        body = body.replace(old, new)
    return header, getter[:start] + '#include "wurm_error_trace.c"\n' + body + getter[end:]

def apply_error_origin(root: Path, native: Path):
    gl = root / 'src/gl'
    pins = {'gl4es.h': '9bbf9bbeb9d3a8117934bfb2e0af666c792b93e0ea5a6a194860fed86b2b5dcb',
            'getter.c': 'bc287740a3656c650f26a6629d9f0138bca965beb1959b3410bd812f8c759500'}
    for name, digest in pins.items():
        if hashlib.sha256((gl/name).read_bytes()).hexdigest() != digest:
            raise ValueError('Unexpected GL4ES ' + name)
    header, getter = patch_error_origin((gl/'gl4es.h').read_text(), (gl/'getter.c').read_text())
    (gl/'gl4es.h').write_text(header)
    (gl/'getter.c').write_text(getter)
    for name in ('wurm_error_trace.h', 'wurm_error_trace.c'):
        (gl/name).write_bytes((native/name).read_bytes())

def patch_shader(source: str) -> str:
    old = """            int l_main = gl4es_getline_for(shad, gl4es_prev_str(shad, strstr(shad, "_gl4es_main"))) - 1;
            shad = gl4es_inplace_insert(gl4es_getline(shad, l_main), "lowp vec4 _gl4es_FragColor;\\n", shad, &shad_cap);"""
    new = """            // Insert at the main declaration, not the preceding line (which may be #endif).
            char* main_decl = gl4es_prev_str(shad, strstr(shad, "_gl4es_main"));
            shad = gl4es_inplace_insert(main_decl, "lowp vec4 _gl4es_FragColor;\\n", shad, &shad_cap);"""
    alpha_old = "shad = gl4es_inplace_insert(gl4es_getline(shad, headline), gl4es_alphaRefSource, shad, &shad_cap);"
    alpha_new = """// Recompute after earlier insertions; a saved line number may now lie inside a conditional.
                shad = gl4es_inplace_insert(gl4es_prev_str(shad, strstr(shad, "_gl4es_main")), gl4es_alphaRefSource, shad, &shad_cap);"""
    # The alpha insertion also exists in other shader generators; change only the custom fragment wrapper.
    start = source.index("const char* const* fpe_CustomFragmentShader(")
    prefix, custom = source[:start], source[start:]
    if custom.count(old) != 1 or custom.count(alpha_old) != 1:
        raise ValueError("Pinned GL4ES custom fragment wrapper changed")
    return prefix + custom.replace(old, new).replace(alpha_old, alpha_new)

def apply(root: Path):
    target = root / "src/gl/fpe_shader.c"
    original = target.read_bytes()
    # Whole-source pin supplements the archive checksum enforced by the caller.
    expected = "d2145bee2b8f22210c47585d82e9b8d019cf905ec3b5fb5e4a3b7cafcc9607c5"
    if hashlib.sha256(original).hexdigest() != expected:
        raise ValueError("Unexpected GL4ES fpe_shader.c")
    target.write_text(patch_shader(original.decode()))


def patch_draw(source: str) -> str:
    marker = '#include "fpe.h"'
    if source.count(marker) != 1:
        raise ValueError("Pinned GL4ES fpe header changed")
    source = source.replace(marker, marker + '\n#include "wurm_draw_trace.h"')
    for signature, call, before in [
        ("void APIENTRY_GL4ES fpe_glDrawArrays(", "gles_glDrawArrays(mode, first, count);",
         "wurm_trace_begin(1, mode, first, count, 0, NULL);"),
        ("void APIENTRY_GL4ES fpe_glDrawElements(", "gles_glDrawElements(mode, count, type, indices);",
         "wurm_trace_begin(2, mode, 0, count, type, indices);"),
    ]:
        start = source.index(signature)
        end = source.index("\n}", start)
        method = source[start:end]
        if method.count(call) != 1: raise ValueError("Pinned draw delegate changed")
        source = source[:start] + method.replace(call, before + "\n    " + call + "\n    wurm_trace_end();") + source[end:]
    return source

def patch_client_pointers(source: str) -> str:
    # Internal FPE setters receive absolute host addresses from render lists.
    # Public glVertexAttribPointer still captures the VBO for genuine offsets.
    for name in ("SecondaryColor", "Vertex", "Color", "Normal", "TexCoord", "FogCoord"):
        suffix = "TMU" if name == "TexCoord" else ""
        start = source.index(f"void APIENTRY_GL4ES fpe_gl{name}Pointer{suffix}(")
        end = source.index("\n}", start)
        method = source[start:end]
        old = ".buffer = glstate->vao->vertex;"
        if method.count(old) != 1:
            raise ValueError(f"Pinned GL4ES {name} client pointer changed")
        method = method.replace(old, ".buffer = NULL; // Internal pointer is already an absolute host address.")
        source = source[:start] + method + source[end:]
    return source

def apply_draw(root: Path, header: Path):
    target = root / "src/gl/fpe.c"
    original = target.read_bytes()
    if hashlib.sha256(original).hexdigest() != "48f8c2c30f7e8b86f02258ef02d10e99067e3d51cad6b25ea05c258826d01d47":
        raise ValueError("Unexpected GL4ES fpe.c")
    target.write_text(patch_draw(patch_client_pointers(original.decode())))
    (target.parent / "wurm_draw_trace.h").write_bytes(header.read_bytes())


def apply_array_addresses(root: Path):
    """Keep VAO pointers as offset+buffer; resolve CPU addresses exactly once.

    The upstream legacy setter stores base+offset but its copy helpers add the
    offset again. Generic setters already use offset+buffer. Update every CPU
    consumer of the legacy representation, including selection and GLES1.
    """
    pins = {
        "gl4es.c": "354a3407e27b3b71af94e9fbe591c25631f488dc2f1a06a9adb59de42c0586f4",
        "array.c": "2aa8f7f022902d03f773b6e8728db7deae62f823e70fa53c76e34861cd841e53",
        "array.h": "d315361ea20947274826aac56b178a3bf43f97a20aef98ac8246d5ee5594e17a",
        "drawing.c": "d6bb4c02c7c9f18e5f155ac023cd75cbcf080352026da97f0840e3d37c366543",
        "render.c": "b796796f8d7e8989bbba1e9ed3688b18c5c321f931195f94995fc0a826cc0aaa",
        "texture_params.c": "cfec863c617567723aa4251fe78f4646b96317c474a6d920b85abd62b3a2bcfd",
    }
    sources = {}
    for name, digest in pins.items():
        data = (root / "src/gl" / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError(f"Unexpected GL4ES {name}")
        sources[name] = data.decode()

    def replace(name, old, new, count):
        if sources[name].count(old) != count:
            raise ValueError(f"Pinned GL4ES address consumer changed: {name}: {old}")
        sources[name] = sources[name].replace(old, new)

    replace("array.h", '#include "gles.h"', '''#include "gles.h"
#include <stdint.h>

// CPU data comes from the currently captured buffer allocation, never from
// real_pointer (which is an offset into a GLES buffer, including locked arrays).
static inline const GLvoid *gl4es_pointer_address(const vertexattrib_t *p) {
    return (const GLvoid*)((uintptr_t)p->pointer +
                          (p->buffer ? (uintptr_t)p->buffer->data : 0));
}''', 1)
    replace("gl4es.c", "t.pointer = (void*)((char*)pointer + (uintptr_t)((glstate->vao->vertex)?glstate->vao->vertex->data:0));",
            "t.pointer = pointer; t.buffer = glstate->vao->vertex;", 1)
    replace("array.c", "(const GLvoid*)((uintptr_t)ptr->pointer+(uintptr_t)ptr->real_pointer)",
            "gl4es_pointer_address(ptr)", 10)
    replace("array.c", "(uintptr_t)p->pointer+(uintptr_t)p->real_pointer",
            "(uintptr_t)gl4es_pointer_address(p)", 1)
    # glArrayElement direct reads and compiled-array upload/range calculations.
    replace("gl4es.c", "p->pointer", "gl4es_pointer_address(p)", 12)
    replace("gl4es.c", "glstate->vao->vertexattrib[ATT_VERTEX].pointer",
            "gl4es_pointer_address(&glstate->vao->vertexattrib[ATT_VERTEX])", 1)
    replace("gl4es.c", "glstate->vao->vertexattrib[i].pointer",
            "gl4es_pointer_address(&glstate->vao->vertexattrib[i])", 2)
    for attr in ("ATT_COLOR", "ATT_SECONDARY"):
        replace("drawing.c", f"glstate->vao->vertexattrib[{attr}].pointer",
                f"gl4es_pointer_address(&glstate->vao->vertexattrib[{attr}])", 3)
    replace("drawing.c", "p->pointer", "gl4es_pointer_address(p)", 3)
    replace("render.c", "vtx->pointer", "gl4es_pointer_address(vtx)", 4)
    replace("texture_params.c", "ptr->pointer", "gl4es_pointer_address(ptr)", 1)
    for name, source in sources.items():
        (root / "src/gl" / name).write_text(source)


def apply_program_cleanup(root: Path):
    """Free shader-cache values, then clear their maps without treating keys as slots."""
    target = root / "src/gl/program.c"
    original = target.read_bytes()
    if hashlib.sha256(original).hexdigest() != "7e0ea0a37737555ec3eb3cfeccd808f6a910fc52769c8ce64a8de73e14432f2b":
        raise ValueError("Unexpected GL4ES program.c")
    source = original.decode()
    start = source.index("static void clear_program(program_t *glprogram)")
    end = source.index("\nstatic void fill_program(", start)
    source = source[:start] + '''static void clear_program(program_t *glprogram)
{
    // kh_foreach returns keys, not bucket indices. Uniform locations may be
    // larger than the table, so passing them to kh_del corrupts its flags.
    // Free each value exactly once before clearing the table's own buckets.
    if(glprogram->attribloc) {
        attribloc_t *m;
        // glname aliases name and must not be freed separately.
        kh_foreach_value(glprogram->attribloc, m,
            free(m->name); free(m);
        )
        kh_clear(attribloclist, glprogram->attribloc);
    }
    glprogram->num_uniform = 0;
    if(glprogram->uniform) {
        uniform_t *m;
        kh_foreach_value(glprogram->uniform, m,
            free(m->name); free(m);
        )
        kh_clear(uniformlist, glprogram->uniform);
    }
    glprogram->cache.size = 0;
}
''' + source[end:]
    target.write_text(source)


def apply_depth_precision(root: Path):
    """Preserve explicit formats; prefer supported 24-bit depth for unsized Wurm targets."""
    gl = root / "src/gl"
    pins = {'texture.c': '72b4bc006a4e6792b2662ed4b3784ed069ac8abe9db8984549cef9ee7d964295', 'framebuffers.c': '7e1900d7b85f4fc423661f03455b2bbdd55046c9f1ae05bc5223c1991ae22c69'}
    for name, digest in pins.items():
        if hashlib.sha256((gl/name).read_bytes()).hexdigest() != digest:
            raise ValueError("Unexpected GL4ES depth source " + name)
    texture = (gl/"texture.c").read_text()
    old = "dest_type=(*format==GL_DEPTH_COMPONENT32 || *format==GL_DEPTH_COMPONENT24)?GL_UNSIGNED_INT:GL_UNSIGNED_SHORT;"
    new = "dest_type=(*format==GL_DEPTH_COMPONENT32 || *format==GL_DEPTH_COMPONENT24 || (*format==GL_DEPTH_COMPONENT && hardext.depth24))?GL_UNSIGNED_INT:GL_UNSIGNED_SHORT;"
    if texture.count(old) != 1: raise ValueError("Depth texture conversion changed")
    texture = texture.replace(old, new)
    framebuffer = (gl/"framebuffers.c").read_text()
    old = "    GLenum format = internalformat;"
    new = old + "\n    if (internalformat == GL_DEPTH_COMPONENT)\n        internalformat = hardext.depth24 ? GL_DEPTH_COMPONENT24 : GL_DEPTH_COMPONENT16;"
    if framebuffer.count(old) != 1: raise ValueError("Depth renderbuffer conversion changed")
    framebuffer = framebuffer.replace(old, new)
    (gl/"texture.c").write_text(texture)
    (gl/"framebuffers.c").write_text(framebuffer)
