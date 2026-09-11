#!/usr/bin/env python3
"""Build LLVM 17 ASan with the upstream AArch64 prctl/PAC correction."""
from pathlib import Path
import re
import subprocess

PATCH = '6bbf0c30ca4449e325beb2d28db00d258d3a1a10'


def patch(source):
    path = source/'lib/sanitizer_common/sanitizer_common_interceptors.inc'
    text = path.read_text()
    old = '''INTERCEPTOR(int, prctl, int option, unsigned long arg2, unsigned long arg3,
            unsigned long arg4, unsigned long arg5) {'''
    # Backport the upstream per-function target attribute. Keep BTI and leave
    # PAC enabled for the rest of ASan. No binary patch or key-reset suppression.
    new = '''// Backport LLVM 6bbf0c30ca4449e325beb2d28db00d258d3a1a10.
#if defined(__aarch64__)
# if defined(__ARM_FEATURE_BTI_DEFAULT)
#  define WURM_PRCTL_BRANCH __attribute__((target("branch-protection=bti")))
# else
#  define WURM_PRCTL_BRANCH __attribute__((target("branch-protection=none")))
# endif
# define WURM_PRCTL_INTERCEPTOR(ret_type, func, ...) \\
  DEFINE_REAL(ret_type, func, __VA_ARGS__) \\
  DECLARE_WRAPPER(ret_type, func, __VA_ARGS__) \\
  extern "C" INTERCEPTOR_ATTRIBUTE WURM_PRCTL_BRANCH ret_type WRAP(func)(__VA_ARGS__)
#else
# define WURM_PRCTL_INTERCEPTOR INTERCEPTOR
#endif
WURM_PRCTL_INTERCEPTOR(int, prctl, int option, unsigned long arg2, unsigned long arg3,
                     unsigned long arg4, unsigned long arg5) {'''
    if text.count(old) != 1:
        raise ValueError('Unexpected LLVM prctl interceptor source; refusing patch')
    path.write_text(text.replace(old, new))


def verify(runtime, objdump):
    output = subprocess.check_output([str(objdump), '-d', '--no-show-raw-insn',
        '--disassemble-symbols=__interceptor_prctl', str(runtime)], text=True)
    if '<__interceptor_prctl>:' not in output or not re.search(r'\bbti\s+c\b', output):
        raise ValueError('ASan prctl must retain its BTI entry')
    if re.search(r'\b(?:paciasp|pacibsp|autiasp|autibsp|retaa|retab)\b', output):
        raise ValueError('ASan prctl still authenticates a return across PAC key reset')
    control = subprocess.check_output([str(objdump), '-d', '--no-show-raw-insn',
        '--disassemble-symbols=__interceptor_pthread_create', str(runtime)], text=True)
    if not re.search(r'\bpaciasp\b', control):
        raise ValueError('ASan PAC protection unexpectedly absent outside prctl')
    print('ASAN_PRCTL_VERIFIED: BTI retained; no return PAC across key reset; other PAC retained', flush=True)


def build(ndk, source, cmake_source, llvm_source, work, run):
    cc = ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
    # compiler-rt expects the common cmake modules next to its source folder.
    cmake_source.rename(source.parent/'cmake')
    patch(source)
    folder = work/'asan-build'
    flags = '-mbranch-protection=standard -g'
    run(['cmake', '-S', source, '-B', folder,
         '-DCMAKE_SYSTEM_NAME=Linux', '-DCMAKE_SYSTEM_PROCESSOR=aarch64', '-DANDROID=1',
         '-DCMAKE_C_COMPILER='+str(cc/'aarch64-linux-android33-clang'),
         '-DCMAKE_CXX_COMPILER='+str(cc/'aarch64-linux-android33-clang++'),
         '-DCMAKE_C_COMPILER_TARGET=aarch64-linux-android33',
         '-DCMAKE_CXX_COMPILER_TARGET=aarch64-linux-android33',
         '-DCMAKE_C_FLAGS='+flags, '-DCMAKE_CXX_FLAGS='+flags,
         '-DCMAKE_ASM_FLAGS='+flags,
         '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=sha1 -Wl,-z,max-page-size=16384',
         '-DCMAKE_BUILD_TYPE=Release', '-DLLVM_MAIN_SRC_DIR='+str(llvm_source),
         '-DCOMPILER_RT_DEFAULT_TARGET_ONLY=ON', '-DCOMPILER_RT_INCLUDE_TESTS=OFF',
         '-DCOMPILER_RT_BUILD_BUILTINS=OFF', '-DCOMPILER_RT_BUILD_XRAY=OFF',
         '-DCOMPILER_RT_BUILD_LIBFUZZER=OFF', '-DCOMPILER_RT_BUILD_PROFILE=OFF',
         '-DCOMPILER_RT_BUILD_MEMPROF=OFF', '-DCOMPILER_RT_BUILD_ORC=OFF',
         '-DCOMPILER_RT_BUILD_GWP_ASAN=OFF', '-DCOMPILER_RT_SANITIZERS_TO_BUILD=asan',
         '-DCOMPILER_RT_USE_BUILTINS_LIBRARY=ON', '-DCOMPILER_RT_CXX_LIBRARY=none',
         '-DSANITIZER_CXX_ABI=none'], work/'asan-configure.log')
    run(['cmake', '--build', folder, '--target', 'clang_rt.asan-dynamic-aarch64', '-j4'],
        work/'asan-build.log')
    runtime = folder/'lib/linux/libclang_rt.asan-aarch64-android.so'
    verify(runtime, cc/'llvm-objdump')
    return runtime
