"""Run the native callback against a deterministic GLES boundary without consuming errors."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
FIXTURE=r'''
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
typedef unsigned GLenum, GLuint;
typedef unsigned char GLboolean, GLubyte;
typedef int GLsizei;
#define GL_EXTENSIONS 0x1f03
#define GL_DONT_CARE 0x1100
#define GL_FALSE 0
#define GL_TRUE 1
static void *dlsym(void *,const char *);
static void (*eglGetProcAddress(const char *))(void);
#include "wurm_driver_debug.h"
#include "wurm_mipmap_trace.c"
static const char *extensions="";
static int controls,enables,registrations,missing;
static unsigned driver_error=0x502;
static wurm_debug_callback registered;
static const GLubyte *fake_string(GLenum n){assert(n==GL_EXTENSIONS);return (const GLubyte *)extensions;}
static void fake_enable(GLenum n){assert(n==0x8242 || n==0x92e0);enables++;}
static void fake_register(wurm_debug_callback cb,const void *p){assert(p==NULL);registered=cb;registrations++;}
static void fake_control(GLenum s,GLenum t,GLenum severity,GLsizei n,const GLuint *ids,GLboolean on){
 assert(severity==GL_DONT_CARE && n==0 && ids==NULL);
 if(!controls)assert(s==GL_DONT_CARE && t==GL_DONT_CARE && !on);
 else assert(s==0x8246 && (t==0x824c || t==0x824e) && on);
 controls++;
}
static void *dlsym(void *h,const char *n){assert(h==(void *)1);if(!strcmp(n,"glGetString"))return (void *)fake_string;if(!strcmp(n,"glEnable"))return (void *)fake_enable;assert(0);return NULL;}
static void (*eglGetProcAddress(const char *n))(void){
 if(missing)return NULL;
 if(!strcmp(n,"glDebugMessageCallbackKHR"))return (void (*)(void))fake_register;
 if(!strcmp(n,"glDebugMessageControlKHR"))return (void (*)(void))fake_control;
 assert(0);return NULL;
}
static void *other_thread(void *p){assert(!wurm_mipmap_current());return p;}
int main(void){
 assert(!wurm_has_extension(NULL,"GL_KHR_debug"));
 extensions="GL_KHR_debug_extra";wurm_install_driver_debug((void *)1);assert(!registrations);
 extensions="XGL_KHR_debug";wurm_install_driver_debug((void *)1);assert(!registrations);
 extensions="GL_X GL_KHR_debug GL_Y";missing=1;wurm_install_driver_debug((void *)1);assert(!registrations && !controls && !enables);
 missing=0;wurm_install_driver_debug((void *)1);assert(registrations==1 && controls==3 && enables==2);
 registered(0x8248,0x824c,1,0x9146,6,"shader",NULL); // No compiler text.
 registered(0x8246,0x8250,2,0x9147,4,"spam",NULL); // No performance chatter.
 assert(atomic_load(&wurm_driver_reports)==0);
 wurm_driver_mipmap_context=wurm_mipmap_current;
 wurm_mipmap_begin("fixture_generate",3553,42,3,6408,128,64,1,0);
 pthread_t thread;assert(!pthread_create(&thread,NULL,other_thread,NULL));assert(!pthread_join(thread,NULL));
 registered(0x8246,0x824c,3,0x9146,8,"bad\ncall",NULL);
 wurm_mipmap_end();
 registered(0x8246,0x824e,4,0x9146,-1,NULL,NULL);
 char long_message[800];memset(long_message,'x',sizeof(long_message));
 for(int i=0;i<100;i++)registered(0x8246,0x824c,5,0x9146,800,long_message,NULL);
 assert(atomic_load(&wurm_driver_reports)==64 && driver_error==0x502);
 puts("DRIVER_DEBUG_PASS errorsUnconsumed=true bounded=true optional=true");
}
'''

class DriverDebugTest(unittest.TestCase):
    def test_bounded_callback_gates_extension_and_preserves_pending_errors(self):
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'fixture.c';source.write_text(FIXTURE);exe=Path(folder)/'fixture'
            subprocess.run(['gcc','-std=c11','-D_POSIX_C_SOURCE=200809L','-Wall','-Wextra','-Werror','-pthread',
                '-I'+str(ROOT/'graphics-compat/native'),str(source),'-o',str(exe)],check=True)
            r=subprocess.run([str(exe)],capture_output=True,text=True,timeout=10)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            self.assertEqual(r.stdout.count('[graphics-driver] time='),64)
            self.assertEqual(r.stdout.count('DETAIL_LIMIT'),1)
            self.assertIn('message=bad call',r.stdout)
            self.assertEqual(r.stdout.count('mipmapSite='),1)
            self.assertIn('mipmapSite=fixture_generate target=0xde1 texture=42 unit=3 format=0x1908 size=128x64 valid=1 compressed=0',r.stdout)
            self.assertNotIn('message=shader',r.stdout);self.assertNotIn('message=spam',r.stdout)
            self.assertLess(max(map(len,r.stdout.splitlines())),700)
            self.assertIn('DRIVER_DEBUG_PASS',r.stdout)

if __name__=='__main__':unittest.main()
