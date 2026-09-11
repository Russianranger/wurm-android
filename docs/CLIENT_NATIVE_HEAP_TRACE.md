# 0.10.30 — shader cache cleanup fix

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.30-program-cache/Wurm-Server.apk).

The native startup check passed on the Thor. Import the same complete client
and stopped-server runtime ZIPs into this separate preview and retry local
play. No repeat of the standalone startup test is needed for this release.

## Evidence from 0.10.29 and the correction

The 2026-09-11 11:14:40 client report confirms `HEAP_ASAN_READY`,
`HEAP_ASAN_THREADS_READY`, `STARTUP_PROBE_PASS`, and standalone exit zero.
The corrected native TLS runtime also entered Java in both actual client
attempts. This validates the preceding startup correction on the Thor.

Both client attempts (PIDs 14397 and 14600) then stopped at shader program 22's
relink, graphics trace sequence 702, before world entry. ASan reports a 4-byte
read immediately after a 32-byte heap allocation in GL4ES. The matching released
library has build ID `d791f5a1d111977753a37d3599f3d6f550fafb2b`:

| ELF offset | Matched code |
| --- | --- |
| `0x28d2c0` | `kh_del_uniformlist`, inlined into `clear_program` |
| `0x290230` | `gl4es_glLinkProgram`, call to `clear_program`, program.c:736 |
| `0x2830c4` | `kh_resize_uniformlist`, allocation of the hash-table flags |
| `0x28385c` | `kh_put_uniformlist` |
| `0x28d858` | `fill_program`, uniform insertion at program.c:563 |
| `0x290874` | previous successful link's `fill_program`, program.c:795 |

`kh_foreach` supplies keys, while `kh_del` requires a bucket index. The old
`clear_program` passes uniform locations directly to `kh_del`. A location can
exceed the number of buckets; the flags lookup then reads outside its allocation.
It can also mark the wrong bucket or leave freed values in the table. Attribute
cache cleanup makes the same key/index mistake.

The checked downstream patch replaces mutation during key iteration with
`kh_foreach_value` to free each cached value, followed by the appropriate
`kh_clear` call. It resets the uniform count and cache size, retains reusable
capacity, and preserves the existing name/glname alias ownership. The khash
implementation, shader contents, rendering settings, ASan policy, JVM and server
POC are unchanged. The graphics manifest records `program-cache-cleanup` and
APK verification requires it.

The regression compiles the actual production program.c cleanup and hash-table
implementation under host ASan. A sparse location of 129 in a 128-bucket map
reproduces the exact `kh_del_uniformlist` 4-byte overread after its 32-byte flags
allocation. The fixed source passes that case and 20 cycles of sparse and
colliding uniform locations, attribute entries, empty cleanup and cache reuse.
Address checks are enabled; LeakSanitizer is disabled for the restricted host
process environment. No Wurm assets or mocked hash-table implementation are used.

The server report records the requested shutdown, world-saving steps and
`SERVER_EXIT=0`. No server crash is shown. This is a concrete graphics defect,
but the new ASan report does not prove that it caused every earlier Scudo
corruption report; further client faults may remain.

## Evidence from 0.10.28 and the correction

The 2026-09-11 10:39:37 report contains a standalone native test, PID 3686.
The preinit recorder ran, ASan initialized its interceptors and shadow mappings,
then SIGSEGV/SEGV_MAPERR occurred before `main`. PC `0x732fb12da0` maps to
`__interceptor_malloc+104`, offset `0xf9da0`, in ASan build ID
`86421f6fd90a061272a7b92bcc65c936ff3c0b0d` (SHA-256
`4eb563c2221af2be2784bfc0d18a260f38310e1f6cacb74ee39b64075d10933d`).
The fault address equals SP `0x7fd7a3a990`, which is `0x670` below the mapped
8 MiB stack's lower boundary, `0x7fd7a3b000`.

The Android trace contains 183 complete frames before truncation, repeating:

1. `__asan::Allocator::Allocate` tags the allocation via `__lsan::DisabledInThisThread`.
2. That function reads `disable_counter` through `__emutls_get_address`.
3. The emulated TLS helper calls `malloc` for its first per-thread storage.
4. ASan intercepts that allocation and re-enters the same counter lookup before
   the initial storage has been installed. The cycle exhausts the stack.

The exact released machine code confirms both call edges. The fault is in the
standalone memory checker; no JVM, game JAR, graphics or server was loaded.
`detect_leaks=0` does not avoid the lookup: ASan's allocator still reads this
counter when setting allocation metadata.

LLVM 17's CMake parses `ANDROID_API_LEVEL` from `-target` in `CMAKE_C_FLAGS`.
Our recipe set the API-33 compiler wrapper and `CMAKE_C_COMPILER_TARGET`, but
omitted that flag. The API detection was therefore empty and LLVM's API>=29
`-fno-emulated-tls` path did not run. This was a configuration error in the
source-built checker. The fixed recipe supplies the explicit API-33 target
and native-TLS flag in C/C++/assembly compilation. It retains the LLVM source,
existing PAC/BTI patches, ASan checks and client-only preload policy.

The new binary gate requires the counter to be an ELF TLS symbol, a PT_TLS
segment, and a direct `TPIDR_EL0` lookup with no function call. It rejects
emulated TLS variables and `__emutls_get_address`. The NDK's separate
`__emutls_unregister_key` stub is harmless (`bti c; ret`) and is permitted.
This gate rejects the exact 0.10.28 runtime and accepts the rebuilt ARM64 one.
All 1,518 BTI entries and the prior prctl/PAC correction still pass.

The early recorder re-raises the original fatal signal after recording it,
which explains the later Android `SI_TKILL` line. The recorder's original
`SEGV_MAPERR`, registers and maps identify the original fault. The Android
exit-info record also became available in this run.

## Evidence from 0.10.27

The supplied client report dated 2026-09-11 10:10:40 contains one entry attempt:
PID 27303, exit 139, SIGSEGV code 1 / SEGV_MAPERR at address `0x7fd3f5dbc0`.
Inventory, compatibility and graphics preparation pass. Entry fails before
`HEAP_ASAN_READY`, Java initialization or any graphics trace. The parent now
records the correct child PID, but Android returns no matching exec-child
exit record. The available log has no PC, module mapping or backtrace. An
invalid-access address is not enough to identify the faulting instruction or
to conclude that the stack overflowed. The server saves and exits zero after
the user's stop request; no server failure is shown.

## Retained early recorder and standalone test

- A small executable `.preinit_array` callback, before shared-library
  constructors, enabled only by `WURM_STARTUP_TRACE=1` in diagnostic client
  children. It uses direct Linux syscalls and has no libc/ASan imports.
- A private 64 KiB alternate signal stack. Before Java starts, fatal signals
  produce signal/code/address, PC/SP/FP, ARM64 LR/general registers and at most
  96 KiB of the current process's `/proc/self/maps`. No unsafe stack walk or
  another process's memory is read. A failed maps read is explicitly reported.
- Existing handlers receive the fatal signal after capture; the child is never
  resumed as if the fault succeeded. On successful native startup, preceding
  handlers and the preceding alternate stack are restored before loading Java.
  Failure to restore blocks Java. A fault before executable preinit, or a later
  library replacing the recorder, may still evade this capture.
- An isolated **Native Memory Startup Test**, with a 30-second limit and Stop
  Client support. It uses the same ASan/C++ preload order and allocator/thread
  checks as game entry, with verbose ASan startup output. It loads no JVM,
  graphics, imported runtime or server. Success requires both exit zero and
  the exact native `STARTUP_PROBE_PASS` marker.
- Startup fault details take precedence over an absent graphics trace in the
  summary; a specific ASan memory-error report still has higher priority.

The ASan source, PAC/BTI patches, graphics instrumentation, JVM, server POC and
game settings remain unchanged; the ASan target/TLS build flags are corrected above. Preinit phase `1`
means before `main`; phase `2` means runner startup before Java. A passing
isolated probe would establish allocator/thread startup only, not game stability.

## Evidence from 0.10.26 and the additional correction

Three client attempts exit 132 with SIGILL, code `ILL_ILLOPC`, before any native
startup marker. No ASan allocation check, Java initialization or graphics trace
is present. The server remained available, then saved and exited zero after the
stop request. This is not evidence about character creation or the original
in-game heap corruption.

The exact released ASan binary advertises GNU BTI/PAC properties, but 283
exported entry addresses begin with a plain assembly branch instead of a valid
BTI landing instruction. Its `memset` trampoline is at `0xf7dd0`, consistent
with the `0xdd0` page offset in all three crashes. A full stack and load address
were unavailable, so that exact fault-site mapping remains an inference.

LLVM fixed this defect in `1c792d24e0a228ad49cc004a1c26bbd7cd87f030` (#84061).
The retained 0.10.27 change backports that correction to both assembly and C++-declared
interceptor trampolines. Branch protection remains enabled. The build now
checks every distinct exported function entry (1,518 in the tested ARM64
runtime), in addition to checking the prctl return sequence. This expanded gate
rejects the actual 0.10.26 library and accepts the rebuilt one. The previous
check was too narrow: it validated prctl but missed the assembly trampolines.

Crash capture previously learned the child PID only from the native runner's
stdout. These crashes happened before that output, causing
`EXIT_INFO_UNAVAILABLE child PID was not observed`. The parent now records the
PID from its own Android 13 `Process.toString()` immediately after `start()`,
including an already-exited child. It uses no hidden API access and retains the
native marker as a fallback. Exit-info collection briefly retries the same
package/PID; UID and attempt-time checks still reject unrelated records.

These corrections address two diagnostic defects. The later 0.10.29 report
confirms startup after the additional native TLS correction. The original
in-game crash still requires testing.

## Evidence from 0.10.25 and the startup correction

Both supplied attempts exited 132 / SIGILL before Java initialization. ASan's
allocation check passed, but the child then stopped in `__interceptor_prctl+776`
at library offset `0x83fcc` (build ID `b1e02acf349a988a64ed6c0a25b4e359cbadb465`).
The exact released binary has `paciasp` at function entry and `autiasp` at the
crash address. Android 13 calls `prctl(PR_PAC_RESET_KEYS, PR_PAC_APIAKEY, ...)`
from its thread startup routine: the interceptor signs with the old key and
tries to authenticate with the new one. This matches the upstream LLVM defect.
The server saved and exited normally after the stop request.

The retained thread correction builds ASan from checksum-pinned LLVM 17.0.2 source with the
per-function target-attribute correction from LLVM commit
`6bbf0c30ca4449e325beb2d28db00d258d3a1a10`. Only the `prctl` interceptor omits
return-address signing; its BTI entry and PAC in other functions are retained.
The build examines the resulting machine code and refuses a runtime that still
has the faulty instructions. The kernel key reset is still performed normally.
No binary instruction patch or signal-error suppression is used.

Before loading Java, the native runner now creates and joins a thread and
verifies that thread's allocation redzones. It logs `HEAP_ASAN_THREADS_READY`
only after this succeeds. The source archives, license, recipe and patch
provenance are included with the release. The NDK compiler and shared C++ runtime
stay pinned to 26.1.10909125; the bundled prebuilt ASan is replaced by this build.

This fixes an identified defect in the diagnostic. It does not establish the
cause of the earlier in-game allocator crashes. Device startup subsequently
passed in 0.10.29 with all three diagnostic corrections.

## Evidence from 0.10.24

The supplied report contains two native aborts, one interrupted attempt, and
successful local authentication. The two aborts occur at approximately eight
and ten seconds of process uptime. Both stop in the JVM Sweeper thread at
`DependencyContext::remove_dependent_nmethod(nmethod*)+368`, when Scudo detects a
corrupted allocation header. No failing Java character-selection operation is
identified. The server later saves and exits normally after the stop request.

Both failing attempts logged `OPENAL_CONTEXT_READY` and
`WURM_VISIBILITY_POLICY occlusion=disabled`. The visibility correction was
active, but the early crashes prevent confirming its visual effect.

The allocator detects damage while freeing memory; that does not establish
that the freeing code caused the damage. The `0x02` top byte in these reports
is also not evidence of a pointer-tagging defect. Android 13 Scudo deliberately
uses that tag for its internal header accesses, including when tagging is off.
The earlier shader-link and EGL-shutdown aborts may share a source of memory
corruption, but the reports do not prove that.

## Retained memory diagnostic

- Instrument GL4ES, LWJGL native bindings, libffi C code, the owned EGL bridge,
  and OpenAL with AddressSanitizer (ASan), retaining frame pointers and symbols.
- Package the corrected LLVM ARM64 ASan and pinned NDK shared C++ runtimes. Preload ASan first
  in the client entry, window-test and graphics-test children before Java loads,
  and the isolated native startup test which loads no Java.
  Other bootstrap stages, JVM memory tests, the app process and server receive
  no sanitizer preload.
- Use the shared C++ runtime for this diagnostic's OpenAL build, consistent with
  Android's documented ASan exception-handling requirements. The audio source
  version and Android OpenSL ES backend are retained.
- The native runner verifies ASan allocation redzones before Java. ASan intercepts
  `mallopt` and returns zero, so this policy deliberately avoids calling the
  Bionic opt-out through that stub. Missing ASan still fails before Java.
- Before EGL initialization, check a real allocation and its two protected
  boundaries. Only log `ASAN_READY` if the allocation interceptor and both
  redzones work. A missing diagnostic runtime or failed check stops startup.
- Capture sanitizer output in the normal client report. A detected memory error
  takes precedence over the last completed graphics event in the app's status.
  Reports retain library offsets; the matching APK contains symbols for analysis.

ASan checks stay enabled for instrumented accesses and abort on detected errors.
Leak checking is off because this test targets memory corruption. HotSpot's
intentional signal-based checks retain their signal handlers. The existing
collector, heap size, visibility correction and graphics controls are retained;
no sweeper-disable flag or allocator-error suppression is introduced.

The Java runtime and Android GPU driver are not recompiled with ASan. Therefore
a clean diagnostic run would not prove the absence of native memory corruption.
The changed allocator and timing can also change whether a crash reproduces.

## Test on the Thor

1. Keep older apps and saved data. Install the separate `programcache` preview.
2. Import the same complete client and stopped-server runtime ZIPs. Stop any
   older server before starting this preview's local game.
3. Use **Start Local Game**. No standalone startup-check repeat is required.
   Complete character creation if it appears, then move and interact for about
   two minutes, or stop at the first failure. Keep the same graphics settings.
4. Export and send both **Client Report** and **Server Report**, including if
   the test succeeds. The memory checker remains enabled and can reduce speed.

## Verification

The production shader-cache cleanup regression must pass in CI after the pinned
graphics archive is downloaded. Android builds, unit tests, lint and packaging
checks remain required. The downloaded APK is verified against the manifest
and checksum before delivery.

The new NDK regression compiles the same LSan TLS declaration in three modes:
initial-exec native TLS, forced emulated TLS, and a dynamic lookup. The gate
accepts the direct native form and rejects the two resolver forms. A full
ARM64 ASan rebuild passes the TLS, BTI and prctl/PAC machine-code gates; the
actual 0.10.28 release fails the TLS gate. The manifest records the exact
runtime flags, and packaging requires this metadata. The released APK is
checked again against these gates after download.


New host regressions execute faults in an actual shared-library constructor
before `main`, verify that the PC falls in the captured module's executable
mapping, exhaust the main stack to exercise alternate-stack capture, verify
that disabled capture does not change the fault, and restore a previous handler
and alternate stack before normal execution. The ARM64 build rejects recorder
objects with any undefined symbol and runners without a PREINIT_ARRAY entry.
These host checks cannot establish that every Thor preload path reaches preinit.
Kotlin tests verify opt-in isolation and startup/sanitizer summary precedence.


The host test executes the same native allocation and thread checks, then verifies that an
instrumented one-byte overrun produces a heap-buffer-overflow report with the
write and allocation details. Kotlin tests check preload order, stage isolation,
missing-runtime rejection and sanitizer crash-summary priority. Crash tests cover
parent-observed PIDs for live and already-exited children, unsupported formats,
overflow and conflicting later identity output.

CI builds/tests/lints the Android variants, reruns existing native graphics,
audio and input regressions, and checks ARM64 dependency closure. Packaging
requires ASan instrumentation in each client native library, the allocation and thread startup markers,
all public BTI entry points, the diagnostic runtimes, exact asset hashes and the unchanged server POC.
These checks do not replace testing the ASan/HotSpot combination on the Thor.

## Sources and reproduction

- [Pinned GL4ES program cleanup](https://github.com/ptitSeb/gl4es/blob/81547d986798e876de8b434193920b606a72363f/src/gl/program.c): `clear_program` and `fill_program`.
- [Pinned khash API](https://github.com/ptitSeb/gl4es/blob/81547d986798e876de8b434193920b606a72363f/include/khash.h): `kh_foreach`, `kh_del`, and `kh_clear`.
- Checked downstream correction: `scripts/patch-gl4es.py`, `apply_program_cleanup`.
- Production-source ASan reproduction: `tests/test_gl4es_program_cleanup.py`.

- [LLVM 17 build configuration](https://github.com/llvm/llvm-project/blob/llvmorg-17.0.2/compiler-rt/CMakeLists.txt): API detection and native TLS flag selection.
- [LLVM 17 LSan counter](https://github.com/llvm/llvm-project/blob/llvmorg-17.0.2/compiler-rt/lib/lsan/lsan_common_linux.cpp): initial-exec thread-local declaration.
- [LLVM 17 ASan allocation](https://github.com/llvm/llvm-project/blob/llvmorg-17.0.2/compiler-rt/lib/asan/asan_allocator.cpp): unconditional counter lookup for allocation metadata when leak support is compiled in.
- [Android ELF TLS](https://android.googlesource.com/platform/bionic/+/HEAD/docs/elf-tls.md): direct access for initially loaded modules.
- Android compiler regression: `tests/test_asan_tls.py`.


- [Android 13 dynamic linker](https://github.com/aosp-mirror/platform_bionic/blob/android13-release/linker/linker_main.cpp): executable preinit is called before shared-library constructors.
- Owned early recorder: `runtime-probe/native/startup_crash.c`.
- Constructor-fault regressions: `tests/test_startup_crash.py`.


- [LLVM interceptor BTI fix](https://github.com/llvm/llvm-project/commit/1c792d24e0a228ad49cc004a1c26bbd7cd87f030).
- [Android 13 UNIXProcess](https://android.googlesource.com/platform/libcore/+/refs/tags/android-13.0.0_r1/ojluni/src/main/java/java/lang/UNIXProcess.java): public `toString()` formats for live/exited children.

- [LLVM prctl/PAC fix](https://github.com/llvm/llvm-project/commit/6bbf0c30ca4449e325beb2d28db00d258d3a1a10).
- [Android 13 thread startup](https://github.com/aosp-mirror/platform_bionic/blob/android-13.0.0_r1/libc/bionic/pthread_create.cpp): `__pthread_start` resets the key.
- [Android NDK report of the same failure](https://github.com/android/ndk/issues/1848).
- Source build and machine-code gate: `scripts/build-asan-runtime.py`.

- [Android ASan guide](https://developer.android.com/ndk/guides/asan): native
  instrumentation, runtime preload, shared C++ support and diagnostic overhead.
  The Thor runs Android 13; normal app HWASan support requires Android 14.
- [Android 13 Scudo header handling](https://android.googlesource.com/platform/external/scudo/+/refs/heads/android13-release/standalone/combined.h):
  `getHeaderTaggedPointer`, `addHeaderTag` and `deallocate`.
- [Pinned OpenJDK cleanup](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/hotspot/share/code/dependencyContext.cpp):
  dependent-method bucket removal and release.
- Owned startup check: `graphics-compat/native/wurm_heap_check.h`.
- Client launch policy: `ClientNativeHeap.kt`.
- Host regression: `tests/test_native_heap_check.py`.

The release's graphics source archive includes the build recipe, source pins,
owned diagnostics and public dependency archives. It includes no imported Wurm
client or server JARs. Native debug symbols remain in the exact released APK.
