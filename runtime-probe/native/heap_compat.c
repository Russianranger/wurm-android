#include "heap_compat.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(__ANDROID__)
#include <malloc.h>
#endif

int wurm_heap_compat_apply(const char *policy, int (*disable)(void), int (*sample_tag)(void)) {
    if (policy == NULL) return 0;
    if (strcmp(policy, "off") != 0) {
        fprintf(stderr, "[native] HEAP_TAGGING_ERROR: unknown requested policy\n");
        return -1;
    }
    int before = sample_tag();
    if (before < 0 || disable() != 1) {
        fprintf(stderr, "[native] HEAP_TAGGING_ERROR: allocator opt-out unavailable; Java not loaded\n");
        return -1;
    }
    int after = sample_tag();
    if (after != 0) {
        fprintf(stderr, "[native] HEAP_TAGGING_ERROR: new allocation still tagged or allocation failed\n");
        return -1;
    }
    printf("[native] HEAP_TAGGING_OFF: before=0x%02x after=0x%02x; Java child only\n",
           (unsigned int)before, (unsigned int)after);
    return 0;
}

static int disable_native_tagging(void) {
#if defined(__ANDROID__) && defined(__aarch64__)
    /* Public Bionic API since 31; APK minimum is 33. Configure this exec'd
     * process directly rather than relying on a zygote/manifest setting.
     * Run before dlopen/libjvm threads. NONE also permits freeing older tagged
     * allocations made by Bionic's own startup code (AOSP heap_tagging.cpp).
     */
    return mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE);
#else
    return 0; // Never pretend a host test configured the Android allocator.
#endif
}

static int allocation_tag(void) {
    void *allocation = malloc(32);
    if (allocation == NULL) return -1;
#if UINTPTR_MAX == UINT64_MAX
    int tag = (int)((uintptr_t)allocation >> 56);
#else
    int tag = 0;
#endif
    // Inspect a copy for diagnostics only. Always free the original pointer.
    free(allocation);
    return tag;
}

int wurm_configure_heap(const char *policy) {
    return wurm_heap_compat_apply(policy, disable_native_tagging, allocation_tag);
}
