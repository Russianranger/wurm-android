/* A small, standalone APK-packaged process. No Android ART VM, root or shell. */
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <signal.h>
#include <unistd.h>
#include "jvm_layout.h"
#include "world_lock.h"
#include "heap_compat.h"
#include "startup_crash.h"

/* OpenJDK libjli's public launcher entry point (java.h, JDK 17). */
typedef int (*jli_launch_fn)(int, char **, int, const char **, int, const char **,
                            const char *, const char *, const char *, const char *,
                            unsigned char, unsigned char, unsigned char, int);

int main(int argc, char **argv) {
    wurm_startup_main();
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    /* Terminate this disposable test if its owning app process dies. */
    pid_t parent = getppid();
    if (parent == 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) {
        fprintf(stderr, "[native] Parent ownership unavailable: errno=%d\n", errno);
        return 70;
    }
    if (getuid() == 0 || geteuid() == 0) {
        fprintf(stderr, "[native] This diagnostic must run as an ordinary app UID.\n");
        return 71;
    }
    if (wurm_configure_heap(getenv("WURM_HEAP_TAGGING")) != 0) return 78;
    if (wurm_startup_finish() != 0) return 79;
    if (argc == 2 && strcmp(argv[1], "--wurm-heap-probe") == 0) {
        if (getenv("WURM_HEAP_TAGGING") == NULL || strcmp(getenv("WURM_HEAP_TAGGING"), "asan") != 0)
            return 78;
        puts("[native-heap] STARTUP_PROBE_PASS allocator and thread verified; no Java, graphics or game loaded");
        return 0;
    }
    if (wurm_world_lock(getenv("WURM_WORLD_LOCK")) < 0) {
        fprintf(stderr, "[native] Workspace is busy or inaccessible.\n");
        return 77;
    }
    printf("[native] uid=%u euid=%u pid=%u; starting APK-packaged Java launcher\n",
           (unsigned)getuid(), (unsigned)geteuid(), (unsigned)getpid());
    const char *jli_path = getenv("WURM_JLI_PATH");
    const char *jvm_path = getenv("WURM_JVM_PATH");
    const char *home = getenv("JAVA_HOME");
    if (jli_path == NULL || jvm_path == NULL || home == NULL) return 72;
    if (!wurm_jvm_layout_init(home, jvm_path)) return 76;
    void *jli = dlopen(jli_path, RTLD_NOW | RTLD_GLOBAL);
    if (jli == NULL) {
        fprintf(stderr, "[native] dlopen libjli failed: %s\n", dlerror());
        return 73;
    }
    jli_launch_fn launch = (jli_launch_fn)dlsym(jli, "JLI_Launch");
    if (launch == NULL) {
        fprintf(stderr, "[native] JLI_Launch missing: %s\n", dlerror());
        return 74;
    }
    /* The Android JLI patch derives its image path from JAVA_HOME for 'java'. */
    argv[0] = "java";
    return launch(argc, argv, 0, NULL, 0, NULL, "17", "17", "java", "openjdk",
                  0, 1, 0, 0);
}
