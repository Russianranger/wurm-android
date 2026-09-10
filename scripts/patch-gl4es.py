#!/usr/bin/env python3
"""Checked downstream fixes for the pinned GL4ES source."""
from pathlib import Path
import hashlib

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
