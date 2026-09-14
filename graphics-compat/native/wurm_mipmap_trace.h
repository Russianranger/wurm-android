/* Synchronous driver-error context. Fixed TLS values only: no GL queries,
 * allocation, logging, texture names/paths or pixel contents on the hot path. */
#ifndef WURM_MIPMAP_TRACE_H
#define WURM_MIPMAP_TRACE_H
typedef struct {
    const char *site;
    unsigned target, texture, unit, format;
    int width, height, valid, compressed;
} wurm_mipmap_site;
void wurm_mipmap_begin(const char *, unsigned, unsigned, unsigned, unsigned, int, int, int, int);
void wurm_mipmap_end(void);
const wurm_mipmap_site *wurm_mipmap_current(void);
#define WURM_MIPMAP_CALL(call, target, unit, tex) do { \
    wurm_mipmap_begin(__func__, target, (tex)->texture, unit, (tex)->format, \
        (tex)->width, (tex)->height, (tex)->valid, (tex)->compressed); \
    call; \
    wurm_mipmap_end(); \
} while (0)
#endif
