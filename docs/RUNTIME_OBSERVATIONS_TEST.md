# 0.10.31 — runtime observations

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
