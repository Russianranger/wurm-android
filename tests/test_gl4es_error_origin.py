"""Attribution must not consume an extra GL error or change existing error semantics."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('error_patch', ROOT/'scripts/patch-gl4es.py')
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

PREFIX = r'''
#include <stdio.h>
#include <assert.h>
typedef unsigned GLenum;
#define GL_NO_ERROR 0
#define APIENTRY_GL4ES
#define DBG(x)
#define LOAD_GLES(x)
static struct { int type_error; GLenum shim_error; } state, *glstate=&state;
static struct { int noerror; } globals4es;
static unsigned driver_error, queries;
static unsigned gles_glGetError(void) { queries++; unsigned result=driver_error; driver_error=0; return result; }
'''
MAIN = r'''
static void firstProducer(void) { errorShim(0x502); }
static void secondProducer(void) { errorShim(0x501); }
static void driverCandidate(void) { errorGL(); }
int main(void) {
 for (int t=0;t<3;t++) for(int s=0;s<2;s++) for(int d=0;d<2;d++) {
  state.type_error=t; state.shim_error=s?0x501:0; driver_error=d?0x502:0; queries=0;
  unsigned result=gl4es_glGetError();
  printf("RESULT %d %d %d %u %u %d %u\n",t,s,d,result,queries,state.type_error,state.shim_error);
 }
 state.type_error=2; state.shim_error=0;
 firstProducer(); secondProducer();
 assert(gl4es_glGetError()==0x502); // first stored error retains its origin
 state.type_error=2; state.shim_error=0;
 unsigned once=0x501; errorShim(once++); assert(once==0x502); assert(gl4es_glGetError()==0x501);
 state.type_error=2; state.shim_error=0;
 driverCandidate(); driver_error=0x502; queries=0;
 assert(gl4es_glGetError()==0x502 && queries==1);
 for (int i=0;i<1000;i++) { state.type_error=2; firstProducer(); assert(gl4es_glGetError()==0x502); }
 assert(gl4es_glGetError()==0);
 puts("ERROR_ORIGIN_PASS");
}
'''

@unittest.skipUnless(os.environ.get('WURM_GL4ES_ARCHIVE'), 'pinned GL4ES archive required')
class ErrorOriginTest(unittest.TestCase):
    def test_existing_getter_and_helpers_preserve_semantics_and_bound_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            home=Path(tmp); archive=Path(os.environ['WURM_GL4ES_ARCHIVE'])
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                '475c30409fd64a649352487d0df0dc3f8ef200ea5c54b7de5f61d099948877ed')
            with tarfile.open(archive) as tar: tar.extractall(home, filter='data')
            src=home/'gl4es-81547d986798e876de8b434193920b606a72363f'
            results=[]
            for fixed in (False,True):
                if fixed: patch.apply_error_origin(src,ROOT/'graphics-compat/native')
                header=(src/'src/gl/gl4es.h').read_text(); getter=(src/'src/gl/getter.c').read_text()
                # Exact production helpers and getter, with a deterministic substitute driver.
                start=header.index('static inline void '+('wurm_original_errorGL' if fixed else 'errorGL'))
                helpers=header[start:header.index('static inline void noerrorShimNoPurge')]
                start=getter.index('GLenum APIENTRY_GL4ES gl4es_glGetError(void)')
                function=getter[start:getter.index('\nAliasExport(GLenum,glGetError',start)]
                source=home/'fixture.c'; exe=home/('fixed' if fixed else 'original')
                source.write_text(PREFIX+helpers+ ('\n#include "wurm_error_trace.c"\n' if fixed else '')+function+MAIN)
                subprocess.run(['gcc','-std=gnu99','-Wall','-Wextra','-Werror','-I'+str(src/'src/gl'),str(source),'-o',str(exe)],check=True)
                result=subprocess.run([str(exe)],capture_output=True,text=True,timeout=5)
                self.assertEqual(result.returncode,0,result.stdout+result.stderr)
                results.append(result.stdout)
            self.assertEqual([s for s in results[0].splitlines() if s.startswith('RESULT')],
                             [s for s in results[1].splitlines() if s.startswith('RESULT')])
            self.assertIn('source=shim origin=firstProducer:',results[1])
            self.assertNotIn('origin=secondProducer:',results[1])
            self.assertIn('source=driver candidates=recent-errorGL-sites-not-proof driverCandidate:',results[1])
            self.assertEqual(results[1].count('[graphics-error] time='),64)
            self.assertEqual(results[1].count('DETAIL_LIMIT'),1)
