#include "heap_compat.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int samples;
static int disables;
static int requested_result;
static int before_tag;
static int after_tag;
static int disable_fixture(void) { ++disables; return requested_result; }
static int sample_fixture(void) { return samples++ == 0 ? before_tag : after_tag; }

int main(int argc, char **argv) {
    if (argc != 5) return 2;
    requested_result = atoi(argv[2]);
    before_tag = atoi(argv[3]);
    after_tag = atoi(argv[4]);
    const char *policy = strcmp(argv[1], "unset") == 0 ? NULL : argv[1];
    int result = wurm_heap_compat_apply(policy, disable_fixture, sample_fixture);
    printf("samples=%d disables=%d\n", samples, disables);
    if (result != 0) return 78;
    puts("JAVA_LOAD_ALLOWED");
    return 0;
}
