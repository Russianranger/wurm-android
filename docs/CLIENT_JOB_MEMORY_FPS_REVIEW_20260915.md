# 0.10.46 job memory and FPS device review — 2026-09-15

## Evidence and method

Reviewed the support bundle exported at 2026-09-15T00:15:03.768146Z, version
0.10.46, package `io.github.russianranger.wurmlauncher.jobprofiletest`.
The user reports that FPS selection worked and the memory recording was made
in the 30 FPS session. Source ZIP SHA-256:
`35a8b7f1befa3ea4eacfbb40a09cbb9f173e7b06dd43036558680a199c43b4eb`.

| Report | Bytes | SHA-256 |
| --- | ---: | --- |
| client-report.txt | 870,740 | `008b132538a2e35b767fa1bd4f1f76a310189e8b828127f61fa605d644435438` |
| server-report.txt | 136,114 | `bed37b6def84cef4e31e953dad4d1ea6ddc5107407bffb424a38c48fd040418e` |
| session.txt | 173 | `20fe378bee1f5df0cd0f858774f759f221842ddd4ef9ce1dc94227a66b552a4e` |
| storage-report.txt | 31 | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |

Identical timestamped observations duplicated in the retained history and
console were counted once. Client PIDs are 8798 and 10380; server PID 8583 and
Android app PID 6082 span both attempts. The supported imported client hash
matches the 0.10.46 fixture. Live Map is enabled; server mod selection is none.
Raw reports, player identifiers, private paths and proprietary bytes are not
published.

Both launches explicitly report `skipPeriodic=false`. They are shorter than
the ten-minute periodic request interval, and no in-play System.gc collection
is recorded. This is not another qualification of the skip switch or a matched
comparison against the earlier 32-minute on-run. The first launch enables job
profiling; the second does not. Both use normal client logging.

## Outcome and frame targets

Both client attempts complete all bootstrap stages with exit 0. The same server
exits 0 after 555,558 ms, with requested, non-forced shutdown. Player/world saves
and completed database close are present. No unhandled Java exception, native
crash, OOM, ASan error, actual server SEVERE record, frame read/copy error or
client GL error was found. KHR_debug installs; the earlier startup mipmap error
does not recur. The prior clothing/spawn warning cluster does not recur either.

Familiar ignored Android linker DT_RPATH entries, OpenAL scheduling/D-Bus
fallbacks and already-stopped cleanup messages remain. Server duplicate item
templates and login-server time-sync warnings remain. The shutdown exception
is an INFO diagnostic associated with the requested shutdown, not an unhandled
failure. Storage audit was not run; clean saves/exits alone do not verify a
later restore or long-term stability.

Times below run from the app's game-loop observation to the console's
second-resolution final-cleanup marker. FPS statistics use nonempty periodic
viewer windows ending over 30 seconds after that observation and before
cleanup; startup and shutdown windows are excluded.

| Target | Client PID | Observed play | Viewer windows | Mean / median displayed FPS | Largest interval |
| --- | ---: | --- | ---: | ---: | ---: |
| 30 | 8798 | 00:05:56–00:12:44 UTC, about 6m48s | 76 | 29.855 / 30.0 | 412.42 ms |
| 60 | 10380 | 00:13:38–00:14:45 UTC, about 1m07s | 7 | 56.171 / 56.6 | 74.26 ms |

The 30 FPS windows range from 26.8 to 30.2; the seven later 60 FPS windows range
from 54.6 to 57.4. Matching producer means are 29.845 and 56.114 FPS. The target
acceptance records are 30 and 60, with no retained 40/50 acceptance or timing
windows. The user's successful control test is acknowledged; this bundle
independently quantifies 30 and 60 only. It does not demonstrate sustained
60 FPS, long-run temperature/battery behavior, or a matched profiling-overhead
comparison. Viewer measurements describe bitmap updates, not GPU scanout.

Each viewer epoch retains one 3,686,400-byte payload allocation. There are zero
skipped published sequences in the selected 30 FPS windows and one in the
selected 60 FPS windows. These counters show bounded viewer payload reuse;
they do not cover every client/native allocation.

## Recording and the allocating jobs

One recording begins at 00:06:21.590820Z and ends automatically at
00:11:21.635044Z, `reason=duration`, elapsed 300,044 ms. It contains 61 memory
samples, 298,025 completed-job observations, 236,117,440 observed heap bytes
(225.179 MiB), and zero unavailable allocation counters. Aggregate job
allocation is approximately 0.750 MiB/s.

The bounded 128-pair table fills. Across the recording, 7,031 calls (2.359%)
have no per-pair attribution because of the cap. Source inspection confirms
their bytes and completed counts still contribute to JOBS totals. The 132
printed top-pair rows cover 89.795% of all observed bytes; the remainder includes
unprinted pairs and capped attribution. Printed rows have zero failures.
Do not infer zero failures or zero bytes for unprinted work. The final empty
JOBS row follows the last nonempty snapshot and is not a lost recording.

| Job class | Bytes in printed rows | Share of all observed job bytes |
| --- | ---: | ---: |
| `com.wurmonline.client.renderer.gui.Renderer` | 174.937 MiB | at least 77.688% |
| `com.wurmonline.client.renderer.model.collada.ColladaAnimationJob` | 20.096 MiB | at least 8.925% |
| `CellRenderer$StructureRenderer` | 3.059 MiB | at least 1.359% |
| `CellRenderer$DecorationRenderer` | 2.453 MiB | at least 1.089% |

GUI Renderer accounts for 8,785 printed calls, averaging 20,880 heap bytes
(20.391 KiB) per listed call. Of its recorded bytes, 124,820,504 bytes
(119.038 MiB) occur on Job_executor_0 across 5,924 calls. This identifies the
GUI rendering job and its synchronous callees as a concrete allocation target;
it does not identify the allocated object classes or prove a leak. The worker
name still describes where the work ran, not a scheduler defect or Live Map
attribution. Do not disable or reschedule the worker on this evidence.

Separate whole-thread telemetry supplies nine complete 30-second windows
wholly inside the recording (00:06:48–00:11:18 UTC). All have 32 matched threads,
zero resets/new/departed/unavailable/omitted threads, and together observe
461,213,272 heap bytes over 270.002 seconds: 1.629 MiB/s. Wurm's main thread
appears in every top-four list at 0.514 MiB/s (31.525%); Job_executor_0 appears
in every list at 0.450 MiB/s (27.653%). Sound and model loaders also contribute.
These windows do not exactly match job windows. The 77.688% figure is a share
of completed-job bytes, not of total client allocation, retained heap or PSS.

## Memory and collection pauses

All values in this table are MiB. PSS is proportional resident memory; direct
buffer usage is separately reported by the JVM and is not additional to PSS.

| Recording sample | Client PSS | Java heap used | Heap committed | Direct buffers |
| --- | ---: | ---: | ---: | ---: |
| First, 00:06:21.595 UTC | 1,200.9 | 437.5 | 542.8 | 158.6 |
| PSS peak, 00:10:18.586 UTC | 2,323.0 | 816.6 | 989.7 | 489.3 |
| Last, 00:11:21.590 UTC | 1,921.5 | 696.8 | 989.7 | 278.3 |

The peak is about 2.269 GiB PSS; the last recording sample is about 1.876 GiB.
At 00:10:21 a natural young collection is followed by lower direct usage and
PSS: the 00:10:23 sample reports 241.8 MiB direct and 1,920.8 MiB PSS. Some
memory is reclaimed, but usage has not returned to its early-session level.
Heap commitment grows substantially during warm-up. These counters cannot
apportion resident growth across heap, direct buffers, GL/driver allocations,
native allocator/ASan state or resource caches.

Direct-buffer pool count grows from 17,598 at recording start to 55,921 at its
end, and reaches 78,228 in the later 00:12:18 ordinary sample. Bytes do not grow
proportionally: that later sample has 279.646 MiB direct usage. Investigate both
buffer count/lifetime and bytes; a count increase alone is not a leak verdict.

The recorder's Java thread disappears after expiry: 32 becomes 31, with OS
threads returning to 49. Client descriptors remain in the 53–55 range and
swap remains zero. Server Java/OS thread counts remain 29/55 after warm-up;
its direct usage stays about 0.092–0.115 MiB. Android app PSS samples after
recording start range about 153–189 MiB. No growing descriptor/thread trend or
OOM is demonstrated, but this short test cannot exclude memory leaks.

The principal 30 FPS hitches correlate with natural collections:

| GC end (UTC) | Collection pause | Following viewer maximum |
| --- | ---: | ---: |
| 00:06:38.698 | 101.317 ms young + 282.926 ms full | 412.42 ms |
| 00:07:39.792 | 223.401 ms young | 245.75 ms |
| 00:10:21.282 | 172.488 ms young | 184.15 ms |

The full collection happens early and leaves 428 MiB used while heap capacity
grows to about 989 MiB. No later full collection occurs until client shutdown.
The shutdown's two explicit collections (550.279 and 425.315 ms) follow final
cleanup and are excluded from gameplay hitches. Their heap results are not a
comparable steady-state in-play retention baseline. The five-minute recording
therefore does not close the prior longer-run memory concern.

## Next development target

1. Inspect the exact supported GUI Renderer's execute path and its callees for
   repeated temporary allocations; measure the suspected allocation site
   before preparing a narrowly scoped reduction. Preserve GUI output, input,
   callback/exception semantics and executor scheduling. Another identical
   job-attribution recording is not needed before this inspection.
2. Investigate direct-buffer ownership, lifetime and resource loading
   separately. The GUI job result cannot establish the source of direct/PSS
   growth. Compare repeated routes after warm-up and natural collections when
   validating a concrete change, keeping FPS, world, mods and cleanup policy
   matched and recording overhead explicit.
3. Keep high-FPS qualification separate from memory attribution. The short
   60-target session works at roughly 56 displayed FPS in its later windows;
   40/50 telemetry and sustained thermal behavior remain unmeasured here.

This review changes documentation only. Version 0.10.46, its APK/release,
runtime settings and the main branch remain unchanged. No memory cure or
profiling performance advantage is claimed.
