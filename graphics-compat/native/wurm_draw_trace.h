/* Authored crash breadcrumb for the owned GL thread. One 1 KiB shared mapping;
 * no per-draw syscalls, heap allocation, driver queries or shader text. */
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <unistd.h>

static volatile uint32_t *wurm_draw_trace;
static int wurm_draw_trace_initialized;
static uint32_t wurm_draw_sequence;

static void wurm_trace_begin(uint32_t kind, uint32_t mode, int first, int count,
                             uint32_t type, const void *indices) {
    if (!wurm_draw_trace_initialized) {
        wurm_draw_trace_initialized = 1;
        const char *path = getenv("WURM_GL_DRAW_TRACE");
        if (path) {
            int fd = open(path, O_RDWR | O_CREAT | O_CLOEXEC, 0600);
            if (fd >= 0) {
                if (!ftruncate(fd, 1024)) {
                    void *mapped = mmap(NULL, 1024, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
                    if (mapped != MAP_FAILED) wurm_draw_trace = mapped;
                }
                close(fd);
            }
        }
    }
    volatile uint32_t *r = wurm_draw_trace;
    if (!r) return;
    __atomic_store_n(r, 0, __ATOMIC_RELEASE);
    r[1] = 1; r[2] = 1; r[3] = ++wurm_draw_sequence; r[4] = kind;
    r[5] = mode; r[6] = first; r[7] = count; r[8] = type;
    r[9] = (uintptr_t)indices; r[10] = (uint64_t)(uintptr_t)indices >> 32;
    r[11] = glstate->gleshard->program; r[12] = glstate->bind_buffer.index;
    int n = hardext.maxvattrib < 16 ? hardext.maxvattrib : 16;
    r[13] = n;
    for (int i = 0; i < n; ++i) {
        vertexattrib_t *v = &glstate->gleshard->vertexattrib[i];
        int k = 16 + i * 8;
        r[k] = v->enabled; r[k+1] = v->size; r[k+2] = v->type; r[k+3] = v->stride;
        r[k+4] = v->real_buffer; r[k+5] = (uintptr_t)v->pointer;
        r[k+6] = (uint64_t)(uintptr_t)v->pointer >> 32; r[k+7] = v->normalized;
    }
    __atomic_store_n(r, 0x57444754, __ATOMIC_RELEASE);
}
static void wurm_trace_end(void) {
    if (wurm_draw_trace) __atomic_store_n(wurm_draw_trace + 2, 2, __ATOMIC_RELEASE);
}
