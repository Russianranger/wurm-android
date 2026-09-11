"""Exercise config fallback and the pinned GL4ES depth-format conversion decisions."""
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class DepthConfigTest(unittest.TestCase):
    def test_preferred_config_fallback_and_failure_do_not_accept_missing_depth(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'config.c'
            source.write_text(r'''
#include <assert.h>
typedef int EGLint; typedef int EGLDisplay; typedef int EGLConfig;
enum { EGL_NONE, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
 EGL_RED_SIZE, EGL_GREEN_SIZE, EGL_BLUE_SIZE, EGL_ALPHA_SIZE, EGL_DEPTH_SIZE };
static int available, fail, wrong, calls;
static int eglChooseConfig(int display,const int *attrs,int *config,int size,int *count) {
 assert(display==1 && size==1); ++calls;
 int depth=0; for(int i=0;attrs[i]!=EGL_NONE;i+=2) if(attrs[i]==EGL_DEPTH_SIZE) depth=attrs[i+1];
 assert(depth==24 || depth==16); *count=available>=depth; *config=available; return !fail;
}
static int eglGetConfigAttrib(int d,int config,int attr,int *value) {
 assert(d==1 && attr==EGL_DEPTH_SIZE); *value=wrong ? 0 : config; return 1;
}
#include "wurm_depth_config.h"
int main(void) {
 int config,depth;
 available=24; assert(wurm_choose_depth_config(1,&config,&depth) && depth==24 && calls==1);
 calls=0; available=32; assert(wurm_choose_depth_config(1,&config,&depth) && depth==32 && calls==1);
 calls=0; available=16; assert(wurm_choose_depth_config(1,&config,&depth) && depth==16 && calls==2);
 calls=0; available=0; assert(!wurm_choose_depth_config(1,&config,&depth) && calls==2);
 calls=0; fail=1; assert(!wurm_choose_depth_config(1,&config,&depth) && calls==1);
 fail=0; available=24; wrong=1; assert(!wurm_choose_depth_config(1,&config,&depth));
}
''')
            exe=Path(tmp)/'config'
            subprocess.run(['gcc','-std=c99','-Wall','-Wextra','-Werror','-I'+str(ROOT/'graphics-compat/native'),str(source),'-o',str(exe)],check=True)
            subprocess.run([str(exe)],check=True,timeout=5)

@unittest.skipUnless(os.environ.get('WURM_GL4ES_ARCHIVE'), 'pinned GL4ES archive required')
class DepthFormatsTest(unittest.TestCase):
    def test_actual_conversion_preserves_explicit_formats_and_depth16_fallback(self):
        spec=importlib.util.spec_from_file_location('depth_patch',ROOT/'scripts/patch-gl4es.py')
        patch=importlib.util.module_from_spec(spec); spec.loader.exec_module(patch)
        with tempfile.TemporaryDirectory() as tmp:
            archive=Path(os.environ['WURM_GL4ES_ARCHIVE'])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),'475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed')
            with tarfile.open(archive) as tar: tar.extractall(tmp,filter='data')
            src=Path(tmp)/'gl4es-81547d986798e876de8b434193920b606a72363f'
            old=(src/'src/gl/texture.c').read_text()
            patch.apply_depth_precision(src)
            for fixed in (False,True):
                text=(src/'src/gl/texture.c').read_text() if fixed else old
                conversion=re.search(r'dest_type=\(\*format==GL_DEPTH_COMPONENT32.*?;',text).group()
                code=r'''
#include <assert.h>
enum { GL_DEPTH_COMPONENT=6402, GL_DEPTH_COMPONENT16=33189, GL_DEPTH_COMPONENT24=33190,
 GL_DEPTH_COMPONENT32=33191, GL_UNSIGNED_SHORT=5123, GL_UNSIGNED_INT=5125 };
struct { int depth24; } hardext;
static int texture(int requested,int supported) { int *format=&requested,dest_type; hardext.depth24=supported;
'''+conversion+r'''
return dest_type; }
int main(void) {
 assert(texture(GL_DEPTH_COMPONENT,0)==GL_UNSIGNED_SHORT);
 assert(texture(GL_DEPTH_COMPONENT16,1)==GL_UNSIGNED_SHORT);
 assert(texture(GL_DEPTH_COMPONENT24,1)==GL_UNSIGNED_INT);
 assert(texture(GL_DEPTH_COMPONENT32,1)==GL_UNSIGNED_INT);
 assert(texture(GL_DEPTH_COMPONENT,1)=='''+('GL_UNSIGNED_INT' if fixed else 'GL_UNSIGNED_SHORT')+r''');
}
'''
                path=Path(tmp)/'formats.c'; path.write_text(code); exe=Path(tmp)/'formats'
                subprocess.run(['gcc','-std=c99','-Wall','-Wextra','-Werror',str(path),'-o',str(exe)],check=True)
                subprocess.run([str(exe)],check=True,timeout=5)
            # Verify the actual renderbuffer normalization separately; color and sized formats remain intact.
            text=(src/'src/gl/framebuffers.c').read_text()
            normalization=re.search(r'if \(internalformat == GL_DEPTH_COMPONENT\)\s+internalformat = hardext.depth24 \? GL_DEPTH_COMPONENT24 : GL_DEPTH_COMPONENT16;',text).group()
            path.write_text('''#include <assert.h>
enum {GL_DEPTH_COMPONENT=6402,GL_DEPTH_COMPONENT16=33189,GL_DEPTH_COMPONENT24=33190};
struct {int depth24;} hardext;
int normalize(int internalformat,int supported) {hardext.depth24=supported;
'''+normalization+'''
return internalformat;}
int main(void) {
 assert(normalize(GL_DEPTH_COMPONENT,1)==GL_DEPTH_COMPONENT24);
 assert(normalize(GL_DEPTH_COMPONENT,0)==GL_DEPTH_COMPONENT16);
 assert(normalize(GL_DEPTH_COMPONENT16,1)==GL_DEPTH_COMPONENT16);
 assert(normalize(6408,1)==6408);
}
''')
            subprocess.run(['gcc','-std=c99','-Wall','-Wextra','-Werror',str(path),'-o',str(exe)],check=True)
            subprocess.run([str(exe)],check=True,timeout=5)
            with self.assertRaises(ValueError): patch.apply_depth_precision(src)
