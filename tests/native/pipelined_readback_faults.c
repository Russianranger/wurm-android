#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static void *lookup(void *,const char *);
#define dlsym lookup
#include "wurm_readback.h"
#undef dlsym
static unsigned char storage[1280*1024*4];
static int version=3,missing,allocation_fail,map_fail,unmap_fail,read_fail;
static int binding=77,alignment=8,row=83,rows=2,pixels=3,emulated,allocated,deleted,issued,mapped;
static size_t bytes;
static GLenum error;
static void get(GLenum name,GLint *out){
 switch(name){case GL_PIXEL_PACK_BUFFER_BINDING:*out=binding;break;case GL_PACK_ALIGNMENT:*out=alignment;break;
 case GL_PACK_ROW_LENGTH:*out=row;break;case GL_PACK_SKIP_ROWS:*out=rows;break;case GL_PACK_SKIP_PIXELS:*out=pixels;break;default:assert(0);}
}
static void backend_get(GLenum name,GLint *out){assert(name==GL_PIXEL_PACK_BUFFER_BINDING);*out=emulated;}
static const GLubyte *string(GLenum name){assert(name==GL_VERSION);return (const GLubyte *)(version==3?"OpenGL ES 3.2 fixture":"OpenGL ES 2.0 fixture");}
static void gen(GLsizei n,GLuint *b){assert(n==1);*b=19;allocated++;}
static void del(GLsizei n,const GLuint *b){assert(n==1&&*b==19);deleted++;}
static void bind(GLenum target,GLuint b){assert(target==GL_PIXEL_PACK_BUFFER);binding=b;}
static void data(GLenum target,GLsizeiptr size,const void *p,GLenum usage){assert(target==GL_PIXEL_PACK_BUFFER&&binding==19&&!p&&usage==GL_STREAM_READ);bytes=size;if(allocation_fail)error=0x505;}
static void *map(GLenum target,GLintptr offset,GLsizeiptr size,GLbitfield flags){assert(target==GL_PIXEL_PACK_BUFFER&&binding==19&&offset==0&&(size_t)size==bytes&&flags==GL_MAP_READ_BIT);if(map_fail)return NULL;mapped++;return storage;}
static GLboolean unmap(GLenum target){assert(target==GL_PIXEL_PACK_BUFFER&&binding==19&&mapped);mapped--;return !unmap_fail;}
static void store(GLenum name,GLint value){switch(name){case GL_PACK_ALIGNMENT:alignment=value;break;case GL_PACK_ROW_LENGTH:row=value;break;case GL_PACK_SKIP_ROWS:rows=value;break;case GL_PACK_SKIP_PIXELS:pixels=value;break;default:assert(0);}}
static void flush(void){}
static GLenum get_error(void){GLenum e=error;error=0;return e;}
static void read_pixels(GLint x,GLint y,GLsizei w,GLsizei h,GLenum format,GLenum type,void *offset){
 assert(!x&&!y&&(size_t)(w*h*4)==bytes&&format==GL_RGBA&&type==GL_UNSIGNED_BYTE&&!offset);
 assert(binding==19&&alignment==1&&!row&&!rows&&!pixels);
 issued++;for(size_t i=0;i<bytes;i++)storage[i]=(unsigned char)(i*13+issued);
 if(read_fail)error=0x502;
}
static void *lookup(void *handle,const char *name){
 if(missing&&!strcmp(name,"glMapBufferRange"))return NULL;
 if(handle==(void *)2){if(!strcmp(name,"glGetIntegerv"))return backend_get;if(!strcmp(name,"glReadPixels"))return read_pixels;if(!strcmp(name,"glFlush"))return flush;assert(0);}
#define FN(n,f) if(!strcmp(name,n))return f;
 FN("glGetString",string) FN("glGetIntegerv",get) FN("glGenBuffers",gen) FN("glDeleteBuffers",del)
 FN("glBindBuffer",bind) FN("glBufferData",data) FN("glMapBufferRange",map) FN("glUnmapBuffer",unmap)
 FN("glPixelStorei",store) FN("glFlush",flush) FN("glGetError",get_error)
#undef FN
 assert(0);return NULL;
}
static void restored(void){assert(binding==77&&alignment==8&&row==83&&rows==2&&pixels==3&&!mapped);}
int main(void){
 unsigned char output[1280*1024*4];
 version=2;assert(wurm_readback_open((void *)1,(void *)2,16,16)==0);assert(!allocated);version=3;
 missing=1;assert(wurm_readback_open((void *)1,(void *)2,16,16)==0);assert(!allocated);missing=0;
 allocation_fail=1;assert(wurm_readback_open((void *)1,(void *)2,16,16)==-1);assert(allocated==deleted);restored();allocation_fail=0;
 for(int pass=0;pass<2;pass++){
  int w=pass?1280:16,h=pass?720:16;
  assert(wurm_readback_open((void *)1,(void *)2,w,h)==1);restored();assert(bytes==(size_t)w*h*4);
  assert(wurm_readback_collect(output,sizeof(output)));
  emulated=7;assert(wurm_readback_issue());restored();emulated=0;
  for(int frame=0;frame<20;frame++){
   assert(!wurm_readback_issue());restored();assert(wurm_readback_issue());
   assert(wurm_readback_collect(output,bytes-1));assert(wurm_readback_collect(NULL,bytes));
   assert(!wurm_readback_collect(output,sizeof(output)));restored();
   for(size_t i=0;i<bytes;i++)assert(output[i]==(unsigned char)(i*13+issued));
  }
  for(int failure=0;failure<3;failure++){
   map_fail=failure==0;unmap_fail=failure==1;read_fail=failure==2;
   if(read_fail)assert(wurm_readback_issue());
   else {assert(!wurm_readback_issue());assert(wurm_readback_collect(output,sizeof(output)));}
   restored();assert(wurm_rb.failed);assert(wurm_readback_collect(output,sizeof(output)));assert(wurm_readback_issue());
   map_fail=unmap_fail=read_fail=0;
   // A failed map/unmap/read is terminal: clearing an error flag must never
   // make a questionable capture publishable during shutdown.
   assert(wurm_readback_collect(output,sizeof(output)));
   wurm_readback_close();restored();assert(allocated==deleted);
   if(failure<2)assert(wurm_readback_open((void *)1,(void *)2,w,h)==1);
  }
  assert(!wurm_rb.buffer&&!wurm_rb.pending);
 }
 puts("READBACK_NATIVE_FAULTS_PASS");
}
