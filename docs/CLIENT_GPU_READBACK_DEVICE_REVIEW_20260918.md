# 0.10.52 device review: pipelined GPU readback

Reviewed 2026-09-18 from `wurm-support (1)(3).zip`, exported at
00:38:53.635273Z. The user reports very smooth play with a few stutters, with
only the 60 FPS pipeline-enabled test performed. This review changes documentation.

## Result

The pipeline is active and performs well on this Thor. The full five-minute
recording averages 58.35 displayed FPS; complete bins in the final 90-second
segment average 59.51 FPS. CPU time spent issuing/collecting readback is 0.625 ms
per captured frame, compared with about 7.75 ms synchronous readback in the prior
build's background-delivery recording. Publication wait remains negligible.

This supports keeping the pipeline enabled on this device. It is one on-run,
not a matched off/on comparison or proof of constant 60 FPS on other workloads.
No new fatal client, readback, publication or out-of-memory error was found.
Client and server shut down normally, with the server completing saves.

The remaining visible problem is intermittent pauses. The largest displayed
gap, 404 ms, aligns with consecutive young/full garbage collections totaling
385 ms. Some smaller gaps align with client work without a matching collection
and remain unclassified. Steady readback is no longer the dominant measured
CPU cost in this run.

## Session and measurement boundaries

App: `0.10.52-managed-preview`, code 66, `.gpupipeline`; client PID 26716.
Target stays at 60 FPS, 1280 x 720, performance preset, with background
publication and text reuse active. Periodic cleanup is retained
(`skipPeriodic=false`). The imported client hash and inventory overlay match
the previous review. Startup confirms `requested=pipelined active=pipelined`,
one PBO, 3,686,400 pixel bytes and `latencyFrames=1`, in the existing GL context.

The recording starts at 00:33:12.128125409Z and ends automatically at
00:38:12.167939566Z, reason `duration`, elapsed 300,040 ms. The client report
repeats observations in retained history: deduplicate identical lines and sort
timestamped metrics before counting. There are 6,717 raw lines and 4,127 distinct
lines; untimestamped duplicate text is not a reliable occurrence count.

Include periodic frame/viewer bins only when both their preceding and current
endpoints are within the recording. This yields 59 complete producer bins
(295.70 seconds) and 59 viewer bins (295.26 seconds). FPS is total frames divided
by total elapsed time. Weight stage averages by their corresponding work,
capture, collected-frame or writer sample counts. Printed values are rounded.
Do not average per-window percentiles into an overall percentile. UI_TIMING
counts viewer bitmap updates; it does not directly measure physical scanout.

## Frame delivery

| Measurement | Full recording, complete bins |
| --- | ---: |
| Displayed FPS | 58.35 |
| Producer capture/submission FPS | 58.50 |
| Client work between swaps | 12.842 ms |
| Readback issue + collection, per captured frame | 0.625 ms |
| Readback issue | 0.263 ms |
| Readback collection | 0.363 ms |
| Game-thread publication/enqueue | 0.035 ms |
| Buffer acquisition wait, average / maximum | 0.0025 / 0.07 ms |
| Pacing elapsed time | 3.552 ms |
| Background writer time | 3.216 ms |
| Five-second displayed FPS range, rounded | 53.2-60.1 |
| Viewer skipped published frames | 43 |

Issue and collection have different sample denominators around pipeline
boundaries, so their rounded averages need not sum exactly to readbackMs.
Readback reports CPU elapsed time, not GPU execution or transfer bandwidth in
isolation. Background writer time overlaps client work and must not be added
to the serial frame budget. Pacing elapsed time can include a GC/scheduling
interruption; it is not exclusively intentional sleep.

Sampled GPU pending count stays at one; sampled publisher pending count never
exceeds one. These samples support bounded operation, not a claim that the
two-buffer publisher could never transiently have two outstanding frames.
Viewer skips are infrequent and do not show a growing queue. Viewer payload
allocation remains one, with 3,686,400 retained pixel bytes. Last published and
viewed sequence 20,968 agrees with the final closed frame count, supporting
successful final-frame drain.

For rough comparison with the previous shorter recording, common initial
210-second spans contain 41 complete bins each (about 205 measured seconds):

| Measurement | 0.10.51 background delivery, synchronous readback | 0.10.52 background delivery, pipelined readback |
| --- | ---: | ---: |
| Displayed FPS | 45.30 | 57.84 |
| Client work | 14.17 ms | 12.68 ms |
| Readback | 7.75 ms | 0.64 ms |
| Game-thread publication/enqueue | 0.043 ms | 0.035 ms |

The direction agrees with the user's experience, but versions, session state,
scene and inventory activity differ. Do not attribute an exact FPS percentage
to the pipeline from these recordings. Within the new run, 17 complete bins
in the final 90-second segment (about 85 measured seconds) average 59.51 displayed
FPS and 59.90 producer FPS. This is a settled segment, not the full-run average.
The architectural one-frame delay remains; logs do not measure input latency.

## Stutters and memory

| Viewer gap | Correlated evidence, UTC |
| --- | --- |
| 404.08 ms, bin ending 00:35:04.923 | Young GC finishes 00:34:59.821 in 96.161 ms, followed by full GC finishing 00:35:00.109 in 288.645 ms; combined 384.806 ms |
| 162.68 ms, bin ending 00:36:24.978 | Young GC at 00:36:24.467 takes 140.805 ms; overlapping producer window has 161.72 ms maximum client work |
| 132.68 ms, bin ending 00:33:24.844 | Overlapping producer window has 129.01 ms maximum client work; no corresponding logged GC |
| 119.95 ms, bin ending 00:33:49.868 | Overlapping producer window has 115.76 ms maximum client work and 24.73 ms maximum readback; no corresponding logged GC |
| 106.71 ms, bin ending 00:34:09.883 | Young GC at 00:34:07.631 takes 86.073 ms |

These are correlations between overlapping periodic windows, not per-frame
traces. Four young collections and one full collection occur inside the
recording, totaling 669.334 ms. The longest full collection is labeled
`Allocation Failure`, with heap 403 -> 403 MiB and committed heap expanding
to 929 MiB. That is allocation-triggered GC, not an OutOfMemoryError or the
periodic explicit cleanup. It also precedes the recording's measured inventory
render activity, so inventory rendering alone does not explain this pause.

The unclassified stalls need finer attribution before blaming a particular
client method, driver, storage operation or thermal throttling. Two explicit
full collections during final shutdown are outside gameplay and are excluded.

First / peak / last values from 61 client-recording memory samples:

| Measurement | First | Peak | Last |
| --- | ---: | ---: | ---: |
| Java heap used, MiB | 385.46 | 658.53 | 658.53 |
| Direct buffers, MiB | 156.51 | 363.82 | 208.75 |
| Client PSS, MiB | 1176.83 | 1682.65 | 1476.08 |
| Client RSS, MiB | 1217.73 | 1723.07 | 1516.81 |
| VmSwap, MiB | 0 | 0 | 0 |

Memory is substantial but demonstrably reclaimed. Around the 00:36:24 young
collection, direct buffers fall from 363.82 to 195.43 MiB and PSS from 1682.65
to 1452.39 MiB. The recording ends on a rising heap segment; a subsequent young
collection at 00:38:17.823 reduces 670 -> 413 MiB, and the next regular sample
reports 436.65 MiB used. Committed heap remains expanded; that is distinct from
live heap usage. These observations neither establish nor exclude a retained
leak over longer play. They do not justify increasing heap limits or forcing GC.

Java thread count stays at 33 during recording. File descriptors range 52-54,
ending at 53. Direct buffer count peaks at 67,690 and ends at 24,928, with sharp
reductions after collections. No growing frame queue or thread count is evident.

## GUI and text follow-up

All printed GUI rows sum to 127,760,528 allocated bytes (121.84 MiB) over 224,255
completed scopes, matching the scope totals. Omitted/unavailable counts and
printed row failures are zero; maximum distinct GUI pairs is 120 of 128.
These are cumulative Java allocations within instrumented scopes, not retained
memory or a complete account of all client allocation.

Inventory accounts for 40,569,784 bytes across 6,086 calls, averaging 6.51 KiB
per call. Its calls occur only in the last portion of the recording; the first
six complete GUI windows report none. Settled windows are about 6.67 KiB per
call. This differs from the prior workload's 4.4-4.6 KiB; without identical
inventory contents/visibility it does not prove a matcher regression. The
same verified inventory overlay remains active.

Other larger measured totals are WurmTabbedWindow (18.23 MiB), HealthBar
(17.48 MiB), and HeadsUpDisplay$2 (14.56 MiB). These provide concrete allocation
investigation targets, while the non-GUI allocation sources remain relevant.
Text reuse is 96.99% by sampled counter deltas, with peak pool capacity
199,440 bytes, maximum 129 entries and all sampled rejection counters zero.
The evidence does not point to text-pool tuning as the next fix.

## Lifecycle, warnings and next work

Inventory, compatibility, preparation and entry stages all exit 0. The client
closes normally at 00:38:36.292433Z after the logged quit/final-cleanup path.
No fatal GL/PBO mapping/readback/publication error, native crash or OOM is
present. Familiar Android linker and already-stopped OpenAL warnings remain.

Server save/stop is requested at 00:38:46.009189Z. Player, creature, zone, map
and ID saves finish; the server reports normal shutdown and exits 0 at
00:38:46.613114Z, `stopRequested=true`, `force=false`. The shutdown INFO
exception trace accompanies that normal sequence. Startup Epic mission
backup-map warnings occur for Fo, Magranon and Libila, alongside familiar
template/TimeSync warnings. These are separate server-state warnings; this
capture does not establish mission impact. No storage audit was completed.

Keep pipelined readback enabled on this Thor, with background publication and
the existing allocation improvements. Next work should investigate allocation
pressure/heap expansion behind the GC pauses and attribute the remaining
long client-work frames. Do not change the global default or claim constant
60 FPS from this single recording. No repeat of the unchanged off/on baseline
is required to begin that investigation. Longer normal play can establish
whether post-collection memory stabilizes; an exact pipeline FPS-effect claim
would still require a matched comparison.

## Source fingerprints

| Source | SHA-256 |
| --- | --- |
| Support ZIP | `c1e1df3b263e32a39eeb296bcdae4821586f6a7fe2228518d2c885711dba0d97` |
| client-report.txt | `1c499bd4afeb9af6116d451a14c16fd420fff403ded497921d35b3f2f0f42155` |
| server-report.txt | `efb92ad58dfa9e15392210dc7f83f110a00e6d2999b380915681ddb343ea6874` |
| session.txt | `db7d7c4a474b249fdb7491398d4448585351f28c922f96a817aa49e0028b797a` |
| storage-report.txt | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |
| Imported client.jar | `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19` |
