# 0.10.47 text-buffer reuse device review — 2026-09-15

## Result

The user reports legible text with no visible degradation. The approximately
10m21s game session exits cleanly and stays near its 30 FPS target. However,
**the text-buffer optimization never reuses a buffer on this device run**.
Visual compatibility is confirmed by the user; the intended allocation reduction
is not working. The received recording is sufficient to establish that problem.
Do not request another identical recording before investigating the release path.

## Evidence and method

Support export: 2026-09-15T13:52:01.173071Z, version 0.10.47, package
`io.github.russianranger.wurmlauncher.textbuffertest`. ZIP SHA-256:
`834a0f4d0100fd9e821cfb78a92db4f810bb65a3be61f31e5bad4c7787e1520b`.

| Report | Bytes | SHA-256 |
| --- | ---: | --- |
| session.txt | 173 | `5fc4a0ee543faa176ddd24e9e95895f2895e70891fa76a7e4d8e62891d8ca807` |
| storage-report.txt | 31 | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |
| client-report.txt | 778,604 | `0ab9eab67baf24e844f1413b90be6fccdce1a382bd362f7c20e22095886a7d74` |
| server-report.txt | 138,558 | `0f7179fd2413e2d95b3962738f57d58b1fe656de0dca3a5b68c53cf9f5c2fc2a` |

Timestamped observations duplicated between retained history and the console
are counted once. A separate sum over the retained-history JOBS rows reproduces
the aggregate below. All 164 raw pool samples (82 unique timestamps) have zero
reuses and zero successful returns. One client PID, 29004, is present; server PID
28813 and Android app PID 26796 accompany it. The supported private client hash
matches the inspected fixture. Live Map is enabled. Normal logging, job profiling,
text reuse and the 30 FPS target are confirmed. `skipPeriodic=false` matches the
previous five-minute test. Private paths, identifiers and raw reports are not
published.

Game-loop observation: 13:41:17.148872 UTC. Original final cleanup begins at
13:51:38 UTC (second-resolution timestamp). The five-minute recording starts at
13:41:54.561675 and ends at 13:46:54.598698, reason=duration, elapsed 300,037 ms.
It contains 61 client-recording memory samples. The recorder thread disappears
after expiry: Java threads 32 -> 31 and OS threads 51 -> 50.

## Reuse failure

All three private text overlay classes report TEXT_BUFFER_PATCH_ACTIVE and the
helper reports enabled=true, initialized=true. The package/configuration is
correct; a disabled checkbox or missing overlay is not the explanation.

| Counter | First recording sample | Last recording sample | Delta |
| --- | ---: | ---: | ---: |
| Factory calls / fresh buffers created | 56,342 | 579,434 | 523,092 |
| Reuses | 0 | 0 | 0 |
| Successful returns to pool | 0 | 0 | 0 |
| Bypassed registrations | 6,965 | 33,922 | 26,957 |
| Removed/evicted registrations | 49,373 | 545,454 | 496,081 |

The last ordinary sample, at 13:51:10.360866, has **1,015,783 creations and zero
reuses**, with 966,563 evictions, 49,216 bypasses and four active registrations.
This is the last sampled cumulative count, not the exact count at process exit.
No idle entries or zeroed reuse bytes are recorded. Peak sampled registration
is 62 entries / 156,600 bytes, far below the 256-entry / 4 MiB limits. Sampled
capacity does not bound unseen transient peaks, but capacity alone cannot explain
zero successful returns: release eligibility does not depend on free capacity.

Source inspection narrows the issue. `ClientTextBuffers.release` is removing
registered entries and calling original deletion instead of making them idle.
With zero successful returns, neither idle expiry nor idle-reuse eviction accounts
for these counts. The release eligibility conditions cover references, vertex
size, locks, the bound-buffer flag, GPU mode and system-storage shape.

The previously identified fixed-function `boundBufferObject` behavior is the
leading explanation: the engine may leave this per-object flag set after a draw,
while the new helper treats it as an active binding and refuses reuse. **The log
does not report the rejecting condition, so that exact cause remains an inference.**
Do not simply remove the binding guard or reset graphics state on a worker.
The existing real-private host GPU test exercised the VAO/shader bind path; it
did not qualify the full fixed-function draw/release path implicated by this
result. Confirm the actual path before assigning the cause.

## Allocation and frame observations

The table compares this recording with the documented
[0.10.46 recording](CLIENT_JOB_MEMORY_FPS_REVIEW_20260915.md). Durations/FPS and
cleanup policy are similar, but the scene, text workload and route are not proven
identical. Higher observed allocation is a finding, not a controlled attribution
of all excess bytes to the new adapter.

| Five-minute job metric | 0.10.46 | 0.10.47 |
| --- | ---: | ---: |
| Completed job observations | 298,025 | 304,291 |
| Observed job heap allocation | 225.179 MiB | 340.426 MiB |
| GUI Renderer bytes in printed rows | 174.937 MiB | 278.219 MiB |
| GUI Renderer calls in printed rows | 8,785 | 8,806 |
| GUI bytes per printed call | 20.391 KiB | 32.353 KiB |
| GUI share of all observed job bytes | at least 77.688% | at least 81.727% |

0.10.47 totals are 356,962,848 bytes, including 291,733,856 printed GUI bytes.
Observed job bytes are about 51% higher and GUI bytes per listed call about 59%
higher. The 128-pair cap omits attribution for 1,872 calls (0.615%); their counts
and bytes still enter JOBS totals. The 132 printed rows cover 91.137% of observed
bytes and all have zero failures/unavailable counters. Unprinted work cannot be
assigned zero failures. Job_executor_0 is not demonstrated to be defective.

Nine complete whole-thread windows inside the recording contain 1,057,598,856
heap bytes over 270.007 seconds, about 3.735 MiB/s, versus 1.629 MiB/s previously.
All have 32 matched threads and zero missing/new/departed/omitted threads. These
windows differ from the exact job-recording boundaries and include non-job work.
The zero-hit pool cannot explain an allocation improvement; its failed-return
and lookup cost should be measured alongside a corrected implementation.

The 60 periodic viewer windows during recording average 29.748 FPS (median 30.0,
range 27.2–30.2). The 118 windows ending more than 30 seconds after game-loop
observation and before final cleanup average 29.807 FPS (median 30.0,
range 26.4–30.2). Corresponding producer mean is 29.790 FPS. All selected viewer
windows have zero skipped published sequences and one retained 3,686,400-byte
payload allocation. These are bitmap-update measurements, not GPU scanout.

GC pauses remain visible in frame timing:

| Collection end UTC | Pause | Following viewer maximum |
| --- | ---: | ---: |
| 13:44:49.111 | 120.208 ms young + 356.134 ms full | 505.37 ms |
| 13:50:01.120 | 0.334 ms young + 589.131 ms full | 617.68 ms |
| 13:51:16.959 | 560.548 ms periodic full collection | 578.31 ms |

The ten-minute World.tick request is explicitly recorded at 13:51:16.398 with
action=collect. It occurs after the five-minute recording; it explains a later
pause but not the earlier allocation-failure collections. The previous short
run ended before this request. Shutdown collections of 397.576 and 344.887 ms
occur after final cleanup and are excluded from gameplay timing.

## Memory, errors and shutdown

Values below are MiB; direct-buffer use is included in process memory and must
not be added to PSS.

| Sample | Client PSS | Heap used | Heap committed | Direct buffers |
| --- | ---: | ---: | ---: | ---: |
| Recording start | 1,120.9 | 432.4 | 625.9 | 136.1 |
| Recording/session sampled PSS peak | 1,816.9 | 575.2 | 625.9 | 366.6 |
| Recording end | 1,647.3 | 743.5 | 989.9 | 197.9 |
| Last ordinary sample before shutdown | 1,638.5 | 777.3 | 989.9 | 177.4 |

Sampled peak PSS is lower than 0.10.46's 2,323.0 MiB, but the pool never reused
anything, so this cannot establish the proposed optimization's benefit. Direct
buffer counts go 13,976 -> 45,813 at the peak -> 22,518 at recording end -> 20,419
at the last ordinary sample. This is not monotonic growth. Some memory is
reclaimed naturally; sustained memory behavior is still unqualified.

Later in-play full collections leave approximately 498 MiB, then 620 MiB, then
556 MiB of Java heap used. The last is the explicit periodic collection, not a
second matched natural collection. There is no monotonic post-full-GC floor in
this short set, and no basis to claim either a confirmed leak or a leak fix.
The existing engine buffer-accounting counter is not a retained-memory measure.

No unhandled Java exception, client/server crash, OOM, ASan error, client GL error,
frame-read/copy failure or server SEVERE record was found in the supplied evidence.
KHR_debug installs without the earlier mipmap error recurring. Known Android
linker DT_RPATH warnings, OpenAL scheduling/D-Bus fallbacks and the already-stopped
cleanup warning remain. Server duplicate-template and login time-sync warnings
remain; the shutdown Exception is an INFO diagnostic following a requested stop.

Client bootstrap/entry exits are all zero. Server exit is zero after 644,302 ms,
stopRequested=true, force=false; player/world saves and completed database close
are present. After warm-up the server retains 29 Java/53 OS threads and about
0.092 MiB direct storage; descriptors fall to 74. These observations do not verify
a later restore. No storage audit was run in this bundle.

## Next development step

1. Reproduce the failed return through the exact private client's full text draw,
   fixed-function binding and Queue cleanup path; inspect which eligibility field
   rejects it. Add bounded reason counters if runtime evidence is needed, using
   the existing sampler. Avoid another identical blind device recording.
2. Correct the release transition only where the original queue has relinquished
   ownership. Test GPU upload/current contents, draw order, shared references,
   locks, cached/native binding state, deferred deletion and bounds. Measure
   allocation cost in both the successful and failed-return paths. Keep scheduler,
   collectors and server behavior unchanged during this investigation.
3. Retest a corrected build at 30 FPS, matching workload and cleanup settings,
   before longer memory qualification. Until then, text reuse can be turned off
   on the next client start: it has no observed benefit in this run. Keep the
   existing working app/backups; the zero-hit finding is not a save corruption.

The client report's generic Gate status paragraph still describes 0.10.45/46;
its version/package and actual runtime records correctly identify 0.10.47. Refresh
that explanatory paragraph in the next implementation release.

This review updates documentation only. No new APK or runtime fix is claimed.
