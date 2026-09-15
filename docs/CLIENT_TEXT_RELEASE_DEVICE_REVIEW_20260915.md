# 0.10.48 device review — 60 FPS then 30 FPS

## Result

The completed-buffer release fix works on the Thor. Both five-minute recordings
have high reuse, successful VBO-layout returns and zero eligibility rejections.
Both client sessions exit zero; the server completes its requested save/shutdown.
No crash, out-of-memory, ASan failure or client GL-error report is present.

This qualifies the release/reuse mechanism, not all memory or performance issues.
The first run does not sustain 60 FPS. The second sustains 30 FPS but has much
higher remaining GUI-job allocation. Both show substantial direct-memory/PSS
fluctuation that is partly reclaimed by natural collection. Do not call the
whole memory problem fixed or label the observed growth a proven leak.

## Source and method

User identifies the first recording as 60 FPS and the second as 30 FPS. Runtime
FRAME_TIMING targets and distinct client PIDs confirm that order. Support export:
2026-09-15T15:53:10.698371Z, package
`io.github.russianranger.wurmlauncher.textreleasefix`, client report 0.10.48.
ZIP SHA-256:
`c8e0886891b7ca277b30042dbaa27d63433bec6df4560f22929c67ff16acb391`.

| Report | Bytes | SHA-256 |
| --- | ---: | --- |
| client-report.txt | 1,438,242 | `950e8acad41319d696879c7b468fd813b58e9fbf6a3bbed2a06df622cafec027` |
| server-report.txt | 144,488 | `ebb9fe7a20339f2179719e2f015a5bef8473aaa839d18607bde9f8481e43a8cc` |
| session.txt | 173 | `b492b097b4fd039f32465a2e3436eea3f821ad78f469eba5bc251d01779e481b` |
| storage-report.txt | 31 | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |

Timestamped duplicates in retained observations and session console are counted
once. Independent sums from the retained section alone reproduce both JOBS and
GUI-row totals. There are 147 distinct pool samples (294 raw duplicated lines),
73 for client PID 7031 and 74 for PID 8029. Each recording contains 61 dedicated
client-recording memory samples and ends automatically with reason=duration.
The same server PID 6852 remains running between the two client sessions;
Android app PID is 5179. Private paths, player details and raw reports are not
published.

| Timeline, UTC | First: target 60 | Second: target 30 |
| --- | --- | --- |
| Game-loop observation | 15:40:45.306437 | 15:46:56.056060 |
| Recording begins | 15:41:04.965691 | 15:47:22.231369 |
| Recording ends | 15:46:05.010527 | 15:52:22.269408 |
| Recorded duration | 300,045 ms | 300,038 ms |
| Original final cleanup begins, second precision | 15:46:24 | 15:52:58 |
| Launcher reports client finished | 15:46:26.633947 | 15:53:00.037006 |

Both use 1280x720, normal logging, Live Map, enabled job profiling and all three
text overlays. The exact private-client SHA matches the tested fixture.
`skipPeriodic=false` in both runs matches the prior recording. Neither session
reaches the ten-minute periodic cleanup point; shutdown collections are separate
from the five-minute measurements.

## Text reuse is now effective

Deltas below run from the first to last pool samples inside each recording.
Counts are process-cumulative at source; the small sample offsets are tens of
milliseconds, not exact recording-boundary instrumentation.

| Five-minute pool metric | Target 60 | Target 30 |
| --- | ---: | ---: |
| Text factory calls | 675,328 | 864,215 |
| Reused buffers | 646,491 | 846,573 |
| Fresh creations, including bypasses | 28,837 | 17,642 |
| Reused / all factory calls | 95.730% | 97.959% |
| Successful returns / VBO-layout returns | 646,618 | 846,911 |
| Bypassed registrations | 28,662 | 17,344 |
| Evicted entries | 151 | 224 |
| Rejected returns or idle checkouts | 0 | 0 |
| Largest sampled active-plus-idle entries, whole session | 130 | 173 |
| Largest sampled system capacity, whole session | 234,120 bytes | 340,680 bytes |

Every rejection reason stays zero. Accepted VBO-layout returns exactly match
successful returns, confirming the previously rejected drawing path is now
accepted. Entry removal is consistent with lazy idle expiry; it is not a
recurrence of release rejection. Sampled capacity remains well under the 256-entry
and 4 MiB limits. The pool reserves only about 229/333 KiB at its sampled peaks,
so the hundreds of MiB in direct-memory measurements are predominantly elsewhere.
Capacity observations do not measure driver metadata or transient buffering.

The last ordinary samples reach 683,992 and 969,835 cumulative reuses,
respectively. These are sampled counts, not exact totals at process exit.

## Frame pacing and allocations

Viewer statistics use sixty periodic windows inside each recording, excluding
startup and shutdown. They measure bitmap updates, not physical display scanout.
Producer FPS closely matches viewer FPS, so the 60-target shortfall is already
present before Android displays the frames.

| Recording metric | Target 60 | Target 30 |
| --- | ---: | ---: |
| Mean displayed FPS | 47.12 | 29.90 |
| Range of five-second displayed-FPS windows | 36.0–57.7 | 28.4–30.2 |
| First-minute mean FPS | 54.24 | 29.88 |
| Last-minute mean FPS | 40.73 | 29.98 |
| Largest recorded frame interval | 430.54 ms | 113.28 ms |
| Published frames skipped by viewer | 9 | 0 |
| Completed job observations | 471,793 | 287,199 |
| Total observed job heap allocation | 181.028 MiB | 369.272 MiB |
| GUI allocation in printed rows | 79.286 MiB | 311.625 MiB |
| GUI calls in printed rows | 12,903 | 8,779 |
| GUI allocation per printed call | 6.292 KiB | 36.349 KiB |

The largest first-run gap aligns with an 85.541 ms young collection followed by
a 318.411 ms full collection at 15:44:02. The recording contains six young
collections and that one full collection, totaling 944.660 ms. The second
recording contains two young collections, 85.921 and 95.705 ms, with no full
collection during recording. The words `Allocation Failure` in normal GC causes
are collection triggers, not an out-of-memory exception.

The 60-target run's reported readback average rises from 4.89 ms in the first
minute to 7.94 ms in the last minute. This supports investigating rendering and
readback cost along with GC pauses. It does not establish a thermal problem:
temperature/clock telemetry and controlled identical scenes are unavailable.
A few GC pauses alone do not explain the sustained late-session FPS reduction.

The allocation comparison is mixed. Relative to the previous 0.10.47 30-FPS
recording's 32.353 KiB per listed GUI call, the new 60-target cohort is about 81%
lower, while the new 30-target cohort is about 12% higher. Do not present the
first number as a controlled optimization percentage. The second recording has
about 98.4 text requests per printed GUI call versus 52.3 in the first, indicating
different text work; these ratios use separately sampled populations and are
approximate. The second run alternates low-allocation GUI windows around 5–6 KiB
per call with much higher windows around 50+ KiB. The high allocations occur on
multiple executor threads, so worker zero itself is not implicated as defective.

These are top-row GUI subtotals, not a complete callee allocation profile. Printed
rows cover 76.14% and 94.27% of total job bytes. The pair cap omits attribution for
1,526 first-run calls (0.323%); their bytes still enter JOBS totals. The second has
zero omitted calls, but the top-row output still omits smaller rows. Every printed
row has zero failures and zero unavailable measurements. Unprinted rows cannot
be assigned zero failures from this output.

## Memory and shutdown

| Client memory during recording | Target 60 | Target 30 |
| --- | ---: | ---: |
| PSS, first → peak → last | 1,111.6 → 1,788.5 → 1,749.7 MiB | 1,503.7 → 1,927.4 → 1,611.3 MiB |
| Direct storage, first → peak → last | 125.8 → 355.5 → 264.9 MiB | 201.3 → 346.1 → 203.9 MiB |
| Heap committed, first → last | 601.0 → 989.9 MiB | 900.3 → 900.3 MiB |
| Heap used, first → last | 396.7 → 794.0 MiB | 652.5 → 757.2 MiB |

Natural reclamation is visible. Around the first run's full collection, sampled
PSS drops from 1,788.5 to 1,487.8 MiB and direct storage from 355.5 to 252.3 MiB.
Around the second run's 15:50:58 young collection, direct storage drops from
346.1 to 197.4 MiB and PSS subsequently reaches 1,602.7 MiB. This demonstrates
reclaimable memory, but does not prove that all remaining residency is necessary
or that a longer repeated route cannot leak. The different starting heap sizes,
workloads and run order also prevent a clean FPS-only memory comparison.

The recording thread terminates after both deadlines: Java/OS thread counts
fall 32/51 → 31/50 and 31/50 → 30/49. Client descriptor counts remain roughly
52–54. Viewer payload allocation stays one per viewer epoch. Server sampled PSS
peaks around 946 MiB and finishes near 938 MiB; it is a separate process.

Client final cleanup completes, executor workers terminate, and both entry
processes exit zero. Server stop is requested at 15:53:05.653482; player/creature/
zone/mesh save messages complete, database connections finish closing, and the
server logs a clean shutdown at 15:53:06.011337. The `java.lang.Exception` attached
to the INFO shutdown call records its call site; it is not a crash. Existing
linker RPATH, duplicate-template/login-server and already-stopped OpenAL warnings
remain startup/shutdown noise in this bundle. Storage audit has not been run,
so this review does not assert a completed storage-integrity audit.

## Next development target

Keep the completed-buffer fix. Investigate the remaining allocations within
GUI rendering, especially the high-allocation intervals in the second recording,
with bounded attribution to the relevant text/widget/overlay work before changing
another allocator. Compare low/high GUI work within the same FPS setting; the
current recordings already establish that release eligibility is fixed.
Investigate the 60-target renderer/readback slowdown separately with timing that
can distinguish scene/render cost from transfer and pacing. Do not change heap,
collector or periodic-cleanup policy solely on this evidence, and do not claim
that 30 FPS itself causes the higher GUI allocation.

No repeat of the unchanged release-path test or synthetic memory stress test is
needed. A later longer same-route recording can qualify memory after the next
specific allocation change. This review updates documentation only; 0.10.48 and
its immutable APK/tag remain the current test release.
