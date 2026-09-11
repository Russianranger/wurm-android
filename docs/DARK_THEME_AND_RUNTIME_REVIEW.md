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
