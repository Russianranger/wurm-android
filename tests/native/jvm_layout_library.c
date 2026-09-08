/* Synthetic shared-library fixture. Contains no JVM or Wurm code. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <link.h>
#include <stdio.h>

static void witness(void) {}
struct search { void *base; int count; };

static int visit(struct dl_phdr_info *info, size_t size, void *opaque) {
    (void)size;
    struct search *search = opaque;
    if ((void *)info->dlpi_addr != search->base) return 0;
    if (info->dlpi_phdr == NULL || info->dlpi_phnum == 0) return 20;
    search->count++;
    printf("PHDR=%s\n", info->dlpi_name);
    return 37; // The adapter must preserve early-stop return values.
}

int inspect_library(void) {
    Dl_info info;
    if (dladdr((void *)witness, &info) == 0 || info.dli_fbase == NULL) return 21;
    printf("DLADDR=%s\n", info.dli_fname);
    struct search search = {info.dli_fbase, 0};
    if (dl_iterate_phdr(visit, &search) != 37 || search.count != 1) return 22;
    return 0;
}
