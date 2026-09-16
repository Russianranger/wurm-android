# 0.10.50 device review: inventory allocation and frame budget

Reviewed 2026-09-16. Source: the user's `wurm-support(7).zip`, exported at
09:09:04Z after the requested 30 FPS and 60 FPS tests. This is an evidence review;
no production change or new APK accompanies it.

## Result

Both five-minute recordings completed automatically. Both client sessions exited
with entry code 0; the server saved and exited normally. No new fatal error,
out-of-memory failure or graphics error was found in the supplied reports.

The new GUI attribution identifies `InventoryWindow` and its rendering subtree
as the dominant measured GUI allocator: 85.3% at 30 FPS and 87.6% at 60 FPS.
The existing text-buffer reuse continues to work. The next allocation fix should
target inventory rendering, followed by the remaining frame/render/readback cost.
Neither the GUI allocation problem nor sustained 60 FPS is resolved yet.

## Evidence and calculation boundaries

- App: `0.10.50-managed-preview`, package
  `io.github.russianranger.wurmlauncher.guiframe`.
- Both clients: 1280 x 720, performance preset, LiveMap v1.8-9805b2a,
  GUI/job profiling and text reuse active; skip-periodic-cleanup false.
- 30 FPS recording: PID 26397, 08:55:57.890492489Z through
  09:00:57.930570187Z, 300,040 ms.
- 60 FPS recording: PID 27886, 09:02:00.227336048Z through
  09:07:00.267925569Z, 300,041 ms. The client restarted between tests.
- Deduplicate identical report lines: observations and console repeat events.
  Sum every GUI row in each recording, grouped by component across worker threads.
  Printed GUI allocation totals equal the scope totals; both recordings have
  zero omitted GUI samples, unavailable allocation samples and failed GUI calls.
  Maximum recorded thread/class pairs are 118 and 103, below the 128-pair bound.
- Frame averages use whole periodic bins inside each recording: 58/59 producer
  bins and 59/59 viewer bins. FPS is frames divided by elapsed time; stage times
  are weighted by samples, using workSamples for clientWorkMs. Exclude bins
  overlapping recording boundaries. Rounded printed timing fields limit precision.
- GUI allocations are cumulative Java heap allocation, including nested calls,
  not retained memory or direct-buffer bytes. Whole-job output prints only its
  top rows; its scope/completion boundaries also differ from GUI scopes. Do not
  subtract printed whole-GUI-job rows from component totals as an accounting test.
- The inventory render counts and timing differ between tests. Treat these as
  observations under two workloads, not a controlled FPS-only A/B result.
  The logs do not establish the exact times that windows were visible or closed.

## GUI allocation

| Measurement | 30 FPS target | 60 FPS target |
| --- | ---: | ---: |
| All measured GUI allocation | 306.16 MiB | 653.46 MiB |
| Inventory subtree allocation | 261.18 MiB | 572.57 MiB |
| Inventory share | 85.31% | 87.62% |
| Inventory render calls | 5,905 | 14,519 |
| Inventory allocation per call | 45.29 KiB | 40.38 KiB |
| Tabbed window allocation | 9.37 MiB | 13.88 MiB |
| Health bar allocation | 9.30 MiB | 14.55 MiB |
| Hover overlay allocation | 5.40 MiB | 18.77 MiB |

At 30 FPS, the first three full GUI windows contain roughly 4.5-4.7 MiB per
30 seconds and no inventory render rows. After inventory render rows appear,
allocation rises to roughly 44-46 MiB per 30 seconds, about 40 MiB of which is
inside inventory. At 60 FPS, inventory rendering is recorded throughout the test.
This identifies the subtree without relying on the cross-session comparison.

Private inspection of the exact imported client gives a concrete next candidate:
`WurmTreeList$TreeListPanel.renderComponent` constructs a numeric-alignment regex
and invokes `String.matches` inside its column-rendering loop. Repeated regex
construction/compilation is a plausible avoidable allocator. This call site is
confirmed; its individual contribution to the device's inventory total is not
yet measured. By contrast, inventory quality/damage/weight formatting is stored
by `updateStrings`; its mere presence does not establish per-frame formatting.
No proprietary bytecode, decompiled source or user logs are committed here.

The inventory subtree's accumulated elapsed time is about 2.09/4.18 seconds over
each five-minute recording (about 0.35/0.29 ms per call). A large allocation share
does not mean this subtree accounts for the full client frame-time deficit.

Text reuse remains effective: first-to-last sampled counter deltas give 97.78%
and 98.43% reuse, with all sampled rejection counters zero. Successful layout
returns increase by 811,090 and 1,634,999. Sampled pool capacity peaks at 266,160
and 332,160 bytes. Do not replace or expand the working text pool to address this
separate inventory allocation finding.

## Frame budget

| Measurement | 30 FPS target | 60 FPS target |
| --- | ---: | ---: |
| Producer average FPS | 29.76 | 48.42 |
| Viewer average FPS | 29.70 | 48.42 |
| Viewer five-second FPS range | 26.1-30.2 | 37.2-58.8 |
| Client work between swaps | 13.52 ms | 12.01 ms |
| Deliberate pacing | 10.55 ms | 0.016 ms |
| Readback | 6.33 ms | 5.67 ms |
| Publication | 3.15 ms | 2.91 ms |
| Capture setup + restore + EGL swap | 0.060 ms | 0.048 ms |
| Longest viewer frame interval | 388.05 ms | 207.71 ms |
| Skipped published frames in included bins | 1 | 0 |

At 60 FPS the measured stages total about 20.66 ms, above the 16.67 ms budget.
Pacing is negligible and the viewer keeps up with the producer. The bottleneck
is upstream of the viewer: client work, readback and publication. Client work is
wall time and can include waiting; readback includes pending GPU work and copying.
These counters do not separately measure GPU execution or prove a thermal cause.

The 60 FPS run varies rather than slowing monotonically: the approximate first
and last minute viewer averages are 45.17 and 44.57 FPS, with up to 58.8 FPS in
between. Publication remains material at roughly 3 ms. The prior host publisher
allocation reduction is not an isolated on-device CPU/FPS improvement measurement.

## Pauses and memory

The 30 FPS recording contains six young collections and one full collection,
957.447 ms total recorded pause time. The 288.766 ms full collection follows a
69.681 ms young collection near 08:57:43Z; the 388.05 ms viewer gap is reported
at that time. The 60 FPS recording has four young collections, 413.122 ms total,
and no full collection. Its 181.864 ms young collection near 09:02:21Z aligns
with the 207.71 ms maximum viewer interval. These events explain brief stalls,
but do not account for the sustained difference between 48 FPS and 60 FPS.

MiB below are first / peak / last five-second recording samples, not necessarily
the exact start/end instants or simultaneous peaks across columns.

| Memory measurement | 30 FPS target | 60 FPS target |
| --- | ---: | ---: |
| Java heap used | 400.51 / 810.61 / 679.77 | 667.43 / 831.48 / 701.39 |
| Direct buffers | 163.85 / 327.68 / 232.61 | 183.55 / 318.77 / 227.89 |
| Client proportional resident memory (PSS) | 1106.40 / 1691.19 / 1449.52 | 1393.17 / 1863.74 / 1769.76 |
| Client VmSwap | 0 / 367.87 / 297.88 | 0 / 0 / 0 |

Direct-buffer use falls after collections, for example 327.68 to 186.19 MiB
across the two samples following the 30 FPS full collection, and 314.92 to
227.68 MiB around the final 60 FPS collection. This supports reclaimable churn;
five minutes with startup settling cannot establish or exclude a retained leak.
Resident memory remains substantial, and the first run reports swapped pages.
VmSwap alone does not prove disk I/O or the cause of a particular frame stall.
Java thread counts remain 32/31; file descriptors stay in the narrow ranges
52-53/53-55. Viewer payload allocation remains at one per session.

## Errors and next implementation

No failed jobs are present in the printed job rows. The Android linker DT_RPATH,
duplicate server template/login-server TimeSync and stopped-OpenAL warnings are
the previously understood startup/shutdown messages. The server's INFO shutdown
exception trace accompanies its requested shutdown; saves and database closes
finish, followed by exit 0 at 09:08:52.945033Z, force=false. A storage audit was
not run, so this bundle does not independently qualify storage integrity.

Next: measure the confirmed tree-list numeric alignment call site locally and
implement a narrow, reversible, hash-pinned optimization if the allocation
measurement supports it. Preserve numeric/locale and alignment behavior, bound
any reusable state, and avoid retaining item strings or GUI objects. Verify
dispatch, exception behavior and exact patch reversal; then compare the same
inventory rows on device, including closed/reopened, scrolled and changed items.
There is enough evidence to proceed without another unchanged diagnostic run.
Afterward, address the frame path with the existing stage counters and pixel/
publication correctness tests. Do not promise that the inventory change alone
will deliver 60 FPS.

## Source fingerprints

| Source | SHA-256 |
| --- | --- |
| Support ZIP | `b72dcd393ace8dfa60a804b27a730ff9495d96d8f7afd2e221d7dfc33cfbc199` |
| client-report.txt | `6da00b22a33556e7565d65dccf67bf0f1d16e541e4774e2b23d79e5af3192059` |
| server-report.txt | `5bcafa878bc4c7bd62cba6eae63b6f9ab22e23b5f634e0cb6ffa4b25c8895ad5` |
| session.txt | `92c837cc1feaeb8d4b5bba7ad007b03c6e7bda87ee66246a7512092bdbf8fdc7` |
| storage-report.txt | `86992fc351da4e58ec4ccca1f792576bfcb35dc62489751db5401a74e174feb8` |
| Imported client.jar | `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19` |

APK provenance and the original test procedure remain in
[CLIENT_GUI_FRAME_TEST.md](CLIENT_GUI_FRAME_TEST.md) and [HANDOFF.md](HANDOFF.md).
