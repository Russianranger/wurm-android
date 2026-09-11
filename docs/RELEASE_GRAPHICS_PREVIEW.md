# 0.10.26 — heap diagnostic thread startup fix

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.26-heap-startup/Wurm-Server.apk).

This is a slower diagnostic build, not a confirmed crash fix or a performance
comparison. Run one short attempt to character creation, then export both reports.

## Evidence from 0.10.25 and the startup correction

Both supplied attempts exited 132 / SIGILL before Java initialization. ASan's
allocation check passed, but the child then stopped in `__interceptor_prctl+776`
at library offset `0x83fcc` (build ID `b1e02acf349a988a64ed6c0a25b4e359cbadb465`).
The exact released binary has `paciasp` at function entry and `autiasp` at the
crash address. Android 13 calls `prctl(PR_PAC_RESET_KEYS, PR_PAC_APIAKEY, ...)`
from its thread startup routine: the interceptor signs with the old key and
tries to authenticate with the new one. This matches the upstream LLVM defect.
The server saved and exited normally after the stop request.

This release builds ASan from checksum-pinned LLVM 17.0.2 source with the
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
cause of the earlier in-game allocator crashes, and device startup is unverified.

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

## What this build changes

- Instrument GL4ES, LWJGL native bindings, libffi C code, the owned EGL bridge,
  and OpenAL with AddressSanitizer (ASan), retaining frame pointers and symbols.
- Package the corrected LLVM ARM64 ASan and pinned NDK shared C++ runtimes. Preload ASan first
  in the client entry, window-test and graphics-test children before Java loads.
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

1. Keep the working older apps and exports. Install the separate `heapcheck`
   preview and import the same complete client and server runtime ZIPs.
   Stop the older server before starting this one.
2. Use an unused player name such as **Thorcheck**. Client login identity still
   does not migrate between separate previews, including through a world export.
3. Keep the same graphics settings for this attempt and start local play.
   Try to reach and complete gender/kingdom selection. No separate JVM memory
   test is required.
4. If the client stops, export both reports immediately. If it remains running,
   move briefly and stop after about two minutes, then export both reports.
   Poorer FPS is expected in this diagnostic and is not a regression measurement.
5. Send the reports even if the game fails before a window appears. Sanitizer
   startup failures and early invalid-access reports are useful evidence.

## Verification

The host test executes the same native allocation and thread checks, then verifies that an
instrumented one-byte overrun produces a heap-buffer-overflow report with the
write and allocation details. Kotlin tests check preload order, stage isolation,
missing-runtime rejection and sanitizer crash-summary priority.

CI builds/tests/lints the Android variants, reruns existing native graphics,
audio and input regressions, and checks ARM64 dependency closure. Packaging
requires ASan instrumentation in each client native library, the allocation and thread startup markers,
the diagnostic runtimes, exact asset hashes and the unchanged server POC.
These checks do not replace testing the ASan/HotSpot combination on the Thor.

## Sources and reproduction

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
