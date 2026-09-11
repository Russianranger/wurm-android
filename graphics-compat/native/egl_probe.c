/* App-owned EGL context for the disposable OpenJDK graphics child. No ART/window pointers. */
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include "wurm_heap_check.h"

static EGLDisplay display = EGL_NO_DISPLAY;
static EGLContext context = EGL_NO_CONTEXT;
static EGLSurface surface = EGL_NO_SURFACE;
static EGLConfig config;
static int width, height;
static pthread_t owner;
static void *backend;
static void *driver;
static GLenum (*driver_error)(void);

static void fail(JNIEnv *env, const char *operation) {
    char message[240];
    snprintf(message, sizeof(message), "%s; EGL error=0x%x", operation, eglGetError());
    fprintf(stderr, "[graphics] EGL_ERROR %s\n", message);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), message);
}

static int valid_size(int w, int h) { return w >= 16 && h >= 16 && w <= 1280 && h <= 1024; }
static void dimensions(int *w, int *h) { *w = width; *h = height; }

static int cleanup(void) {
    int ok = 1;
    if (display != EGL_NO_DISPLAY) {
        if (!eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)) ok = 0;
        if (surface != EGL_NO_SURFACE && !eglDestroySurface(display, surface)) ok = 0;
        if (context != EGL_NO_CONTEXT && !eglDestroyContext(display, context)) ok = 0;
        if (!eglTerminate(display)) ok = 0;
    }
    display = EGL_NO_DISPLAY; surface = EGL_NO_SURFACE; context = EGL_NO_CONTEXT;
    return ok;
}

JNIEXPORT void JNICALL Java_wurm_graphics_NativeEgl_open(JNIEnv *env, jclass type, jstring library, jint w, jint h) {
    (void)type;
    if (!valid_size(w, h) || display != EGL_NO_DISPLAY) { fail(env, "Invalid dimensions or already open"); return; }
    if (!wurm_heap_check_ready()) { fail(env, "Native heap diagnostic inactive"); return; }
    owner = pthread_self(); width = w; height = h;
    const char *path = (*env)->GetStringUTFChars(env, library, NULL);
    if (path == NULL) return;
    backend = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env, library, path);
    if (backend == NULL) {
        fprintf(stderr, "[graphics] GL4ES_DLOPEN_ERROR %s\n", dlerror());
        fail(env, "Cannot load packaged GL4ES"); return;
    }
    void (*initialize)(void) = (void (*)(void))dlsym(backend, "initialize_gl4es");
    void (*set_dimensions)(void (*)(int *, int *)) = (void (*)(void (*)(int *, int *)))dlsym(backend, "set_getmainfbsize");
    if (initialize == NULL || set_dimensions == NULL) { fail(env, "GL4ES initialization API missing"); return; }
    set_dimensions(dimensions);
    /* GL4ES probes capabilities with its own temporary EGL context. Initialize it
       before creating ours, then explicitly make our context current. */
    printf("[graphics] GL4ES_INIT_BEGIN\n"); initialize(); printf("[graphics] GL4ES_INIT_RETURNED\n");
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor, count;
    const EGLint attributes[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 16, EGL_NONE };
    const EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    const EGLint surface_attributes[] = { EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE };
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, &major, &minor) ||
        !eglBindAPI(EGL_OPENGL_ES_API) || !eglChooseConfig(display, attributes, &config, 1, &count) || count != 1) {
        fail(env, "EGL display/config initialization failed"); cleanup(); return;
    }
    context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    surface = eglCreatePbufferSurface(display, config, surface_attributes);
    if (context == EGL_NO_CONTEXT || surface == EGL_NO_SURFACE || !eglMakeCurrent(display, surface, surface, context)) {
        fail(env, "EGL context/pbuffer/makeCurrent failed"); cleanup(); return;
    }
    /* Resolve from the GLES handle explicitly, avoiding late symbol interposition
       after LWJGL opens GL4ES globally. Keep the handle for this owned process. */
    const char *gles_name = getenv("LIBGL_GLES");
    driver = dlopen(gles_name != NULL ? gles_name : "libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
    driver_error = driver != NULL ? (GLenum (*)(void))dlsym(driver, "glGetError") : NULL;
    if (driver_error == NULL) { fail(env, "GLES error function lookup failed"); cleanup(); return; }
    printf("[graphics] EGL_CONTEXT_READY version=%d.%d size=%dx%d renderer=%s gles=%s\n",
        major, minor, w, h, glGetString(GL_RENDERER), glGetString(GL_VERSION));
}

JNIEXPORT void JNICALL Java_wurm_graphics_NativeEgl_resize(JNIEnv *env, jclass type, jint w, jint h) {
    (void)type;
    if (context == EGL_NO_CONTEXT || !pthread_equal(owner, pthread_self()) || !valid_size(w, h)) { fail(env, "Invalid resize/thread"); return; }
    const EGLint attributes[] = { EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE };
    EGLSurface next = eglCreatePbufferSurface(display, config, attributes);
    if (next == EGL_NO_SURFACE) { fail(env, "Resize allocation failed"); return; }
    if (!eglMakeCurrent(display, next, next, context)) {
        fail(env, "Resize makeCurrent failed"); eglDestroySurface(display, next); return;
    }
    EGLSurface old = surface; surface = next; width = w; height = h;
    if (!eglDestroySurface(display, old)) { fail(env, "Old surface destruction failed"); return; }
    printf("[graphics] EGL_SURFACE_RESIZED size=%dx%d same_context=true\n", w, h);
}

JNIEXPORT void JNICALL Java_wurm_graphics_NativeEgl_swap(JNIEnv *env, jclass type) {
    (void)type;
    if (context == EGL_NO_CONTEXT || !pthread_equal(owner, pthread_self()) || !eglSwapBuffers(display, surface))
        fail(env, "Pbuffer swap failed");
}

JNIEXPORT jint JNICALL Java_wurm_graphics_NativeEgl_error(JNIEnv *env, jclass type) {
    (void)type;
    if (context == EGL_NO_CONTEXT || !pthread_equal(owner, pthread_self()) ||
        eglGetCurrentContext() != context || driver_error == NULL) {
        fail(env, "GLES error check without owned current context"); return 0;
    }
    GLenum error = driver_error();
    if (error != GL_NO_ERROR) fprintf(stderr, "[graphics] GLES_ERROR code=0x%04x\n", error);
    return (jint)error;
}

JNIEXPORT void JNICALL Java_wurm_graphics_NativeEgl_close(JNIEnv *env, jclass type) {
    (void)type;
    if (display != EGL_NO_DISPLAY && !pthread_equal(owner, pthread_self())) { fail(env, "Close on wrong thread"); return; }
    if (context != EGL_NO_CONTEXT && backend != NULL) {
        void (*close_backend)(void) = (void (*)(void))dlsym(backend, "close_gl4es");
        if (close_backend != NULL) close_backend();
    }
    if (!cleanup()) { fail(env, "EGL teardown failed"); return; }
    /* Backend stays loaded until this disposable process exits; no callback can
       jump into unloaded code. Every test run gets a new process. */
    printf("[graphics] EGL_CONTEXT_CLOSED\n");
}
