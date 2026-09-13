# 0.10.43 — frame reuse and quieter diagnostics

This starts phase 4 of the agreed production-readiness plan: performance and
release qualification. The user reports 0.10.42 worked. The native renderer,
ASan instrumentation, audio, Java runtime, collectors/heaps, frame transport,
resolution/FPS defaults, server preparation and input behavior are retained.
This release does not claim an FPS gain or completed production qualification.

Subsequent device evidence: the user's approximately 35-minute 0.10.43 session
confirms payload reuse, near-30-FPS play and normal exits, with recoverable GL,
audio and GC-hitch findings. See the
[extended session review](CLIENT_FRAME_PERFORMANCE_REVIEW_20260913.md).

## Device evidence

The new support bundle is 82,503 bytes, SHA-256
`712d9f9e76fee5b82d1226b4d535e780ec453a2eb79791f5461fab3a48ee8969`.
It was exported at 2026-09-13T17:02:32.596781Z from `.stockdbfix` (0.10.42).
The retained run shows successful player login, Survival and Live Map READY,
client entry exit 0, and requested server shutdown with player/creature/zone/map
saving followed by exit 0. The actual server JAR is the previously prepared
`9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06`
and preflight checks the existing localhost/sqlite directory. This bundle
corroborates the prepared-runtime path; fresh-import success is user-reported,
not a stock-recipe trace in this particular retained session.

No ASan fatal report, OOM, fatal signal or SQLITE_CANTOPEN was found. Two native
GL error observations remain during initial entry: code 0x500 at integer-query
candidates, and 0x502 with colour/uniform/texture candidates. The breadcrumbs
identify collection points/recent candidates, not proven originating GL calls.
They are not suppressed or declared universally harmless. Gameplay recovered;
this short run is not a 60–90-minute soak result. Duplicate item templates,
login-server time-sync notice and normal shutdown INFO stack remain familiar
content/role/teardown messages in this run.

## Pixel storage

The old Android reader allocated 3,686,400 payload bytes for each 1280x720 raw
frame: about 105.5 MiB/s at 30 frames/s. That is allocation volume calculated
from the source, not physical disk writes or an observed throughput improvement.

The viewer now uses FrameBuffers with at most two leased slots. Its existing
single in-flight reader/UI delivery normally uses one slot. Storage is allocated
lazily, grows within the existing 1280x1024 bound, and is reused at equal/smaller
sizes. The direct 720p path needs one 3.52 MiB raw buffer after warm-up. The ARGB
fallback also reuses its conversion array. At maximum size, two raw plus two
fallback arrays retain at most 20 MiB; fallback arrays exist only when needed.
Small frame/stream/metadata objects still allocate. The existing Bitmap is reused.

A slot belongs to the reader until its posted UI callback finishes copying or
discarding the frame. The UI closes the lease in finally, including stale
sessions, pauses, destroyed activities and failed copies. A failed/truncated
read releases its slot. Pool shutdown retains outstanding leases until their
owners release them. No mutable payload array is kept by GameFrameView; it
stores dimensions/pointer metadata and the copied Bitmap. Android's
[Bitmap copy API](https://developer.android.com/reference/android/graphics/Bitmap#copyPixelsFromBuffer(java.nio.Buffer))
copies pixel content from the buffer. Normal/fallback origin, channels and
pointer geometry remain unchanged. Atomic file-open/sequence validation remains
authoritative, including multiple frames with identical file timestamps.

## Logging and measurements

Diagnostics now offers **Verbose client diagnostics**, default off and changeable
while the client is stopped. The choice takes effect at the next start and is
included in complete backups. This is a logging preference, not a different
native build or a switch that disables ASan.

- Normal mode retains bounded, flushed shader source/compile/link/status traces,
  all delegate exceptions, GL/native crash evidence and startup checks. Repeated
  active-uniform/attribute and location lookup pairs are omitted. Those lookups
  do not consume the compile/link trace budget. Verbose restores their details.
  Shader text is never logged. A native abort during an omitted lookup has no
  Java BEGIN pair for that call; use verbose when investigating that path. The
  native draw/error instrumentation remains active in both modes.
- Connection states and changed reasons are immediate. Changing traffic counters
  alone produces a five-second summary in normal mode; verbose retains the
  one-second detail cadence. Read-only sampling, timeout decisions and packet
  behavior are unchanged. The app no longer logs a duplicate status line just
  because the elapsed-time label changed.
- Normal mode keeps first-frame and five-second producer timing reports rather
  than a sequence message every 25 frames. Producer samples now carry time/PID.
  Viewer measurements are timestamped with a session epoch and include update
  interval p50/p95/p99/max, read/copy p95, skipped published sequences, and
  cumulative payload allocation/retained-byte counts for that viewer instance.
- Viewer percentiles describe successful Bitmap updates, not GPU execution,
  physical scanout or child-JVM GC. Each five-second window retains the last
  512 samples; total count/max cover the full window. Windows drain across
  reporting boundaries and reset on pause/new session so background time is
  not represented as a gameplay stall. Do not average window p95s into a global
  p95. A zero-frame window is reported while the client remains active.
- Periodic performance, memory, mod and error observations are retained separately
  from rotating console output, bounded to about 2 MiB with a 1 MiB retained tail
  after rotation. This provides room for a typical 60–90-minute qualification
  session; repeated sessions or unusually noisy observations can still rotate it.

## Validation

Local Android API compilation covers all 98 application/test Kotlin sources.
Nineteen focused Kotlin tests cover original frame parsing plus pooled 720p
reads, both pixel paths, exact colours/orientation, cross-thread lease ownership,
bounded exhaustion, corruption, resize/format changes and disposal. Percentile,
sequence-gap, pause reset, bounded samples and observation rotation are tested.

The host suite passes 182 tests with 30 unavailable fixture/platform skips
locally. Its seven graphics-trace tests cover unchanged delegates/exceptions,
normal/verbose policy, trace budgets and a flushed unfinished native call after
an abrupt exit. Four connection tests cover real authored state transitions,
periodic counters, immediate error/reason changes and read-only behavior. CI
also passed its required actual upstream native/input fixtures and all three
Android build/unit/lint gates in
[run 34771446319](https://github.com/Russianranger/wurm-android/actions/runs/34771446319).
The initial CI suite has 23 expected fixture/platform skips; the later native
checks retain one unavailable real-host-EGL shader-compilation skip.

The published APK independently passes its contents verifier, published checksum,
package/version and v2 signature checks. All 194 JRE members, POC and 39 of 40
native libraries match 0.10.42 exactly. GL4ES differs only in 20 build-ID bytes
and five compile-time string characters. Full release/commit/certificate identities
are recorded in HANDOFF.md. Subsequent device performance results are linked
above; lifecycle and longer-soak qualification remain incomplete.

## Thor qualification

Version 0.10.43, code 57, package
`io.github.russianranger.wurmlauncher.frameperf`, immutable tag
`v0.10.43-frame-performance`. Preview signing remains per-run. Keep 0.10.42
installed; a complete backup moves your current world, client, mods and settings.

1. Normally stop both runtimes in 0.10.42 and export a complete backup. Install
   0.10.43 alongside it and restore that backup. A fresh stock import is not
   required for this performance comparison.
2. Leave Verbose client diagnostics off. Keep the same graphics options,
   resolution and frame cap for comparison. Play and check orientation/colours,
   touch/controller pointer, chat, gear/graphics settings and inventory changes.
3. Return to the launcher/game, background and resume, and try screen recording.
   There should be no frozen, torn or stale frame. Change render resolution and
   restart once to exercise buffer resizing, then return to the baseline setting.
4. When convenient, run 60–90 minutes of mixed movement, menus, crafting and
   inventory work. Save/stop normally, restart, confirm persistence, then export
   the support bundle. Note the duration, visible hitches and anything that was
   done immediately before a problem. Host tests cannot supply this device result.
5. If a problem occurs, export the normal-mode bundle first. For a separate
   reproduction, stop the client, enable verbose diagnostics, repeat the issue
   and export again. Keep the original backup and working older app.

Later qualification remains: investigate remaining startup GL behavior, compare
matched normal/ASan native builds as a separate controlled change, complete
persistent signing/stable updates, and broaden device coverage. Do not change
GC/native behavior in this comparison or declare production readiness from
another short successful session.
