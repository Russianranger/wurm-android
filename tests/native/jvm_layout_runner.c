#include "jvm_layout.h"
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv) {
    if (argc != 5) return 2;
    if (strcmp(argv[4], "enabled") == 0 && !wurm_jvm_layout_init(argv[1], argv[2])) return 76;
    for (int i = 2; i <= 3; ++i) {
        void *library = dlopen(argv[i], RTLD_NOW | RTLD_LOCAL);
        if (library == NULL) { fprintf(stderr, "%s\n", dlerror()); return 3; }
        int (*inspect)(void) = (int (*)(void))dlsym(library, "inspect_library");
        if (inspect == NULL) return 4;
        int result = inspect();
        if (result != 0) return result;
        dlclose(library);
    }
    return 0;
}
