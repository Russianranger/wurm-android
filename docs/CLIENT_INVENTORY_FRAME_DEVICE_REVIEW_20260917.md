# 0.10.51 device review: inventory reuse and background delivery

Reviewed 2026-09-17 from `wurm-support(8).zip`, exported at 01:10:06.713681Z.
This review changes documentation only; the published APK/tag remain unchanged.

## Result

The inventory allocation reduction is now observed on the Thor: 4.44/4.64 KiB
per measured inventory render in the two recordings, compared with roughly
40-45 KiB in support7. Background delivery reduces game-thread publication from
about 2.95 ms to 0.043 ms, with negligible buffer wait. Equal initial recording
spans show about 39.3 FPS off and 45.3 FPS on. Retain both improvements.

The FPS comparison is encouraging, not a controlled estimate of the feature's
entire effect: inventory rendering activity and client-work times differ. The
remaining serial frame budget is about 22 ms, above the 16.67 ms needed for 60 FPS.
No new client crash, out-of-memory failure, readback error or publication failure
was found. All four client attempts and the server finish with exit code 0.

## Sessions and evidence boundaries

App: `0.10.51-managed-preview`, `.inventoryframes`, 1280 x 720, performance preset,
text reuse active, periodic cleanup retained (`skipPeriodic=false`). The exact
imported client hash matches support7. All four imports select the new inventory
panel overlay, SHA-256
`def3d03ac4a99608d69d7584ab7250a4f5d1c35c7cb16e3f91a3ce66bc4ce669`.

| Client PID | FPS target / delivery | Evidence |
| --- | --- | --- |
| 29004 | 30 / background | Short preliminary run; normal exit 00:54:19.952124Z |
| 29698 | 60 / synchronous | Short preliminary run; normal exit 00:56:43.558986Z |
| 30196 | 60 / synchronous | Recording 00:57:44.730161219Z to 01:02:44.763399333Z; automatic END after 300,033 ms; normal client exit 01:04:59.662133Z |
| 31358 | 60 / background | Recording starts 01:05:56.601527229Z; normal client exit 01:09:37.936487Z, about 221.335 seconds later; no recording END marker before exit |

The second capture is incomplete as a five-minute recording. Its last GUI
snapshot ends at 211.519 seconds, and its final memory sample is at 216.532 seconds.
Do not assume that its unreported final partial GUI window contains zero bytes.
Two explicit full collections at shutdown are not gameplay pauses.

Identical report lines are deduplicated because observations and retained console
history repeat events. Timestamped records are sorted and associated by PID.
Frame/viewer averages include only complete periodic bins within the selected
interval; exclude the viewer's final pause report. FPS is frames divided by
elapsed time. Stage times are sample-weighted, with workSamples for client work
and writerSamples for completed publication. GUI bytes are cumulative Java heap
allocation inside measured component subtrees, not retained or native memory.

## Inventory and text

| Measurement | Delivery off | Delivery on |
| --- | ---: | ---: |
| Inventory render calls in reported GUI windows | 11,610 | 3,324 |
| Inventory allocation per call | 4.44 KiB | 4.64 KiB |
| Inventory cumulative allocation | 50.31 MiB | 15.07 MiB |
| All reported GUI cumulative allocation | 111.78 MiB | 67.48 MiB |
| Text reuse, first-to-last sampled counter deltas | 97.82% | 96.56% |
| Text pool sampled capacity peak | 239,280 bytes | 207,120 bytes |

In settled inventory windows, both runs converge on 4,720 bytes (4.609375 KiB)
per call. This supports the matcher fix working independently of delivery mode.
The earlier support7 averages were 45.29 KiB at 30 FPS and 40.38 KiB at 60 FPS;
observed per-render cost is now roughly one tenth of those earlier averages.
Content/visibility differs, so this is not an exact equal-content savings estimate.

The off recording has inventory render rows throughout all five minutes. The on
recording has no inventory render calls in several complete 30-second windows.
These are measured rendering scopes, not explicit user-action/visibility events.
Do not infer that the user ignored the instructions, or compare their cumulative
inventory totals as a delivery-mode allocation reduction.

GUI scope sums exactly match listed rows. Both runs have zero GUI/job unavailable
or omitted samples and zero failures in printed rows. Maximum GUI thread/class
pairs are 98, below the 128-pair bound. All sampled text rejection counters are
zero. Successful layout returns increase by 1,044,856 / 554,965. The working text
pool is not the next intervention target.

## Frame delivery and remaining budget

For equal coverage, use the first 210 seconds of each recording. Each contains
41 complete producer bins and 41 complete viewer bins, about 205 seconds of
measurements after excluding bins crossing the interval boundaries.

| Weighted measurement, common initial spans | Delivery off | Delivery on |
| --- | ---: | ---: |
| Displayed FPS | 39.33 | 45.30 |
| Producer capture/submission FPS | 39.36 | 45.34 |
| Client work between swaps | 15.00 ms | 14.17 ms |
| GPU readback, including pending rendering | 7.40 ms | 7.75 ms |
| Game-thread publication/enqueue | 2.954 ms | 0.043 ms |
| Buffer acquisition wait | 0.000 ms | 0.006 ms |
| Actual writer time | 2.95 ms | 3.41 ms |
| Sampled maximum outstanding frames | 0 | 1 |
| Viewer skipped published frames | 0 | 8 |

Observed displayed FPS is 15.2% higher in these spans. Since client work and
inventory activity also differ, do not attribute all of that percentage to the
publisher. The publication mechanism itself behaves as intended: writing overlaps
rendering, with average buffer wait under 0.01 ms and maximum wait 0.18 ms. The
0-2 buffer bound is respected in reported samples; the sampled maximum of one
is not proof that a transient queue of two never occurred. Eight viewer skips
are infrequent and do not represent a growing backlog; the viewer continues to
track capture/submission rate. Viewer payload allocation remains one per session.

Across all complete recording bins, displayed FPS is 38.66 off / 45.30 on, covering
unequal durations (59 / 43 bins). Five-second ranges are 36.4-52.2 / 42.7-53.6 FPS.
At relative seconds 90-120, where both have continuous inventory render activity,
complete viewer bins average 37.37 / 44.59 FPS; camera/scene and other UI activity
are still not established as identical.

With background delivery on, the serial stages total about 22.04 ms; writerMs
must not be added because it overlaps those stages. Client work (14.17 ms) and
readback (7.75 ms) dominate. Readback remains synchronous and includes pending GPU
rendering, so the log does not isolate transfer bandwidth from GPU work. There
are no temperature or clock samples to justify a thermal explanation.

The new recordings have higher client-work/readback costs than support7's 60 FPS
recording (12.01 / 5.67 ms). Thus 45.3 FPS here versus 48.4 FPS in support7 does not
by itself prove a version regression or improvement. Preserve this limitation
when assessing the new build.

## Memory and pauses

First / peak / last sampled values, in MiB:

| Measurement | Delivery off | Delivery on |
| --- | ---: | ---: |
| Java heap used | 459.35 / 549.82 / 416.75 | 429.81 / 566.78 / 493.70 |
| Direct buffers | 209.48 / 244.22 / 193.85 | 160.37 / 249.52 / 249.52 |
| Client PSS | 1345.49 / 1419.76 / 1318.70 | 1232.23 / 1399.08 / 1397.84 |
| VmSwap | 0 / 0 / 0 | 0 / 0 / 0 |

Recorded heap and PSS peaks are lower than the prior support7 60 FPS test, but
workload, duration and starting state differ. The off run's live heap after young
collections progresses 388 -> 392 -> 395 -> 395 MiB. The on run's young collection
reduces heap from 566 to 387 MiB; memory later rises again. Direct use in the on
run rises toward its last sample, including a late increase. These short captures
neither establish nor exclude retained growth; no out-of-memory event is present.

During the complete off recording there are four young collections, 260.936 ms
combined, longest 72.654 ms; no full collection. The on recording has one gameplay
young collection, 80.599 ms. The two later System.gc full collections (324.255 and
261.483 ms) occur during logged final cleanup near 01:09:36-37Z, after gameplay.
Maximum included viewer intervals are 103.82 / 109.02 ms; the young-collection
windows account for the largest gameplay gaps. Do not count shutdown cleanup as
an asynchronous-publisher freeze.

Java thread counts stay at 31 off / 32 on, consistent with the additional writer.
File descriptors remain 53-54 / 52-54. No unbounded queue or thread proliferation
is indicated by these samples.

## Server warnings and next work

Server shutdown is requested at 01:09:47.309622Z. Saves/database closes finish,
then SERVER_EXIT=0 at 01:09:47.912302Z (about 17m16s uptime), force=false. The
INFO shutdown exception trace accompanies this normal save/stop sequence.

In addition to the familiar duplicate-template, login-server TimeSync, Android
linker and stopped-OpenAL messages, startup contains two EpicServerStatus warnings:
missing mission difficulty backup-map data for Magranon and Libila. The server
continues and creates mission/trigger records. These are server mission-state
warnings, not frame-delivery errors. This bundle does not establish their gameplay
impact or prove mission correctness; retain them as a separate follow-up if Epic
missions misbehave. No storage audit was completed, so normal shutdown does not
independently qualify all stored data.

Keep inventory matcher reuse and background delivery enabled. Next performance
work should isolate the remaining game-thread rendering/work and GPU/readback
cost before changing GPU synchronization. Further text-pool tuning is not supported
by these counters. There is enough evidence to retain the improvements without
another unchanged diagnostic rerun; a stronger FPS-only claim would need a matched
workload/full-duration comparison. Visual correctness still depends on the user's
observations; logs alone cannot prove pixel appearance or input feel.

## Source fingerprints

| Source | SHA-256 |
| --- | --- |
| Support ZIP | `be4fa2c3465cefd4786da31174767df9254eece0500264645d0b4a98b794a273` |
| client-report.txt | `9c5148287d7d417f4d0b0dac5b34babed295b4161f911f306d31d5e00c966887` |
| server-report.txt | `95c05288f994923c04e11f5514c328c5d6f035a80c80c27dee3a0be08182ee4e` |
| session.txt | `790d30e7a70fbf29f84f95fc3783755e8edb660b06dd10038aacdf3bf3639438` |
| storage-report.txt | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |
| Imported client.jar | `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19` |
