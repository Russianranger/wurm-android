/* The ASan preload can fail before main(), before libc's interposed functions
 * are usable. This executable preinit callback uses Linux syscalls only.
 * Never call malloc, stdio, memcpy, sigaction or the ASan runtime here.
 * Compile freestanding with no builtins or stack protector; verify no imports.
 */
#define _GNU_SOURCE
#include <stddef.h>
#include <stdint.h>
#include <signal.h>
#include <sys/syscall.h>
#include <ucontext.h>
#include "startup_crash.h"

#if !defined(__aarch64__) && !defined(__x86_64__)
#error Unsupported startup recorder architecture
#endif

static long raw(long nr, long a, long b, long c, long d) {
#if defined(__aarch64__)
    register long x8 __asm__("x8") = nr;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    register long x3 __asm__("x3") = d;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3) : "memory", "cc");
    return x0;
#else
    register long r10 __asm__("r10") = d;
    long result;
    __asm__ volatile("syscall" : "=a"(result) : "a"(nr), "D"(a), "S"(b), "d"(c), "r"(r10) : "rcx", "r11", "memory", "cc");
    return result;
#endif
}

static void output(const char *p, size_t size) {
    while (size) {
        long n = raw(__NR_write, 2, (long)p, (long)size, 0);
        if (n == -4) continue; /* EINTR */
        if (n <= 0) break;
        p += n; size -= (size_t)n;
    }
}
#define LITERAL(s) output(s, sizeof(s) - 1)

static void hex(uintptr_t value) {
    char text[2 + sizeof(value) * 2];
    text[0] = '0'; text[1] = 'x';
    for (size_t i = 0; i < sizeof(value) * 2; ++i)
        text[2 + i] = "0123456789abcdef"[(value >> ((sizeof(value) * 2 - i - 1) * 4)) & 15];
    output(text, sizeof(text));
}

/* Linux rt_sigaction ABI, not the libc wrapper's struct sigaction layout. */
struct kernel_action {
    void (*handler)(int, siginfo_t *, void *);
    unsigned long flags;
    void (*restorer)(void);
    uint64_t mask;
};
static const int signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT };
static struct kernel_action previous[5];
static stack_t previous_stack;
static unsigned char alternate_stack[65536] __attribute__((aligned(16)));
static unsigned installed;
static int stack_installed;
static volatile sig_atomic_t phase;
static volatile sig_atomic_t handling;

__attribute__((naked)) static void restore_signal(void) {
#if defined(__aarch64__)
    __asm__ volatile("hint #34\nmov x8, #139\nsvc #0");
#else
    __asm__ volatile("mov $15, %rax\nsyscall");
#endif
}

static void crash(int sig, siginfo_t *info, void *context) {
    if (handling) { raw(__NR_exit_group, 128 + sig, 0, 0, 0); return; }
    handling = 1;
    LITERAL("[startup-crash] SIGNAL signal="); hex((uintptr_t)sig);
    LITERAL(" code="); hex((uintptr_t)info->si_code);
    LITERAL(" address="); hex((uintptr_t)info->si_addr);
    LITERAL(" phase="); hex((uintptr_t)phase); LITERAL("\n");
    const ucontext_t *uc = context;
#if defined(__aarch64__)
    LITERAL("[startup-crash] REGISTERS pc="); hex(uc->uc_mcontext.pc);
    LITERAL(" lr="); hex(uc->uc_mcontext.regs[30]);
    LITERAL(" sp="); hex(uc->uc_mcontext.sp);
    LITERAL(" fp="); hex(uc->uc_mcontext.regs[29]); LITERAL("\n");
    for (unsigned i = 0; i < 29; ++i) {
        LITERAL("[startup-crash] REGISTER index="); hex(i);
        LITERAL(" value="); hex(uc->uc_mcontext.regs[i]); LITERAL("\n");
    }
#else
    LITERAL("[startup-crash] REGISTERS pc="); hex((uintptr_t)uc->uc_mcontext.gregs[REG_RIP]);
    LITERAL(" sp="); hex((uintptr_t)uc->uc_mcontext.gregs[REG_RSP]);
    LITERAL(" fp="); hex((uintptr_t)uc->uc_mcontext.gregs[REG_RBP]); LITERAL("\n");
#endif
    /* The current process only; no stack-pointer dereferences or other PIDs.
     * Prefix each bounded maps line so the normal report retains its meaning. */
    LITERAL("[startup-crash] MAPS_BEGIN\n");
    long fd = raw(__NR_openat, -100, (long)"/proc/self/maps", 0x80000, 0);
    if (fd >= 0) {
        char buffer[1024];
        size_t total = 0;
        int line_start = 1;
        while (total < 98304) {
            long n = raw(__NR_read, fd, (long)buffer, sizeof(buffer), 0);
            if (n == -4) continue;
            if (n <= 0) break;
            for (long i = 0, start = 0; i < n; ++i) {
                if (line_start) { LITERAL("[startup-crash] MAP "); line_start = 0; }
                if (buffer[i] == '\n' || i == n - 1) {
                    output(buffer + start, (size_t)(i - start + 1));
                    start = i + 1;
                    line_start = buffer[i] == '\n';
                }
            }
            total += (size_t)n;
        }
        raw(__NR_close, fd, 0, 0, 0);
        if (!line_start) LITERAL("\n");
        if (total >= 98304) LITERAL("[startup-crash] MAPS_TRUNCATED\n");
    } else { LITERAL("[startup-crash] MAPS_UNAVAILABLE\n"); }
    LITERAL("[startup-crash] CAPTURE_END\n");
    /* Restore the pre-existing Android handler and queue the fatal signal.
     * It runs after this handler returns; this is reporting, not recovery. */
    for (unsigned i = 0; i < installed; ++i)
        if (signals[i] == sig) raw(__NR_rt_sigaction, sig, (long)&previous[i], 0, 8);
    raw(__NR_tgkill, raw(__NR_getpid, 0, 0, 0, 0), raw(__NR_gettid, 0, 0, 0, 0), sig, 0);
}

static int equal(const char *a, const char *b) {
    while (*a && *a == *b) { ++a; ++b; }
    return *a == *b;
}

int wurm_startup_finish(void) {
    if (!phase) return 0;
    int failed = 0;
    for (unsigned i = 0; i < installed; ++i)
        if (raw(__NR_rt_sigaction, signals[i], (long)&previous[i], 0, 8) != 0) failed = 1;
    if (stack_installed && raw(__NR_sigaltstack, (long)&previous_stack, 0, 0, 0) != 0) failed = 1;
    if (failed) { LITERAL("[startup-crash] RESTORE_FAILED; Java not loaded\n"); return -1; }
    installed = 0; stack_installed = 0; phase = 0;
    LITERAL("[startup-crash] CAPTURE_RELEASED before Java\n");
    return 0;
}

void wurm_startup_main(void) {
    if (phase) { phase = 2; LITERAL("[startup-crash] MAIN_ENTERED\n"); }
}

static void preinit(int argc, char **argv, char **envp) {
    (void)argc; (void)argv;
    int enabled = 0;
    for (char **p = envp; p && *p; ++p)
        if (equal(*p, "WURM_STARTUP_TRACE=1")) enabled = 1;
    if (!enabled) return;
    phase = 1;
    stack_t stack;
    stack.ss_sp = alternate_stack; stack.ss_size = sizeof(alternate_stack); stack.ss_flags = 0;
    if (raw(__NR_sigaltstack, (long)&stack, (long)&previous_stack, 0, 0) != 0) {
        LITERAL("[startup-crash] INSTALL_FAILED altstack\n");
        raw(__NR_exit_group, 79, 0, 0, 0); return;
    }
    stack_installed = 1;
    struct kernel_action action;
    action.handler = crash;
    action.flags = SA_SIGINFO | SA_ONSTACK | 0x04000000UL; /* SA_RESTORER */
    action.restorer = restore_signal; action.mask = 0;
    for (unsigned i = 0; i < 5; ++i) {
        if (raw(__NR_rt_sigaction, signals[i], (long)&action, (long)&previous[i], 8) != 0) {
            LITERAL("[startup-crash] INSTALL_FAILED sigaction\n");
            raw(__NR_exit_group, 79, 0, 0, 0); return;
        }
        ++installed;
    }
    LITERAL("[startup-crash] CAPTURE_READY preinit; register/map capture only\n");
}

__attribute__((section(".preinit_array"), used))
static void (*const startup_preinit)(int, char **, char **) = preinit;
