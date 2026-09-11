/* Owned startup qualification for the diagnostic graphics child. */
#ifndef WURM_HEAP_CHECK_H
#define WURM_HEAP_CHECK_H
#include <stdio.h>
#include <stdlib.h>
#include <sanitizer/asan_interface.h>

static int wurm_heap_check_ready(void) {
    char *memory = (char *)malloc(32);
    if (memory == NULL) return 0;
    int payload = __asan_address_is_poisoned(memory);
    int left = __asan_address_is_poisoned(memory - 1);
    int right = __asan_address_is_poisoned(memory + 32);
    free(memory);
    if (payload || !left || !right) {
        fprintf(stderr, "[native-heap] ASAN_STARTUP_FAILED payload=%d left=%d right=%d\n", payload, left, right);
        return 0;
    }
    fprintf(stderr, "[native-heap] ASAN_READY allocation=intercepted leftRedzone=true rightRedzone=true\n");
    return 1;
}
#endif
