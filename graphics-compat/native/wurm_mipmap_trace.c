/* Included once by GL4ES getter.c; read only from the synchronous GLES callback. */
#include "wurm_mipmap_trace.h"
static __thread wurm_mipmap_site wurm_mipmap_active;
void wurm_mipmap_begin(const char *site, unsigned target, unsigned texture, unsigned unit,
                        unsigned format, int width, int height, int valid, int compressed) {
    wurm_mipmap_active = (wurm_mipmap_site){site, target, texture, unit, format, width, height, valid, compressed};
}
void wurm_mipmap_end(void) { wurm_mipmap_active.site = 0; }
__attribute__((visibility("default"))) const wurm_mipmap_site *wurm_mipmap_current(void) {
    return wurm_mipmap_active.site ? &wurm_mipmap_active : 0;
}
