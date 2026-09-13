# 0.10.43 extended Thor session review — 2026-09-13

The user reports everything remained functional during extended play, shorter
than the requested 60 minutes. This bundle supports a successful approximately
35-minute session, normal saves/shutdown, and effective viewer payload reuse.
There are recoverable graphics errors, an audio decoding failure, and brief GC
pauses to follow up. This is partial qualification, not a completed 60–90-minute
soak or proof of an FPS improvement over 0.10.42.

## Evidence and method

- Support ZIP: 160,762 bytes, SHA-256
  `92bd9d8c5000f08151af3ccf93dc5e47c4f5764d0b2b9206866ca6f9fab261e9`.
  Session export: 2026-09-13T18:30:59.999750Z. Version 0.10.43, package
  `io.github.russianranger.wurmlauncher.frameperf`, Android 13/API 33.
- Client entry starts 17:54:56.692Z, first GAME_LOOP observation 17:55:04.214Z,
  final game cleanup 18:30:34Z, reporting 35 minutes played. Client entry exits
  0; the final window sequence is 63,476. Server runs 35m56.809s and exits 0
  after a requested, non-forced stop at 18:30:47.737Z.
- Exact duplicate timestamped records in observation history and console are
  counted once. Identical untimestamped console warnings are counted separately
  when they represent separate occurrences.
- Steady-play summaries select periodic records timestamped between
  17:57:04.214Z and 18:30:34Z, excluding the first two minutes and shutdown.
  They include the later graphics exception. FPS means below are means of
  roughly five-second windows. Window percentiles are not global percentiles.
  Viewer timing measures successful Bitmap updates, not GPU/display scanout.

## Frame performance and memory

| Measurement | Observed result |
| --- | --- |
| Viewer timing | 402 steady-play windows, mean 29.831 FPS, median 30.0, window range 25.8–30.2 |
| Producer presentation | 401 windows, mean 29.835 FPS, median 30.0 |
| Viewer update intervals | Median window p50 33.29 ms; median window p95 35.89 ms; largest window p95 50.64 ms |
| Viewer read/copy | Median window read p95 1.73 ms; copy p95 1.79 ms |
| Payload reuse | One allocation after initial zero-allocation startup; 3,686,400 retained bytes (3.52 MiB), unchanged through last frame |
| Delivery | 60,064 updates and zero skipped published sequences in steady-play windows; one skipped sequence across the complete viewer history |
| Longest viewer gap | 519.35 ms in the window ending 18:05:05.233Z |

All retained viewer records use one epoch and raw-copy support; no
FRAME_READ_ERROR, FRAME_COPY_ERROR or FRAME_READBACK_ERROR is present. Allocation
telemetry corroborates reuse of the large payload array. It does not imply that
all frame processing is allocation-free or that FPS improved over the previous
release. Native renderer and JVM policies were unchanged in this release.

Memory below uses 61 samples per process, after the first five minutes of play
and before cleanup. PSS is reported proportional resident memory, in MiB.

| Process | Sampled PSS range | Median PSS, minutes 5–10 | Median PSS, final 5 minutes |
| --- | --- | --- | --- |
| Android app | 102.8–130.2 | 116.0 | 113.8 |
| Client JVM | 1,522.4–1,849.3 | 1,641.1 | 1,693.0 |
| Server JVM | 795.9–814.2 | 799.6 | 800.7 |

Client heap fluctuates between 442.9 and 781.7 MiB, direct buffers between
181.8 and 292.2 MiB. Client file descriptors stay at 55–56 and threads at 47;
server descriptors cycle between 75 and 120 and threads stay at 53. Neither JVM
reports swap use. No obvious runaway memory/resource growth appears in this
interval, but the client has a substantial footprint and a modestly higher
late-session median. A single session cannot exclude slow leaks.

There are 35 client GC pauses during the observed game-loop interval. The
largest is a 492.411 ms System.gc() full collection at 18:05:03.994Z, in the
same viewer window as the 519.35 ms gap. Further System.gc() full collections
at 18:15:03.592Z and 18:25:03.579Z take 387.456 and 437.251 ms; corresponding
viewer-window maxima are 425.40 and 461.77 ms. An allocation-triggered full
collection at 18:00:40.857Z takes 329.217 ms, with a 485.50 ms viewer gap in
that window. These are plausible contributors to brief visible hitches, not
crashes. Any GC experiment should be a separate controlled comparison,
preserving this measured baseline.

## Errors and warnings

Three unique native graphics-error records remain:

| Time (UTC) | Error | Context |
| --- | --- | --- |
| 17:54:58.577 | 0x500 | Startup integer-query candidates |
| 17:55:05.804 | 0x502 | Initial entry, colour/uniform/texture candidates |
| 17:57:51.443 | 0x502 | Early play, colour/uniform/depth/enable candidates; CLIENT_GL_ERROR before readback at frame 4904 |

The third observation accompanies an IllegalStateException through
WindowBackend.swap and the game's render loop. It is a pending GL error detected
before capture, not an Android frame-array read/copy failure. The next retained
viewer window reaches sequence 5000 at 29.5 FPS; gameplay continues for more
than 32 minutes afterward with no further native graphics-error record. These
breadcrumbs identify recent candidates, not a proven originating call. Do not
classify all three as startup-only or suppress them as harmless.

At 17:59:34 the client's Ogg decoder logs SEVERE messages that input is not an
Ogg bitstream and identifies `res/missingsound.ogg`. OpenAL then reports two
separate `Invalid sample rate 0` warnings (code 0xa003), the second a few seconds
later. This suggests an invalid/missing sound or fallback resource reached audio
upload, but the exact asset/call path is not established by this bundle. A sound
effect may have failed; gameplay continued. It is an observed issue, not proof
that 0.10.43 introduced an audio regression.

The server has six unique startup WARNING records: a reused eye template, four
duplicate guard-tower definitions, and the login-server time-sync notice. No
later server WARNING or actual SEVERE record is present. The shutdown INFO
exception stack is the deliberate shutdown trace. Familiar linker DT_RPATH,
optional GL capability, normal-matrix/texture and audio-priority fallback
messages remain, distinct from the GL and sound failures above.

No ASan fatal report, fatal signal/SIGSEGV, OutOfMemoryError, SQLITE_CANTOPEN,
world database-path failure, abnormal child exit or frame read/copy failure
was found in the supplied reports.

## Saves, limits and next work

Server preflight validates nine databases under the existing localhost/sqlite
directory; Survival and Live Map reach READY. Normal shutdown logs player,
creature, zone, mesh and other world saves before exit 0. Client settings and
player-file saves also complete before exit 0. This corroborates normal save
and shutdown behavior, not a post-restart comparison or full SQLite integrity
audit. The storage report explicitly says no storage audit was completed.

Only the initial viewer resume and final pause are visible; this bundle does
not demonstrate a mid-session background/resume, resolution change or subsequent
restart. User-reported overall functionality is retained separately from these
specific unobserved checks.

Keep 0.10.43 as the working baseline. Next targeted investigations are the
remaining GL errors and invalid sound/fallback path; investigate periodic GC
hitches separately. Longer mixed-play/lifecycle/persistence qualification,
matched normal/ASan native comparison, stable signing and broader devices remain
on the roadmap. This review changes documentation only, with no new APK. Keep
private raw reports and proprietary assets out of git and release assets.
