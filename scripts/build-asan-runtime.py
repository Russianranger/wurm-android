#!/usr/bin/env python3
"""Build LLVM 17 ASan with upstream AArch64 PAC and BTI corrections."""
from pathlib import Path
import re
import subprocess

PATCH = '6bbf0c30ca4449e325beb2d28db00d258d3a1a10'
BTI_PATCH = '1c792d24e0a228ad49cc004a1c26bbd7cd87f030'


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
    patch_bti(source)


def patch_bti(source):
    """Backport LLVM #84061 to both C++ and assembly interceptor trampolines."""
    path = source/'lib/sanitizer_common/sanitizer_asm.h'
    text = path.read_text()
    anchor = '#if defined(__x86_64__) || defined(__i386__) || defined(__sparc__)'
    definitions = '''// Backport LLVM 1c792d24e0a228ad49cc004a1c26bbd7cd87f030.
#if defined(__aarch64__) && defined(__ARM_FEATURE_BTI_DEFAULT)
# define ASM_STARTPROC CFI_STARTPROC; hint #34
# define C_ASM_STARTPROC SANITIZER_STRINGIFY(CFI_STARTPROC) "\\nhint #34"
#else
# define ASM_STARTPROC CFI_STARTPROC
# define C_ASM_STARTPROC SANITIZER_STRINGIFY(CFI_STARTPROC)
#endif
#define ASM_ENDPROC CFI_ENDPROC
#define C_ASM_ENDPROC SANITIZER_STRINGIFY(CFI_ENDPROC)

'''
    if text.count(anchor) != 1 or text.count('CFI_STARTPROC;') != 1 or text.count('CFI_ENDPROC;') != 1:
        raise ValueError('Unexpected LLVM assembly trampoline source; refusing patch')
    text = text.replace('CFI_STARTPROC;', 'ASM_STARTPROC;').replace('CFI_ENDPROC;', 'ASM_ENDPROC;')
    path.write_text(text.replace(anchor, definitions + anchor))
    path = source/'lib/interception/interception.h'
    text = path.read_text()
    for old, new in [('SANITIZER_STRINGIFY(CFI_STARTPROC) "\\n"', 'C_ASM_STARTPROC "\\n"'),
                     ('SANITIZER_STRINGIFY(CFI_ENDPROC) "\\n"', 'C_ASM_ENDPROC "\\n"')]:
        if text.count(old) != 1:
            raise ValueError('Unexpected LLVM C++ trampoline source; refusing patch')
        text = text.replace(old, new)
    path.write_text(text)


def verify_exports(runtime, objdump):
    """Check actual public entry instructions, including assembly and aliases."""
    symbols = subprocess.check_output([str(objdump.parent/'llvm-readelf'),
        '--dyn-syms', '--wide', str(runtime)], text=True)
    assembly = subprocess.check_output([str(objdump), '-d', '--no-show-raw-insn', str(runtime)], text=True)
    instructions = {}
    for line in assembly.splitlines():
        match = re.match(r'\s*([0-9a-f]+):\s+(\S+)(?:\s+(.*))?$', line)
        if match: instructions[int(match[1], 16)] = (match[2], (match[3] or '').strip())
    exports = {}
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) >= 8 and fields[3] in ('FUNC', 'IFUNC') and fields[6].isdigit():
            exports.setdefault(int(fields[1], 16), fields[7])
    if len(exports) < 1000:
        raise ValueError('Unexpected ASan export inventory')
    accepted = {('bti', 'c'), ('bti', 'jc'), ('paciasp', ''), ('pacibsp', '')}
    invalid = [(hex(address), name) for address, name in exports.items()
               if instructions.get(address) not in accepted]
    if invalid:
        raise ValueError(f'ASan has {len(invalid)} public entries without BTI landing instructions: {invalid[:8]}')
    print(f'ASAN_BTI_VERIFIED: {len(exports)} distinct public entries accept indirect calls', flush=True)


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
    verify_exports(runtime, objdump)
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
