/* Compatibility with the pinned Android HotSpot's early os::jvm_path().
 * Bionic reports the APK's canonical lib/arm64/libjvm.so path. HotSpot needs
 * the existing JAVA_HOME/lib/server/libjvm.so alias to locate lib/modules.
 * Only the selected JVM's reported name changes; mappings, addresses, symbols,
 * file loading and every other library stay with the system linker.
 */
#define _GNU_SOURCE
#include "jvm_layout.h"
#include <dlfcn.h>
#include <limits.h>
#include <link.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

typedef int (*phdr_callback)(struct dl_phdr_info *, size_t, void *);
static int (*system_iterate)(phdr_callback, void *);
static int (*system_dladdr)(const void *, Dl_info *);
static pthread_once_t resolve_once = PTHREAD_ONCE_INIT;
static char installed_path[PATH_MAX];
static char image_path[PATH_MAX];
static int ready;
static atomic_flag reported = ATOMIC_FLAG_INIT;

static void resolve_linker(void) {
    system_iterate = (int (*)(phdr_callback, void *))dlsym(RTLD_NEXT, "dl_iterate_phdr");
    system_dladdr = (int (*)(const void *, Dl_info *))dlsym(RTLD_NEXT, "dladdr");
    if (system_iterate == NULL || system_dladdr == NULL) {
        fprintf(stderr, "[native] Cannot resolve system library-path queries\n");
        _Exit(75);
    }
}

int wurm_jvm_layout_init(const char *home, const char *installed_jvm) {
    char alias_target[PATH_MAX];
    char modules[PATH_MAX];
    struct stat st;
    if (ready || home == NULL || home[0] != '/' || installed_jvm == NULL) return 0;
    int count = snprintf(image_path, sizeof(image_path), "%s/lib/server/libjvm.so", home);
    if (count < 0 || (size_t)count >= sizeof(image_path)) return 0;
    count = snprintf(modules, sizeof(modules), "%s/lib/modules", home);
    if (count < 0 || (size_t)count >= sizeof(modules)) return 0;
    if (realpath(installed_jvm, installed_path) == NULL ||
        realpath(image_path, alias_target) == NULL ||
        strcmp(installed_path, alias_target) != 0 ||
        stat(modules, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size == 0) {
        fprintf(stderr, "[native] Invalid JVM image alias or missing boot modules\n");
        return 0;
    }
    pthread_once(&resolve_once, resolve_linker);
    ready = 1;
    printf("[native] JVM installed path: %s\n", installed_path);
    printf("[native] JVM image alias: %s\n", image_path);
    return 1;
}

static const char *image_name(const char *name) {
    if (!ready || name == NULL || strcmp(name, installed_path) != 0) return name;
    if (!atomic_flag_test_and_set(&reported)) {
        printf("[native] JVM_IMAGE_PATH_OK: %s\n", image_path);
    }
    return image_path;
}

struct callback_state { phdr_callback callback; void *data; };

static int visit_library(struct dl_phdr_info *info, size_t size, void *opaque) {
    struct callback_state *state = opaque;
    const char *name = image_name(info->dlpi_name);
    if (name == info->dlpi_name) return state->callback(info, size, state->data);
    // Keep the loader's object untouched and preserve all fields known to this
    // ABI. Report the copied size, so newer optional fields aren't over-read.
    struct dl_phdr_info copy;
    size_t copied = size < sizeof(copy) ? size : sizeof(copy);
    memset(&copy, 0, sizeof(copy));
    memcpy(&copy, info, copied);
    copy.dlpi_name = name;
    return state->callback(&copy, copied, state->data);
}

/* Exported from the main executable, before the dynamically loaded JVM. */
int dl_iterate_phdr(phdr_callback callback, void *data) {
    pthread_once(&resolve_once, resolve_linker);
    struct callback_state state = {callback, data};
    return system_iterate(visit_library, &state);
}

int dladdr(const void *address, Dl_info *info) {
    pthread_once(&resolve_once, resolve_linker);
    int result = system_dladdr(address, info);
    if (result != 0) info->dli_fname = image_name(info->dli_fname);
    return result;
}
