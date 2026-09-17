/* One owned GLES3 pixel-pack buffer. GL4ES still selects/restores the read FBO.
 * Only the EGL owner calls this code; no GL context or mapped pointer is shared.
 * Native pack state is borrowed only during an operation, then restored. */
#include <GLES3/gl3.h>
#include <string.h>
#include <stdint.h>

static struct {
    GLuint buffer;
    int width, height, pending, failed;
    size_t bytes;
    void (*get)(GLenum, GLint *);
    const GLubyte *(*string)(GLenum);
    void (*gen)(GLsizei, GLuint *);
    void (*del)(GLsizei, const GLuint *);
    void (*bind)(GLenum, GLuint);
    void (*data)(GLenum, GLsizeiptr, const void *, GLenum);
    void *(*map)(GLenum, GLintptr, GLsizeiptr, GLbitfield);
    GLboolean (*unmap)(GLenum);
    void (*store)(GLenum, GLint);
    void (*flush)(void);
    GLenum (*error)(void);
    void (*backend_get)(GLenum, GLint *);
    void (*backend_read)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);
    void (*backend_flush)(void);
} wurm_rb;

static void wurm_readback_close(void) {
    if (wurm_rb.buffer) wurm_rb.del(1, &wurm_rb.buffer);
    memset(&wurm_rb, 0, sizeof(wurm_rb));
}

/* 0 = unsupported (safe synchronous fallback), 1 = initialized, -1 = error. */
static int wurm_readback_open(void *driver_handle, void *backend_handle, int w, int h) {
    if (wurm_rb.buffer || w < 16 || h < 16 || w > 1280 || h > 1024) return -1;
#define WURM_RB_LOAD(member, handle, name) do { \
    *(void **)(&wurm_rb.member) = dlsym(handle, name); \
    if (!wurm_rb.member) { memset(&wurm_rb, 0, sizeof(wurm_rb)); return 0; } \
} while (0)
    WURM_RB_LOAD(string, driver_handle, "glGetString");
    const char *version = (const char *)wurm_rb.string(GL_VERSION);
    int major = 0;
    if (!version || sscanf(version, "OpenGL ES %d", &major) != 1 || major < 3) {
        memset(&wurm_rb, 0, sizeof(wurm_rb)); return 0;
    }
    WURM_RB_LOAD(get, driver_handle, "glGetIntegerv");
    WURM_RB_LOAD(gen, driver_handle, "glGenBuffers");
    WURM_RB_LOAD(del, driver_handle, "glDeleteBuffers");
    WURM_RB_LOAD(bind, driver_handle, "glBindBuffer");
    WURM_RB_LOAD(data, driver_handle, "glBufferData");
    WURM_RB_LOAD(map, driver_handle, "glMapBufferRange");
    WURM_RB_LOAD(unmap, driver_handle, "glUnmapBuffer");
    WURM_RB_LOAD(store, driver_handle, "glPixelStorei");
    WURM_RB_LOAD(flush, driver_handle, "glFlush");
    WURM_RB_LOAD(error, driver_handle, "glGetError");
    WURM_RB_LOAD(backend_get, backend_handle, "glGetIntegerv");
    WURM_RB_LOAD(backend_read, backend_handle, "glReadPixels");
    WURM_RB_LOAD(backend_flush, backend_handle, "glFlush");
#undef WURM_RB_LOAD
    if (wurm_rb.error() != GL_NO_ERROR) { wurm_readback_close(); return -1; }
    GLint binding = 0;
    wurm_rb.get(GL_PIXEL_PACK_BUFFER_BINDING, &binding);
    wurm_rb.gen(1, &wurm_rb.buffer);
    if (!wurm_rb.buffer) { wurm_readback_close(); return -1; }
    wurm_rb.width = w; wurm_rb.height = h; wurm_rb.bytes = (size_t)w*h*4;
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, wurm_rb.buffer);
    wurm_rb.data(GL_PIXEL_PACK_BUFFER, (GLsizeiptr)wurm_rb.bytes, NULL, GL_STREAM_READ);
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, (GLuint)binding);
    if (wurm_rb.error() != GL_NO_ERROR) { wurm_readback_close(); return -1; }
    return 1;
}

static const char *wurm_readback_issue(void) {
    if (!wurm_rb.buffer || wurm_rb.pending || wurm_rb.failed) return "Readback issue without a free owned buffer";
    /* GL4ES emulates PBOs in CPU memory. Do not combine its pack offset with
       our real driver PBO. The launcher normally has no emulated PBO bound. */
    GLint emulated = 0;
    wurm_rb.backend_get(GL_PIXEL_PACK_BUFFER_BINDING, &emulated);
    if (emulated) return "Readback conflicts with a client pixel-pack buffer";
    wurm_rb.backend_flush();
    GLint binding = 0, pack[4];
    const GLenum names[4] = {GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_ROWS, GL_PACK_SKIP_PIXELS};
    wurm_rb.get(GL_PIXEL_PACK_BUFFER_BINDING, &binding);
    for (int i=0; i<4; ++i) wurm_rb.get(names[i], pack+i);
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, wurm_rb.buffer);
    for (int i=0; i<4; ++i) wurm_rb.store(names[i], i ? 0 : 1);
    /* The pinned GL4ES RGBA8 path passes offset zero through without allocation
       or conversion and retains its readfboBegin/readfboEnd handling. */
    wurm_rb.backend_read(0, 0, wurm_rb.width, wurm_rb.height, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, (GLuint)binding);
    for (int i=0; i<4; ++i) wurm_rb.store(names[i], pack[i]);
    wurm_rb.flush();
    if (wurm_rb.error() != GL_NO_ERROR) { wurm_rb.failed = 1; return "Pipelined readback submission failed"; }
    wurm_rb.pending = 1;
    return NULL;
}

static const char *wurm_readback_collect(void *destination, size_t capacity) {
    if (!wurm_rb.buffer || !wurm_rb.pending || wurm_rb.failed || !destination || capacity < wurm_rb.bytes)
        return "Readback collection without pending frame or sufficient direct storage";
    GLint binding = 0;
    wurm_rb.get(GL_PIXEL_PACK_BUFFER_BINDING, &binding);
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, wurm_rb.buffer);
    /* Mapping may wait for the previous read to finish. It is intentionally
       delayed until the next swap, after the next frame's client work. */
    void *mapped = wurm_rb.map(GL_PIXEL_PACK_BUFFER, 0, (GLsizeiptr)wurm_rb.bytes, GL_MAP_READ_BIT);
    const char *failure = NULL;
    if (!mapped) failure = "Pipelined readback mapping failed";
    else {
        memcpy(destination, mapped, wurm_rb.bytes);
        if (!wurm_rb.unmap(GL_PIXEL_PACK_BUFFER)) failure = "Pipelined readback storage invalidated";
    }
    wurm_rb.bind(GL_PIXEL_PACK_BUFFER, (GLuint)binding);
    if (wurm_rb.error() != GL_NO_ERROR) failure = "Pipelined readback collection GL error";
    if (!failure) wurm_rb.pending = 0;
    else wurm_rb.failed = 1;
    return failure;
}
