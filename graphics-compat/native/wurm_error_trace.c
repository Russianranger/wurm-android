/* Included by getter.c. Fixed per-thread storage; hot paths only store literals.
 * Driver candidates are errorGL call sites, NOT proof of a failing GLES call. */
#include "wurm_error_trace.h"
#include <stdio.h>
#include <time.h>
#include <unistd.h>

typedef struct { const char *function; int line; } wurm_error_site;
static __thread wurm_error_site wurm_shim_site;
static __thread wurm_error_site wurm_driver_sites[16];
static __thread unsigned wurm_driver_next, wurm_driver_count;
static __thread unsigned wurm_error_reports;

void wurm_error_shim(const char *function, int line) {
    wurm_shim_site = (wurm_error_site){function, line};
}
void wurm_error_driver(const char *function, int line) {
    wurm_driver_sites[wurm_driver_next] = (wurm_error_site){function, line};
    wurm_driver_next = (wurm_driver_next + 1) % 16;
    if (wurm_driver_count < 16) wurm_driver_count++;
}
void wurm_error_observed(unsigned error, int driver) {
    if (error && wurm_error_reports < 64) {
        struct timespec now = {0};
        clock_gettime(CLOCK_REALTIME, &now);
        fprintf(stdout, "[graphics-error] time=%lld.%03ld pid=%ld code=0x%x source=%s",
            (long long)now.tv_sec, now.tv_nsec / 1000000, (long)getpid(), error, driver ? "driver" : "shim");
        if (!driver) {
            fprintf(stdout, " origin=%s:%d", wurm_shim_site.function ? wurm_shim_site.function : "unobserved", wurm_shim_site.line);
        } else {
            fputs(" candidates=recent-errorGL-sites-not-proof", stdout);
            unsigned count = wurm_driver_count < 8 ? wurm_driver_count : 8;
            for (unsigned i = 0; i < count; i++) {
                wurm_error_site site = wurm_driver_sites[(wurm_driver_next + 16 - count + i) % 16];
                fprintf(stdout, " %s:%d", site.function, site.line);
            }
        }
        fputc('\n', stdout);
        if (++wurm_error_reports == 64)
            fputs("[graphics-error] DETAIL_LIMIT errors=64; GL error return values remain unchanged\n", stdout);
        fflush(stdout);
    }
    // The existing getter consumed/reset this state. Never attach an old site to a new error.
    wurm_shim_site = (wurm_error_site){0};
    wurm_driver_next = wurm_driver_count = 0;
}
