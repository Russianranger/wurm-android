/* Real EGL/GLES driver + pinned GL4ES: compare delayed captures byte for byte.
 * This proves host correctness, not Adreno timing or gameplay qualification. */
#include <assert.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <EGL/egl.h>
#include "wurm_readback.h"
static int width=64,height=32;
static void dimensions(int *w,int *h){*w=width;*h=height;}
#define LOAD(type,name) type name=(type)dlsym(backend,#name); assert(name)
typedef void (*init_fn)(void);
typedef void (*size_fn)(void (*)(int *,int *));
typedef void (*clear_color_fn)(GLfloat,GLfloat,GLfloat,GLfloat);
typedef void (*enum_fn)(GLenum);
typedef void (*rect_fn)(GLint,GLint,GLsizei,GLsizei);
typedef void (*get_fn)(GLenum,GLint *);
typedef void (*store_fn)(GLenum,GLint);
typedef void (*read_fn)(GLint,GLint,GLsizei,GLsizei,GLenum,GLenum,void *);
typedef GLenum (*error_fn)(void);
int main(int argc,char **argv){
 assert(argc==2);
 void *driver=dlopen(getenv("LIBGL_GLES"),RTLD_NOW|RTLD_LOCAL);assert(driver);
 void *backend=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);if(!backend){puts(dlerror());return 1;}
 LOAD(init_fn,initialize_gl4es);LOAD(size_fn,set_getmainfbsize);set_getmainfbsize(dimensions);initialize_gl4es();
 EGLDisplay d=eglGetDisplay(EGL_DEFAULT_DISPLAY);EGLint major,minor,count;EGLConfig config;
 assert(eglInitialize(d,&major,&minor));assert(eglBindAPI(EGL_OPENGL_ES_API));
 EGLint attr[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_DEPTH_SIZE,24,EGL_NONE};
 assert(eglChooseConfig(d,attr,&config,1,&count)&&count);
 EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE},sa[]={EGL_WIDTH,width,EGL_HEIGHT,height,EGL_NONE};
 EGLContext ctx=eglCreateContext(d,config,EGL_NO_CONTEXT,ca);assert(ctx!=EGL_NO_CONTEXT);
 EGLSurface surface=eglCreatePbufferSurface(d,config,sa);assert(surface!=EGL_NO_SURFACE);assert(eglMakeCurrent(d,surface,surface,ctx));
 LOAD(clear_color_fn,glClearColor);LOAD(enum_fn,glClear);LOAD(enum_fn,glEnable);LOAD(enum_fn,glDisable);
 LOAD(rect_fn,glScissor);LOAD(read_fn,glReadPixels);LOAD(error_fn,glGetError);
 store_fn native_store=(store_fn)dlsym(driver,"glPixelStorei");get_fn native_get=(get_fn)dlsym(driver,"glGetIntegerv");
 assert(wurm_readback_open(driver,backend,width,height)==1);
 unsigned char expected[64*32*4],actual[64*32*4];
 for(int frame=0;frame<40;frame++){
  glDisable(GL_SCISSOR_TEST);glClearColor((frame%3)/2.f,(frame%5)/4.f,(frame%7)/6.f,1);glClear(GL_COLOR_BUFFER_BIT);
  glEnable(GL_SCISSOR_TEST);glScissor(frame%32,frame%16,7,9);glClearColor(1,0,1,1);glClear(GL_COLOR_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);
  glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,expected);assert(glGetError()==0);
  native_store(GL_PACK_ROW_LENGTH,93);native_store(GL_PACK_SKIP_ROWS,2);native_store(GL_PACK_SKIP_PIXELS,3);native_store(GL_PACK_ALIGNMENT,8);
  assert(!wurm_readback_issue());
  GLint v;native_get(GL_PACK_ROW_LENGTH,&v);assert(v==93);native_get(GL_PACK_SKIP_ROWS,&v);assert(v==2);native_get(GL_PACK_SKIP_PIXELS,&v);assert(v==3);native_get(GL_PACK_ALIGNMENT,&v);assert(v==8);
  /* Modify the framebuffer after queuing: collected bytes must still represent
     the captured frame, not the later image. */
  glClearColor(0,1,0,1);glClear(GL_COLOR_BUFFER_BIT);
  assert(!wurm_readback_collect(actual,sizeof(actual)));assert(!memcmp(expected,actual,sizeof(actual)));
  native_store(GL_PACK_ROW_LENGTH,0);native_store(GL_PACK_SKIP_ROWS,0);native_store(GL_PACK_SKIP_PIXELS,0);native_store(GL_PACK_ALIGNMENT,4);
  assert(glGetError()==0);
 }
 wurm_readback_close();assert(wurm_readback_open(driver,backend,width,height)==1);wurm_readback_close();
 assert(eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT));assert(eglDestroySurface(d,surface));assert(eglDestroyContext(d,ctx));assert(eglTerminate(d));
 puts("READBACK_REAL_GL4ES_MESA_PASS frames=40 exact_rgba=true state_restored=true");
}
