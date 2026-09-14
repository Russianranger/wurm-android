"""Execute the pinned GL4ES realization function against a recorded GLES boundary."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('mipmap_patch', ROOT/'scripts/patch-gl4es.py')
patch = importlib.util.module_from_spec(spec); spec.loader.exec_module(patch)

HEADER = r'''
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "src/gl/glstate.h"
#include "src/gl/init.h"
#include "src/glx/hardext.h"
#include "wurm_mipmap_trace.c"
#undef LOAD_GLES
#define LOAD_GLES(n)
#undef LOAD_GLES2_OR_OES
#define LOAD_GLES2_OR_OES(n)
#define DBG(n)
glstate_t *glstate;
globals4es_t globals4es;
hardext_t hardext;
static glstate_t state;
static gleshard_t actual;
static GLuint actual_bindings[2];
static gltexture_t textures[2][ENABLED_TEXTURE_LAST];
static int calls, active, last_unit, driver_error, gen_target;
static void gles_glActiveTexture(GLenum target) { active=target-GL_TEXTURE0; }
static void gles_glBindTexture(GLenum target, GLuint name) { (void)target; (void)name; }
static void gles_glGenerateMipmap(GLenum target) {
    calls++; last_unit=active; gen_target=target;
    if(!textures[active][ENABLED_TEX2D].valid) driver_error=GL_INVALID_OPERATION;
    if(FIXED) {
        const wurm_mipmap_site *site=wurm_mipmap_current();
        assert(site && !strcmp(site->site,"realize_textures") && site->unit==(unsigned)active && site->target==target);
    }
}
static int is_mipmap_needed(glsampler_t *s) {return s->min_filter==GL_LINEAR_MIPMAP_LINEAR;}
void realize_1texture(GLenum target,int unit,gltexture_t *tex,glsampler_t *sampler) {
    (void)target; (void)unit; (void)tex; (void)sampler;
}
static void reset(void) {
    memset(&state,0,sizeof(state)); memset(&actual,0,sizeof(actual));
    memset(textures,0,sizeof(textures)); memset(&globals4es,0,sizeof(globals4es));
    glstate=&state; state.gleshard=&actual; state.actual_tex2d=actual_bindings; hardext.esversion=2;
    calls=active=driver_error=0; last_unit=-1; gen_target=0;
    for(int i=0;i<2;i++) for(int t=0;t<ENABLED_TEXTURE_LAST;t++) {
        gltexture_t *tex=&textures[i][t]; tex->texture=tex->glname=10+i*10+t;
        tex->sampler.min_filter=GL_LINEAR_MIPMAP_LINEAR; tex->width=tex->height=64;
        state.texture.bound[i][t]=tex;
    }
    for(int i=0;i<2;i++) state.actual_tex2d[i]=textures[i][ENABLED_TEX2D].glname;
}
'''

MAIN = r'''
int main(void) {
    // Brand new texture/default image: upstream calls GLES and marks it done.
    reset(); state.bound_changed=1;
    realize_textures(1);
    assert(calls==(FIXED?0:1)); assert(driver_error==(FIXED?0:GL_INVALID_OPERATION));
    assert(textures[0][ENABLED_TEX2D].mipmap_done==!FIXED);
    // A subsequent upload must still be eligible; do not permanently disable it.
    if(FIXED) {
        textures[0][ENABLED_TEX2D].valid=1; state.bound_changed=1; realize_textures(1);
        assert(calls==1 && !driver_error && textures[0][ENABLED_TEX2D].mipmap_done);
        assert(!wurm_mipmap_current());
    }
    // Both bindings already match. Actual unit is 1; image to generate is on 0.
    reset(); textures[0][ENABLED_TEX2D].valid=1;
    textures[1][ENABLED_TEX2D].sampler.min_filter=GL_LINEAR;
    state.bound_changed=2; state.texture.active=1; actual.active=active=1;
    realize_textures(1);
    assert(calls==1 && last_unit==(FIXED?0:1));
    assert(driver_error==(FIXED?0:GL_INVALID_OPERATION));
    // Unit-local target selection and mapped generation target.
    reset(); state.bound_changed=2; state.texture.active=1;
    state.enable.texture[0]=1<<ENABLED_CUBE_MAP;
    textures[0][ENABLED_CUBE_MAP].valid=1;
    textures[1][ENABLED_TEX2D].sampler.min_filter=GL_LINEAR;
    realize_textures(1);
    assert(gen_target==(FIXED?GL_TEXTURE_CUBE_MAP:GL_TEXTURE_2D));
    // Existing policy exclusions remain, as does no-drawing realization.
    for(int mode=0;mode<7;mode++) {
        reset(); gltexture_t *tex=&textures[0][ENABLED_TEX2D]; tex->valid=1; state.bound_changed=1;
        if(mode==0) globals4es.automipmap=3;
        if(mode==1) tex->compressed=1;
        if(mode==2) tex->npot=1;
        if(mode==3) tex->mipmap_done=1;
        if(mode==4) tex->mipmap_auto=1;
        if(mode==5) tex->sampler.min_filter=GL_LINEAR;
        realize_textures(mode!=6); assert(!calls);
    }
    if(FIXED) {
        reset(); textures[0][ENABLED_TEX2D].valid=1; textures[0][ENABLED_TEX2D].width=0;
        state.bound_changed=1; realize_textures(1); assert(!calls);
    }
    puts(FIXED?"MIPMAP_FIXED_PASS":"MIPMAP_ORIGINAL_ERRORS_REPRODUCED");
}
'''

@unittest.skipUnless(os.environ.get('WURM_GL4ES_ARCHIVE'), 'pinned GL4ES archive required')
class MipmapTest(unittest.TestCase):
    def test_realization_defers_empty_images_and_generates_on_the_correct_unit(self):
        with tempfile.TemporaryDirectory() as tmp:
            home=Path(tmp); archive=Path(os.environ['WURM_GL4ES_ARCHIVE'])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                '475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed')
            with tarfile.open(archive) as tar: tar.extractall(home,filter='data')
            src=home/'gl4es-81547d986798e876de8b434193920b606a72363f'
            original=(src/'src/gl/texture_params.c').read_text()
            patch.apply_array_addresses(src); patch.apply_depth_precision(src)
            patch.apply_error_origin(src, ROOT/'graphics-compat/native')
            patch.apply_mipmap_realization(src, ROOT/'graphics-compat/native')
            for fixed in (False,True):
                text=(src/'src/gl/texture_params.c').read_text() if fixed else original
                body=text[text.index('void realize_textures(int drawing) {'):text.index('\n//Direct wrapper')]
                code=home/'fixture.c'; code.write_text('#define FIXED '+str(int(fixed))+'\n'+HEADER+body+MAIN)
                exe=home/'fixture'
                result=subprocess.run(['gcc','-std=gnu99','-O1','-DNOX11','-DNO_GBM','-DEGL_NO_X11',
                    '-I'+str(src/'include'),'-I'+str(src),'-I'+str(ROOT/'graphics-compat/native'),
                    str(code),'-o',str(exe)],capture_output=True,text=True)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)
                result=subprocess.run([str(exe)],capture_output=True,text=True,timeout=10)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)
                self.assertIn('MIPMAP_FIXED_PASS' if fixed else 'MIPMAP_ORIGINAL_ERRORS_REPRODUCED',result.stdout)
            with self.assertRaises(ValueError): patch.apply_mipmap_realization(src,ROOT/'graphics-compat/native')
