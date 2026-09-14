# 0.10.45 mipmap and allocation device review — 2026-09-14

## Evidence and method

Reviewed the support bundle exported at 2026-09-14T13:53:28.093434Z, reported
version 0.10.45, package `io.github.russianranger.wurmlauncher.mipmaptest`.
The source ZIP SHA-256 is
`e82ccaf3088c80b56134db8eb26c81ec41aa513592377080e000f3a119c33602`.

| Extracted report | Bytes | SHA-256 |
| --- | ---: | --- |
| client-report.txt | 1,052,814 | `c8dcf0e20e09d9f86a3d1d886e0b95d038fef2a6d14ee243e6dae7455ca899c8` |
| server-report.txt | 197,277 | `54e423760c89e7be3ff4357951f76c7dd51c3d3e327f0cd0c748c38a978a728e` |
| session.txt | 169 | `9c3ce9ded5b5658b1fe0c3b4c27e44d387df46bef03abfadcf4054e1f01258db` |
| storage-report.txt | 31 | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |

Correlated client PID 27193, server PID 27045 and Android app PID 24666.
Identical timestamped records repeated between retained observations and console
were counted once. Repeated untimestamped clothing warnings within the console
were counted individually. Raw reports, player identifiers, coordinates and
proprietary inputs are not published.

Client game-loop entry was 13:20:55.465956Z, with final cleanup beginning at
13:53:14Z: approximately **32 minutes 19 seconds**. Normal client logging and
skip-periodic-cleanup were enabled. Live Map was enabled; server mod selection
was none, with Survival disabled. The server created a new character. This is
not a matched-workload comparison with the earlier 0.10.44 run and does not
establish a version or switch performance advantage.

## Outcome

- No client native/Java fatal error, OOM, severe server record or failed database
  shutdown was found. All client stages exit 0. The final frame sequence is
  57,959, followed by client stop at 13:53:16.260471Z.
- Server starts at 13:20:43.491855Z and exits 0 at 13:53:21.144625Z after
  1,957,652 ms. Shutdown is requested, non-forced and not startup cancellation.
  Player/world save operations, completed database close and the normal
  shutdown message are present.
- No startup texture-level error, `[graphics-error]`, timestamped driver error,
  or `CLIENT_GL_ERROR` was found. The KHR_debug installation marker is present.
  This supplies positive device evidence for the 0.10.45 startup correction in
  this run. Optional non-debug driver reporting and lack of visual inspection
  prevent a claim that every texture path or the older intermittent in-play
  issue is fixed.
- The storage report says no audit was completed. Successful saves and clean
  exits do not independently verify backup restoration or a subsequent restart.

## Frame pacing and garbage collection

For 382 nonempty periodic viewer windows ending more than 30 seconds after
game-loop entry and before cleanup:

| Metric | Result |
| --- | ---: |
| Mean / median displayed FPS | 29.937 / 30.0 |
| Minimum displayed FPS | 26.4 |
| Median window interval p95 | 36.235 ms |
| Largest frame interval | 678.88 ms |
| Largest window read p95 / copy p95 | 2.35 / 2.91 ms |
| Skipped published sequences in these windows | 0 |
| Payload allocations / retained payload | 1 / 3,686,400 bytes |

These are viewer bitmap-update measurements, not GPU scanout. Startup and the
final pause record are excluded; two skipped sequences occur during startup.
Window percentile summaries are not a reconstructed whole-session percentile.

The three actual World.tick requests are skipped at 13:30:54, 13:40:54 and
13:50:54Z. Their following viewer windows have maxima of 36.83, 37.58 and
51.34 ms. No gameplay System.gc pause is recorded. The scheduled-skip behavior
is confirmed again; there is no need to repeat this test solely to prove that
the switch activates.

Automatic pressure collections remain. There are 18 gameplay GC pause records,
totalling 3,845.580 ms, including a 227.998 ms full collection during early
login. The four later full collections correlate with the largest viewer gaps:

| Full-GC end time (UTC) | Full pause | Heap after GC | Following viewer maximum |
| --- | ---: | ---: | ---: |
| 13:35:40.420 | 452.971 ms | 615 MiB | 655.96 ms |
| 13:39:09.370 | 570.098 ms | 590 MiB | 588.84 ms |
| 13:43:28.475 | 658.746 ms | 582 MiB | 678.88 ms |
| 13:51:58.768 | 592.484 ms | 614 MiB | 620.60 ms |

The first late full collection immediately follows a 177.720 ms young pause:
630.691 ms combined, consistent with that longer viewer interval. The last
three young attempts preceding full GC are approximately 0.35–0.63 ms.
Two explicit collections of 420.755 and 327.327 ms occur during requested
shutdown and are excluded from gameplay stalls.

## Allocation attribution: a useful new lead

The report contains 65 allocation observations. The initial sample is a baseline;
the next window covers only eight matched threads while 22 establish baselines.
Do not treat it as complete startup allocation coverage.

There are 59 complete windows whose starts are at least two minutes after
game-loop entry, totalling 1,770.069 seconds. Every one reports 30 matched
threads and zero reset/new, unavailable, departed or omitted threads.

| Contributor | Observed heap allocation rate | Share of observed bytes |
| --- | ---: | ---: |
| All matched live threads | 1.195 MiB/s | 100% |
| Job_executor_0 | 0.714 MiB/s | 59.76% |
| Wurm__main_thread | 0.226 MiB/s | 18.88% |

Both named contributors appear in every retained top-four list in those windows.
In the nine complete windows wholly within the final five minutes, total
allocation is 1.060 MiB/s, with Job_executor_0 at 0.705 MiB/s (66.50%) and the
game thread at 0.212 MiB/s (20.05%).

A separate burst in the 30-second window ending 13:33:18.675Z attributes
168.345 MiB to WOM_Model_Loader_2, out of 203.475 MiB observed for that window.
This is a loading lead as well as the sustained executor lead; it is not
evidence that those bytes remain retained.

These approximate counters measure new Java heap allocation on matched live
threads. They do not identify allocated classes, the tasks queued to an
executor, allocation stacks, retained objects, or native/direct/GPU memory.
Contributors absent from a top-four list cannot be treated as allocating zero.
Do not label Job_executor_0 as Live Map or any particular subsystem solely from
its thread name, and do not disable it or rewrite the frame producer on this
evidence.

## Memory

Ranges below use samples after five minutes from client game-loop entry and
before cleanup. PSS is proportional resident memory; values are MiB.

| Process | PSS range | Java heap used | Direct buffers |
| --- | ---: | ---: | ---: |
| Client | 1,337.4–1,754.2 | 465.6–916.0 | 163.3–243.2 |
| Android app | 151.3–184.3 | ART 56.9–88.7 | Not reported |
| Server | 943.8–1,006.8 | 266.4–794.4 | 0.093 |

Client PSS median rises from 1,360.8 MiB in minutes 5–10 to 1,686.3 MiB in the
final five minutes. Late full-GC heap results fluctuate around 582–615 MiB.
Heap commitment also grows during the run. These counters do not apportion the
PSS increase or prove a leak; they also do not establish settled memory.

Client descriptors remain 53–54, OS threads 49 and Java threads 30. Server OS
threads remain 54 and Java threads 29; descriptors fluctuate between 72 and
160. Both runtime processes report zero swap. Server PSS medians are nearly
unchanged across the same early/late windows (989.3 versus 989.7 MiB).
Android app PSS medians are 167.6 versus 180.8 MiB; native allocated memory
stays approximately 20.4–20.9 MiB. The principal unresolved memory trend remains
in the client.

## Login and spawn findings

At 13:21:04Z, the client emits **12 clothing-add warnings involving nine IDs**.
They form one login-time cluster; none recur later in the recorded session.
At the same time, a server warning reports a creature missing from the tile
being removed during spawn selection. Its stack passes through
SelectSpawnQuestion.answer, Creature.startTeleporting and Zone.deleteCreature.
Vision-area creation follows. A subsequent intrateleport diagnostic is logged
at INFO, not as an unhandled server crash.

The session continues normally for the remainder of the test. These records
should be tracked as appearance/spawn warnings, not declared harmless or a
proven asset defect without reproducing the visible behavior. The report alone
does not establish whether the two warnings share a cause.

User follow-up: the new character was created intentionally to test that path.
This is not evidence of a restore failure. The appearance/spawn warnings remain
observations to track.

The server explicitly records successful creation of a new character. If the
user intended a fresh character, this is expected. If an existing character
was expected, first verify the selected world, account/character and restored
backup. This bundle alone does not prove restore data loss.

Familiar duplicate item-template and login-server time-sync warnings remain.
OpenAL records scheduling/D-Bus fallback warnings and an already-stopped
message during cleanup; no recurrence of the earlier missing-sound decoding
failure was found.

## Next development target

1. Inspect and attribute the jobs executed by Job_executor_0 in the exact
   supported client before designing a narrowly scoped allocation reduction.
   Use bounded task/class evidence if the private code inspection does not
   identify the allocating work. Retain normal pressure and shutdown cleanup.
2. Investigate loading bursts and retained/direct/native memory separately.
   The thread counters identify churn; they cannot close the PSS concern.
3. Track the login clothing/spawn cluster and clarify expected character
   continuity if relevant. A visual appearance/location issue or repeat warning
   would supply a concrete reproduction target.

The texture startup correction and new allocation diagnostics now have evidence
from this 32-minute device run. Broad visual qualification, memory stability,
and restored-world restart remain open. No application code, APK, release tag,
heap/collector setting or default cleanup policy changes are part of this
review.
