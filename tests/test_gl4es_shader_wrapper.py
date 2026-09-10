"""Reproduce the pinned native wrapper's disabled-conditional bug; no game assets."""
import ctypes
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("gl4es_patch", ROOT/"scripts/patch-gl4es.py")
patch = importlib.util.module_from_spec(spec); spec.loader.exec_module(patch)

@unittest.skipUnless(os.environ.get("WURM_GL4ES_ARCHIVE"), "pinned public GL4ES archive required")
class Gl4esShaderWrapperTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        archive = Path(os.environ["WURM_GL4ES_ARCHIVE"])
        assert hashlib.sha256(archive.read_bytes()).hexdigest() == "475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed"
        with tarfile.open(archive) as tar: tar.extractall(cls.home, filter="data")
        cls.src = cls.home/"gl4es-81547d986798e876de8b434193920b606a72363f"
        driver = cls.home/"driver.c"
        driver.write_text('''#include "src/gl/fpe_shader.h"
#include "src/gl/init.h"
#include "src/glx/hardext.h"
globals4es_t globals4es;
hardext_t hardext;
const char* wrap(const char* src, int enabled) {
 fpe_state_t state={0}; state.alphatest=enabled; state.alphafunc=FPE_GREATER;
 return *fpe_CustomFragmentShader(src,&state);
}''')
        cls.libs = []
        for name in ["old", "fixed"]:
            if name == "fixed": patch.apply(cls.src)
            dest = cls.home/(name+".so")
            subprocess.run(["gcc","-shared","-fPIC","-O1","-DNOX11","-DNO_GBM","-DEGL_NO_X11",
                "-I"+str(cls.src/"include"), "-I"+str(cls.src), str(driver),
                str(cls.src/"src/gl/fpe_shader.c"), str(cls.src/"src/gl/string_utils.c"),
                "-o", str(dest)], check=True)
            lib = ctypes.CDLL(str(dest)); lib.wrap.argtypes = [ctypes.c_char_p, ctypes.c_int]; lib.wrap.restype = ctypes.c_char_p
            cls.libs.append(lib)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def wrapped(self, fixed, shader, enabled=1):
        return self.libs[int(fixed)].wrap(shader.encode(), enabled).decode()

    def test_disabled_block_cannot_swallow_required_globals(self):
        shader = """precision mediump float;
#ifdef OPTIONAL_HELPER
float helper() { return 0.5; }
#endif
void main() { gl_FragColor = vec4(1.0); }
"""
        processed = []
        for fixed in [False, True]:
            wrapped = self.wrapped(fixed, shader)
            result = subprocess.run(["gcc","-E","-P","-x","c","-"], input=wrapped, text=True, capture_output=True, check=True)
            processed.append(result.stdout)
        self.assertNotIn("lowp vec4 _gl4es_FragColor;", processed[0])
        self.assertNotIn("uniform float _gl4es_AlphaRef;", processed[0])
        self.assertIn("lowp vec4 _gl4es_FragColor;", processed[1])
        self.assertIn("uniform float _gl4es_AlphaRef;", processed[1])
        self.assertIn("<= _gl4es_AlphaRef) discard;", processed[1])

    def test_no_alpha_test_preserves_original_shader(self):
        src="precision mediump float;\nvoid main() { gl_FragColor = vec4(1.0); }\n"
        self.assertEqual(src, self.wrapped(True, src, 0))

    def test_source_drift_is_rejected(self):
        with self.assertRaises(ValueError): patch.apply(self.src)

    def test_fixed_shader_compiles_on_real_gles_with_conditional_variants(self):
        # EGL uses Mesa's surfaceless software context on the host, never a fake GL compiler.
        os.environ.setdefault("EGL_PLATFORM", "surfaceless")
        os.environ.setdefault("LIBGL_ALWAYS_SOFTWARE", "1")
        try: egl = ctypes.CDLL("libEGL.so.1")
        except OSError: self.skipTest("Host EGL unavailable; native/preprocessor tests still required")
        def bind(name, result, *args):
            fn = getattr(egl, name); fn.restype = result; fn.argtypes = list(args); return fn
        ptr=ctypes.c_void_p; integer=ctypes.c_int
        display=bind("eglGetDisplay",ptr,ptr)(None)
        init=bind("eglInitialize",integer,ptr,ctypes.POINTER(integer),ctypes.POINTER(integer))
        if not init(display,None,None): self.skipTest("Host surfaceless EGL unavailable")
        bind("eglBindAPI",integer,ctypes.c_uint)(0x30A0)
        attrs=(integer*7)(0x3033,1,0x3040,4,0x3024,8,0x3038)
        config=ptr(); count=integer()
        self.assertTrue(bind("eglChooseConfig",integer,ptr,ctypes.POINTER(integer),ctypes.POINTER(ptr),integer,ctypes.POINTER(integer))(display,attrs,ctypes.byref(config),1,ctypes.byref(count)))
        self.assertGreater(count.value,0)
        ctxattrs=(integer*3)(0x3098,2,0x3038)
        context=bind("eglCreateContext",ptr,ptr,ptr,ptr,ctypes.POINTER(integer))(display,config,None,ctxattrs)
        self.assertTrue(context)
        self.assertTrue(bind("eglMakeCurrent",integer,ptr,ptr,ptr,ptr)(display,None,None,context))
        proc=bind("eglGetProcAddress",ptr,ctypes.c_char_p)
        def gl(name, result, *args): return ctypes.CFUNCTYPE(result,*args)(proc(name.encode()))
        create=gl("glCreateShader",ctypes.c_uint,ctypes.c_uint)
        source=gl("glShaderSource",None,ctypes.c_uint,integer,ctypes.POINTER(ctypes.c_char_p),ptr)
        compile_shader=gl("glCompileShader",None,ctypes.c_uint)
        status=gl("glGetShaderiv",None,ctypes.c_uint,ctypes.c_uint,ctypes.POINTER(integer))
        delete=gl("glDeleteShader",None,ctypes.c_uint)
        try:
            for defines in ["", "#define OPTIONAL_HELPER\n"]:
                for newline in ["\n","\r\n"]:
                    src=(defines+"precision mediump float;\n#ifdef OPTIONAL_HELPER\nfloat helper() { return .5; }\n#endif\nvoid main() { gl_FragColor = vec4(1.0); }\n").replace("\n",newline)
                    for fixed in [False,True]:
                        shader=create(0x8B30)
                        text=ctypes.c_char_p(self.wrapped(fixed,src).encode())
                        source(shader,1,ctypes.byref(text),None); compile_shader(shader)
                        ok=integer(); status(shader,0x8B81,ctypes.byref(ok)); delete(shader)
                        self.assertEqual(bool(ok.value), fixed or bool(defines), (fixed,defines,newline))
        finally:
            bind("eglMakeCurrent",integer,ptr,ptr,ptr,ptr)(display,None,None,None)
            bind("eglDestroyContext",integer,ptr,ptr)(display,context)
            bind("eglTerminate",integer,ptr)(display)

if __name__ == "__main__": unittest.main()
