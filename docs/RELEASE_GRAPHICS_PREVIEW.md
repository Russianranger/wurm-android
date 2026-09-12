# 0.10.35 — world settings and in-game bindings

The user reports 0.10.34 stable. This build adds:

- Server → World gameplay settings: 16 controls, including skill gain, action speed,
  new-character starting skills, breeding, field/tree growth, creature population,
  upkeep and deed costs. Displays verified bounds, reads actual values, saves while
  stopped with a consistent database backup, and applies at next server start.
- Gear → Game keybindings: Wurm's native action/category catalog, key picker,
  modifier keys, conflict checks, atomic persistence and live reload.
- Gear → Controller mappings: changes reload when you return to the running game.
- 1280 × 720 as the unset/default resolution; explicit previous choices are retained.

Install the separate `.worldcontrols` package alongside the stable app. Import
its normally stopped **working** server export and your existing client ZIP.
The graphics/audio fixes, dark theme, white pointer and memory policies are retained.

Test settings save/reopen, keybinding save and client restart, then controller
remapping without restarting the game. Export both reports from Diagnostics.
[Details and device checklist](WORLD_SETTINGS_AND_BINDINGS.md).
[Maintained project handoff](HANDOFF.md).

---

## Previous: 0.10.34 — three tabs and graphics controls

The 0.10.32 device tests passed repeated logout/login and app reentry with normal
client/server exits. Nearby object pop-in is resolved. This release:

- Adds fixed Server, Client and Diagnostics tabs. Tests, reports and live logs
  move to Diagnostics. Client includes Return to Game; navigation keeps services running.
- Expands graphics from 17 to 47 controls, including texture filtering/quality,
  terrain detail, particles, animations, field of view and supersampling.
  Restart-only choices are labeled and deferred; unavailable settings are explained.
- Prefers a 24-bit EGL depth buffer with a verified 16-bit fallback. Unsized
  offscreen depth formats use 24-bit precision when supported. This targets the
  distance-dependent beam flicker; visual improvement is not yet device-confirmed.
- Retains the dark theme, white pointer, audio, occlusion-query fix and native
  memory checking. Hidden launcher pages no longer build or refresh log text.

The APK uses a separate `.graphicstabs` package to preserve the prior install.
Keep the old app and backups. Import your stopped, exported working server runtime
and client ZIP into this version; do not restore the original world unless intended.

On the Thor, start local play, repeat the building-distance walk from the video,
change one live setting and one restart-only setting, then test each tab and Return
to Game while the server/client are running. Restart the client to apply texture
settings. Export both reports from Diagnostics after stopping normally.

[Complete audit and test checklist](GRAPHICS_AND_TABS.md).
[Maintained project handoff](HANDOFF.md).

---

# 0.10.32 — dark app theme and white pointer

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.32-dark-theme/Wurm-Server.apk).

The app now defaults to dark backgrounds, light text, subdued disabled controls,
and a pale blue accent. Native launcher screens, settings/dialogs and system bars
use the shared dark theme. The game pointer is white with its black outline
retained; the controller-test pointer also uses white. The existing game view,
gear controls, opacity, graphics options, and runtime settings are preserved.

This is an explicit Android Material dark theme, independent of the device's
light/dark preference. Automatic Force Dark is disabled so Android does not
recolor the game image. Shared theme resources and disabled text states follow
[Android's theme guidance](https://developer.android.com/develop/ui/views/theming/darktheme).
External Android file-picker surfaces remain controlled by the operating system.

## Review of the 2026-09-11 13:27:33 reports

Reviewed `wurm-client-report(20260911-132733).txt` and the matching server report.
The client report appends server history; both reports repeat observations in
their retained history and console. Memory/error entries were deduplicated by
the complete timestamped line, and the appended server was not counted as a
second session. ABI class names and the first-error capture's SEVERE header
are not actual exceptions or severe events.

| Event (UTC) | Observed result |
| --- | --- |
| Server starts 12:48:48; TCP ready 12:48:53 | Normal managed startup, PID 17827 |
| Client entry 12:50:15; game loop 12:50:23 | PID 18946, 1280 × 720, 30 FPS |
| Client closes 13:26:36 | Approximately 36m13s since game-loop observation; exit 0, 64,787 published frames |
| Server closes 13:26:56 | Requested normal shutdown, saving logged, exit 0; total runtime 38m08s |

The user confirms the client stayed up throughout. There is no ASan fatal report,
OOM, native fatal signal or frame-readback failure. Client/server stop times
agree with the server's lost-link notice when the client closes. This report
does not show a subsequent restart, so save persistence still needs confirmation.

## Performance and memory

- After discarding the first 12 five-second samples, render/publish rate averages
  **29.87 FPS** and Android display **29.88 FPS**. The lowest remaining five-second
  average is **26.2 FPS**. These averages can hide short visible stalls.
- Median frame readback is **5.85 ms**, down from 7.76 ms in the earlier short
  run; publishing remains **3.11 ms**. Workloads differ, so the readback difference
  cannot be attributed solely to probe throttling. Android frame reading/copying
  occurs separately and these phases must not be blindly summed.
- The controller records **160 TCP checks over 38m08s**, including startup checks
  that can fail before listening. The server logs **156 accepted connections**,
  including the actual client and its startup checks. Steady-state checks are
  about 15 seconds apart, versus about 0.5 seconds previously: the requested
  reduction worked.
- There are 73 client and 73 Android memory samples and 77 server samples. No
  sampler-unavailable marker appears. Every reported JVM swap reading is zero.

Approximate ranges after the first five minutes of each sampler:

| Process | PSS | Other observations |
| --- | --- | --- |
| Client JVM | 1,592–1,777 MiB | 48 OS threads; 29 Java threads; 50–51 FDs; direct buffers about 165–245 MiB |
| Server JVM | 1,007–1,030 MiB | 54 OS threads; 29 Java threads; eight direct buffers, about 95 KiB total; FDs cycle 72–156 |
| Android viewer | 111–143 MiB | Native allocated memory approximately 21.3 MiB; ART heap use rises/falls rather than increasing continuously |

Client PSS grows somewhat as the session warms up, while later full collections
reclaim the Java heap to roughly 562–601 MiB during play. The last shutdown
collection leaves 563 MiB. These observations do not establish a memory leak;
they also cannot exclude one in a longer run. ASan instrumentation remains active
and leak checking remains disabled. Server FD counts fall periodically, rather
than accumulating without bound. Eight large FD-count drops coincide with a
young-GC count increase between samples. This suggests some resources may rely
on GC-driven cleanup; a targeted FD-owner/lifetime review would be useful, but
the aggregate report cannot identify those owners or prove a specific leak.

### Highest-value follow-ups

1. **Reduce client GC stalls.** This run has five allocation-driven full GCs after
   initial loading, taking 384–644 ms. Other young collections during play often
   take about 90–215 ms. Explicit full GCs at 13:00:21, 13:10:21 and 13:20:21 take
   406, 505 and 488 ms. Inspection of the owned client confirms `World.tick()`
   calls `System.gc()` every 14,400 ticks, matching this ten-minute cadence.
   The two explicit GCs at exit are shutdown work and must not be counted as
   gameplay stalls. A controlled client G1 comparison is a sensible next
   experiment; [Java 17's collector guidance](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html)
   describes G1's pause-oriented design and Serial's pause trade-off. G1 is
   already used by this server, but that does not prove it will improve the
   client's different workload. Avoid combining a collector change, heap resize,
   and changes to explicit-GC behavior in one first comparison. Disabling all
   explicit GC calls without checking native/direct-buffer reclamation is premature.
2. **Reduce frame-transfer allocations/copies.** The 1280 × 720 raw frame is
   3,686,400 bytes, about 105 MiB/s at 30 FPS. The Android reader allocates a new
   byte array for each new frame; its Bitmap is already reused. A bounded reusable
   pixel-buffer pool with explicit reader/UI ownership is the next direct code
   optimization. Shared-memory/direct-surface presentation is a larger later
   change. This is byte traffic, not a measurement of physical flash writes.
3. **Rate-limit unchanged runtime messages.** The client console contains 6,083
   shader trace lines, roughly 1,987 connection lines, 2,169 app lines, and 2,592
   frame-sequence messages, despite a stable session. Keep errors, phase changes,
   periodic frame/memory measurements and native crash evidence, while reducing
   repeated unchanged status and frame messages. File appends and report size can
   fall without changing the game. No new logging reduction is applied in 0.10.32.

## Remaining warnings and graphics errors

Six distinct native error observations occur during startup/initial world entry,
all between **12:50:17 and 12:50:42**. Four lead to recoverable pre-capture frame
exceptions. None recur later in the retained run, and the 64-detail limit is not hit.

- Four observations record `GL_INVALID_OPERATION` at
  `gl4es_glBindFramebuffer:263`. That source line stores the existing driver
  `glGetError()` result immediately after binding the framebuffer. It localizes
  the collection point; a pending earlier driver error could still be returned
  there, so it is not yet proof that the bind itself was invalid. Next graphics
  investigation should compare pending/error-after-bind state and record the
  framebuffer target/ID while preserving normal error semantics.
- A startup `GL_INVALID_ENUM` lists integer-query sites. One later
  `GL_INVALID_OPERATION` lists color/uniform/depth/enable sites. Those are bounded
  candidates, not exact attribution.
- Seventeen `_gl4es_NormalMatrix` unsupported-uniform notices remain. Missing
  cobble/sand normal-map resources, hair mappings and 12 clothing warnings may
  affect appearance. The texture-size probe still rejects sizes down to 512;
  this does not establish that the GPU only supports 256-pixel textures.
- Server warnings are duplicate item templates and its expected time-sync role
  notice. No actual SEVERE record or repeated zone-removal exception is present.
  One initial `VisionArea null ... creating one` is INFO, followed by normal play.
- OpenAL creates and frees its device/context. The real-time scheduling warning,
  absent D-Bus, ignored linker DT_RPATH entries and repeated final OpenAL stop
  remain low-priority platform/cleanup messages, without an audio failure here.

## Install and test

Stop the previous app normally and export its working server runtime to preserve
your current character/world. Keep the previous app/data. Install this separate
`.darktheme` package and import the working runtime and same complete client ZIP.
Reselect the same graphics settings, 1280 × 720 and 30 FPS.

Check the server/client screens, player-name and graphics dialogs, gear panel,
disabled buttons, and white pointer over both light and dark game areas. Existing
opacity and fullscreen controls should continue working. Normal gameplay and
report exports remain available; send both reports if anything regresses. No
standalone native-memory test needs repeating for this visual update.

Validation uses the existing Android build/unit/lint gates and native/packaging
regressions. Device screenshots/contrast and physical controls require Thor
confirmation; no emulator screenshot is claimed. Runtime, graphics transport,
GC policy and heap sizes are intentionally unchanged for this visual release.

---

# Previous release: 0.10.31 — runtime observations

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.31-runtime-observations/Wurm-Server.apk).

The 0.10.30 Thor report records about 9m38s in the client game loop, normal
client/server exit zero, and five recoverable pending GL_INVALID_OPERATION
errors near initial world entry. After the first minute, five-second render
and display averages were approximately 30 FPS at 1280 × 720. This establishes
one successful short run, not long-session stability or absence of leaks.

## Changes

- TCP readiness checks stay at 500 ms during startup and change to 15 seconds
  after readiness. Failed later checks retain the slower cadence. Child exit
  and Stop are still polled every 500 ms. Each restart gets a fresh schedule.
  `TCP_PROBE_POLICY` and `TCP_PROBE_SUMMARY` make the cadence/count reviewable.
  The old run accepted 1,205 localhost connections in about ten minutes;
  the new steady-state schedule creates approximately 40 checks in that time.
- GL4ES records the function and source line that stored a shim error. Driver
  errors list up to eight recent `errorGL` sites as candidates, not proven
  failing GLES calls. Fixed per-thread storage adds no GL queries or hot-path
  allocations. Logging occurs only on a nonzero result returned by the existing
  getter, capped at 64 detailed errors per GL thread. Existing error returns,
  resets, and frame exceptions remain intact. Errors suppressed by GL4ES itself
  are not made visible by this diagnostic. Pending frame-capture exceptions gain
  a timestamp/frame number. Native attribution includes UTC epoch time and PID.
- Client and server JVM daemon threads sample every 30 seconds, starting near
  entry. Each line identifies role, UTC time, PID, uptime, Java heap
  used/committed/max, non-heap use, direct/mapped buffer use/counts, Java thread
  count, cumulative per-collector collection count/time, RSS/high-water RSS,
  anonymous RSS, swap, OS thread/FD count, and PSS where available. `-1` means
  unavailable, never zero use. Proc files and directory streams close each time.
  The sampled FD count may include the sampler's own directory descriptor.
- The Android viewer has its own 30-second background sampler during the client
  entry stage. It reports ART heap use, native allocated bytes and PSS/private
  dirty memory. Its worker is interrupted when the client operation ends.
  Android allocations and HotSpot client GC are separate measurements.
- Low-rate memory/error observations are also retained separately from console
  rotation. Each history is capped around 512 KiB and retains the latest half
  on rotation. Client/server exports include these histories, identified as
  possible duplicates of console entries. Compare timestamps/PIDs across attempts.

PSS/RSS are process memory measures, not native-heap allocation totals; buffers
and memory mappings overlap those totals and must not simply be summed. JVM GC
times are cumulative milliseconds, so compare deltas between samples. PSS can
be unavailable on Android. No sampler forces GC or inspects heap objects. ASan
remains enabled with leak detection disabled. No heap sizes, collectors,
rendering settings, frame transport, audio or world files are changed.

## Physical-device test

1. Stop the existing client/server normally and export the working server runtime
   if you want to carry forward the current character/world. Keep that ZIP and
   the older app. Development signing requires this separate `.runtimemetrics`
   package; existing app-private data/settings do not migrate automatically.
2. Install 0.10.31, import that stopped runtime ZIP and the same complete client
   ZIP. Choose the same world/player and restore the same graphics options,
   **1280 × 720**, and **30 FPS** for a useful comparison. No standalone native
   startup or JVM-memory check needs repeating for this test.
3. Start local play. Complete creation if using a new character. Move/interact
   for **30–45 minutes** using the same graphics settings. Note any freeze,
   missing objects/UI, audio failure, or time of a graphics change.
4. Stop client and server normally, wait for saving to finish, and export both
   reports before starting another session. Then restart with the same player
   and verify character/world progress survived. Export a second pair afterward.
   If a crash occurs, export immediately and report what was happening.

Review priorities: lower accepted-connection count, useful GL error attribution,
memory/FD/thread trends rather than a single high-water mark, GC pause deltas,
frame timing, normal exit, and persistence. No claimed performance improvement
or resolution of the pending graphics errors is established until device testing.

## Verification

The production TCP schedule is tested over a simulated ten-minute run, startup
deadlines, late ticks and session reset. A host JVM verifies real memory fields,
unavailable proc metrics, closed descriptors over 100 samples, and daemon exit.
The actual pinned GL4ES getter/helpers are run against a deterministic driver:
the original and instrumented versions return identical errors and make identical
driver-query counts; first-error attribution and output bounds are verified.
CI compiles the complete ARM64 GL4ES build, checks all Android variants/unit tests/
lint, reruns native regressions after downloading source fixtures, and checks
the packaged observer classes, patch manifest, native markers and checksums.

---

# Previous release: 0.10.30 — shader cache cleanup fix

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
