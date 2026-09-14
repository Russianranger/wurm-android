# 0.10.44 extended periodic-cleanup test — 2026-09-14

The user reports more than 30 minutes with Skip periodic client cleanup on.
The reports capture approximately 37m39s of gameplay, three actual skipped
World.tick cleanup requests, normal client/server exits and successful server
save logging. The option works as designed at the three scheduled times.
Automatic allocation-triggered collections still cause substantial hitches;
this is not proof that skipping periodic GC improves total session latency.

## Evidence and scope

Exported together 2026-09-14T00:44:48.978350Z, version 0.10.44, package
`io.github.russianranger.wurmlauncher.sessionfix`, Android 13/API 33.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| client-report.txt | 1,897,999 | ef22afc0bbe2698cdf496a8036aec2b7bc71516e2c78c9028d669fc3ab3630f3 |
| server-report.txt | 380,475 | dd8d75ac1ceff1cd6221ada357d96ebef56121405995779b63d7139a0ee34713 |
| session.txt | 169 | 2507f9a4113acd9eba4877319b7c2e4f5995d36e12d593adfabf396d4dc621f2 |
| storage-report.txt | 31 | 86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8 |

The files also retain September 13 history. This review selects September 14,
client PID 23164, server PID 23022 and Android app PID 22869; exact duplicate
timestamped observations/console records are counted once. Older failures and
the earlier off/on attempts are not counted as new occurrences.

Client entry starts 00:06:39.407Z, effective skipPeriodic=true is recorded at
00:06:40.052Z, first game-loop observation is 00:06:47.929920Z, final cleanup
begins at 00:44:27Z, and stopped status is recorded at 00:44:28.947841Z.
All four client stages exit 0; final viewer sequence is 67,554. The Adventure
server starts at 00:06:34.614Z and exits 0 at 00:44:39.305832Z, elapsed
2,284,691 ms, with stopRequested=true and force=false.

## Skipped requests and remaining pauses

Each scheduled request logs WORLD_GC_REQUEST action=skipped caller=World.tick.
The following viewer window covers that timestamp:

| Request UTC | Viewer window ending UTC | Viewer FPS | Largest interval in window |
| --- | --- | ---: | ---: |
| 00:16:46.432 | 00:16:50.247 | 29.9 | 50.10 ms |
| 00:26:46.094 | 00:26:50.962 | 30.0 | 49.80 ms |
| 00:36:46.011 | 00:36:46.975 | 30.0 | 49.48 ms |

There is no corresponding System.gc collection during play. The prior same-build
off run had one World.tick request with a 326.718 ms collection and 350.93 ms
viewer gap. These three on events demonstrate that the targeted pauses are
avoided at their scheduled times. Shutdown still invokes System.gc twice,
taking 547.000 and 335.870 ms; retaining those calls is intended.

Ordinary collection remains active. During the observed game-loop interval
there are 17 client GC pauses totaling 4,182.996 ms. Five later full collections
triggered by allocation pressure align with the largest viewer gaps:

| GC completion UTC | GC pause | Corresponding viewer gap |
| --- | ---: | ---: |
| 00:20:59.709 | 586.641 ms | 614.60 ms |
| 00:25:16.378 | 674.457 ms | 682.46 ms |
| 00:33:51.494 | 576.078 ms | 578.40 ms |
| 00:38:04.483 | 571.455 ms | 580.46 ms |
| 00:42:15.620 | 570.626 ms | 598.48 ms |

In JVM GC terminology, Allocation Failure here identifies the trigger for a
successful collection, not an OutOfMemoryError. These collections reclaim heap
from 873–912 MiB to 574–608 MiB; the last three return to approximately
607–608 MiB. The pause is spent primarily collecting, rather than waiting to
reach a safepoint. No OOM, failed collection or abnormal child exit is present.

Workload, duration and device conditions differ from the earlier off run.
Skipping explicit GC can alter later allocation-pressure collection timing;
the reports do not establish that the switch caused all later pauses or that
it improves overall worst-case latency. Preserve the separate optional setting
and current collector/heap policy while investigating client allocations and
retained memory. Do not automatically switch the default on or enlarge the heap
based solely on the successful skip markers.

## Frame timing and memory

Select periodic viewer windows from 30 seconds after the first game-loop
observation until final cleanup, excluding loading and shutdown. There are 445
windows, mean viewer FPS 29.934, lowest window 26.5 FPS, median window interval
p95 35.99 ms and largest interval 682.46 ms. Producer timing has 444 windows,
mean presented/rendered FPS 29.936. Window summaries are not global frame
percentiles or GPU scanout measurements. Excluding the first two minutes instead
gives 427 windows and mean viewer FPS 29.939, with the same largest gap.

One 3,686,400-byte payload allocation is reused throughout. There is one skipped
published sequence in the selected windows, with no frame read/copy/readback
failure. Median window read p95 is 3.19 ms and copy p95 1.35 ms.

Client/app memory uses 71 samples after the first two minutes and before cleanup:

| Measurement | Observed range |
| --- | --- |
| Client PSS | 1,283.9–1,723.0 MiB, peak about 1.68 GiB |
| Client used heap | 571.7–902.1 MiB |
| Client direct buffers | 166.5–247.5 MiB |
| Client descriptors / process threads | 53–54 / 50 |
| Android app PSS | 94.6–121.5 MiB |

Client PSS is not completely flat: median rises from 1,489.4 MiB in minutes
5–10 to 1,706.5 MiB in the final five minutes. Corresponding direct-buffer
medians are 167.8 and 220.3 MiB, ending at 193.8 MiB after later collection.
The heap is reclaimed repeatedly, late post-collection heap levels are similar,
descriptors/threads are bounded, and neither JVM reports swap. There is no
observed runaway/OOM, but continued resident-memory growth merits investigation;
this run cannot exclude a slow leak or prove fully settled memory.

Server memory after the first five minutes has 66 samples: PSS 953.8–992.2 MiB,
used heap 268.7–799.9 MiB, direct buffers approximately 0.092 MiB, descriptors
72–153 and process threads 54. Descriptors and heap cycle down with collection.
Server PSS median is 966.5 MiB in minutes 5–10 and 972.0 MiB in the final five
minutes of client play. Android app and server show no comparable upward trend.

## Errors, saves and next work

The existing startup texture error occurs once in the new session at
00:06:48.858Z: driver-origin 0x502 with "unable to generate levels for texture
target 3553", followed by TextureLoader.uploadPreCompressedTexture reporting a
pending invalid operation during error cleanup. The precise texture/format/call
still needs tracing. No later CLIENT_GL_ERROR render-loop exception appears.
The old GPU-memory 0x500, Ogg decoding and invalid-sample-rate errors do not
recur; the capability guards and prepared sound fallback are logged.

Familiar nonfatal messages remain: linker DT_RPATH notices, OpenAL falling back
after denied real-time scheduling, and already-stopped audio during shutdown.
The server has the same six startup warnings (eye template, four guard-tower
duplicates, login-server time sync) and no actual SEVERE record. Its INFO-level
shutdown stack is the deliberate shutdown trace. No ASan fatal report, fatal
signal, OOM, database-open failure, malformed/locked database error or abnormal
exit is found.

At 00:44:38.904Z the launcher requests normal server shutdown. The server logs
player saves, item/creature batches, creatures, zones, surface/rock/cave/resource/
flags meshes, constants and ID saves, closes databases, and exits 0 without a
forced kill. The client saves its settings/player/window files and exits 0.
This corroborates normal saving and shutdown; no subsequent world-state
comparison or SQLite integrity audit is supplied. The storage report explicitly
says no audit has completed. No mid-play background/resume is demonstrated.

The requested 20–30-minute on test is now complete, including three actual skips
and server shutdown. Do not request another identical run merely to prove the
switch activates. Next technical work is the startup texture-level error and
client allocation/retention causing automatic full-GC hitches. Longer lifecycle/
persistence qualification, native instrumentation comparison and persistent
release signing remain separate roadmap items. This review changes documentation
only; no new APK, runtime policy, version or tag. Raw logs stay outside git.
