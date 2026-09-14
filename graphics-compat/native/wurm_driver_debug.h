/* Non-consuming GLES KHR_debug callback for the owned context. No GL calls in
 * the callback; no extra glGetError, context flags, or renderer-state changes.
 * Non-debug drivers may emit no messages even when the extension is available. */
#ifndef WURM_DRIVER_DEBUG_H
#define WURM_DRIVER_DEBUG_H
#include <stdatomic.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "wurm_mipmap_trace.h"

typedef void (*wurm_debug_callback)(GLenum, GLenum, GLuint, GLenum, GLsizei, const char *, const void *);
typedef void (*wurm_debug_register)(wurm_debug_callback, const void *);
typedef void (*wurm_debug_control)(GLenum, GLenum, GLenum, GLsizei, const GLuint *, GLboolean);
static atomic_uint wurm_driver_reports;
static const wurm_mipmap_site *(*wurm_driver_mipmap_context)(void);

static int wurm_has_extension(const char *list, const char *name) {
    if (!list || !name || !*name || strchr(name, ' ')) return 0;
    size_t n=strlen(name);
    const char *p=list;
    while ((p=strstr(p,name)) != NULL) {
        if ((p==list || p[-1]==' ') && (p[n]==' ' || p[n]=='\0')) return 1;
        p+=n;
    }
    return 0;
}
static void wurm_driver_message(GLenum source, GLenum type, GLuint id, GLenum severity,
                                GLsizei length, const char *message, const void *user) {
    (void)user;
    /* API errors / undefined behavior only. No shader text or routine chatter. */
    if (source!=0x8246 || (type!=0x824c && type!=0x824e)) return;
    unsigned report=atomic_load(&wurm_driver_reports);
    do { if (report>=64) return; }
    while (!atomic_compare_exchange_weak(&wurm_driver_reports,&report,report+1));
    char text[513]; size_t n=0;
    if (message && length>0) {
        n=(size_t)length; if(n>512) n=512;
        for(size_t i=0;i<n;i++) {
            unsigned char c=(unsigned char)message[i];
            text[i]=(c>=32 && c<127)?(char)c:' ';
        }
    }
    text[n]='\0';
    const wurm_mipmap_site *mipmap=wurm_driver_mipmap_context?wurm_driver_mipmap_context():NULL;
    struct timespec now={0}; clock_gettime(CLOCK_REALTIME,&now);
    fprintf(stdout,"[graphics-driver] time=%lld.%03ld pid=%ld source=0x%x type=0x%x id=%u severity=0x%x message=%s",
        (long long)now.tv_sec,now.tv_nsec/1000000,(long)getpid(),source,type,id,severity,text);
    if(mipmap) fprintf(stdout," mipmapSite=%s target=0x%x texture=%u unit=%u format=0x%x size=%dx%d valid=%d compressed=%d",
        mipmap->site,mipmap->target,mipmap->texture,mipmap->unit,mipmap->format,
        mipmap->width,mipmap->height,mipmap->valid,mipmap->compressed);
    fputc('\n',stdout);
    if(report==63) fputs("[graphics-driver] DETAIL_LIMIT errors=64; original GL error state untouched\n",stdout);
    fflush(stdout);
}
static void wurm_install_driver_debug(void *gles) {
    const GLubyte *(*get_string)(GLenum)=(const GLubyte *(*)(GLenum))dlsym(gles,"glGetString");
    void (*enable)(GLenum)=(void (*)(GLenum))dlsym(gles,"glEnable");
    const char *extensions=get_string?(const char *)get_string(GL_EXTENSIONS):NULL;
    if(!wurm_has_extension(extensions,"GL_KHR_debug")) {
        puts("[graphics-driver] KHR_DEBUG unavailable; existing GL error breadcrumbs retained"); return;
    }
    wurm_debug_register callback=(wurm_debug_register)eglGetProcAddress("glDebugMessageCallbackKHR");
    wurm_debug_control control=(wurm_debug_control)eglGetProcAddress("glDebugMessageControlKHR");
    if(!callback || !control || !enable) {
        puts("[graphics-driver] KHR_DEBUG entrypoints-unavailable; existing GL error breadcrumbs retained"); return;
    }
    atomic_store(&wurm_driver_reports,0);
    control(GL_DONT_CARE,GL_DONT_CARE,GL_DONT_CARE,0,NULL,GL_FALSE);
    control(0x8246,0x824c,GL_DONT_CARE,0,NULL,GL_TRUE);
    control(0x8246,0x824e,GL_DONT_CARE,0,NULL,GL_TRUE);
    callback(wurm_driver_message,NULL);
    enable(0x8242); /* GL_DEBUG_OUTPUT_SYNCHRONOUS_KHR */
    enable(0x92e0); /* GL_DEBUG_OUTPUT_KHR */
    puts("[graphics-driver] KHR_DEBUG installed api-errors=true undefined-behavior=true limit=64; non-debug context output is driver-dependent");
}
#endif
