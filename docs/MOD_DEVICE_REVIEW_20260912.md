# 0.10.37 device review — Announcer enabled, then disabled

Reviewed 2026-09-12. Branch: `mod-launcher-test`. APK and runtime remain
0.10.37 (`v0.10.37-mod-hook-order`); this review makes no runtime changes.

Evidence: `wurm-server-report(20260912-191104).txt` and
`wurm-client-report(20260912-191104).txt`, supplied by the user after two
sessions. Times below are UTC. The client report embeds the server report;
observation history also repeats console records. Those copies are not separate
events. The retained first-warning section describes the second server session;
the console contains both sessions.

## Result

The first physical mod-loading and disable test passed. The earlier
`Communicator class is frozen` failure did not recur. Actual game entry and
normal server shutdown worked through the transforming loader. This establishes
Announcer loading and the off path, not arbitrary mod compatibility or complete
gameplay verification of Announcer's chat output.

| Evidence | Announcer enabled | Announcer disabled |
| --- | --- | --- |
| Server process | PID 19891; 19:05:07.309–19:08:22.671 | PID 21504; about 19:08:52.009–19:09:56.632 |
| Selection | `announcer@v0.47-73f7152` | `none; baseline startup` |
| Loader | `SERVER_MOD_READY announcer` and `SERVER_LOADER_READY count=1` at 19:05:07.854; Android entry at 19:05:07.857 | No mod-loader launch markers in this session |
| Client | PID 20313; login accepted and game loop observed; normal exit 0 at 19:08:01.971 | PID 21760; welcome back to Thor and game loop observed; normal exit 0 at 19:09:45.177 |
| Server shutdown | User-requested, exit 0 after 195,361 ms; no forced stop | User-requested, exit 0 after 64,623 ms; no forced stop |

Both server sessions log player/world/mesh saving, completed database closure,
and `The server shut down nicely`. The second login loads Thor's existing
skills/items and position; this supports character persistence across restart,
without proving every individual gameplay change was saved.

The first shutdown stack includes `ProxyServerHook` / `HookManager.invoke`,
providing device evidence that the installed shutdown hook executed. Its
`java.lang.Exception` is attached to an INFO shutdown diagnostic, followed by
successful saving; it is not an uncaught crash. An INFO intra-teleport trace
at 19:05:46 also did not terminate the server. Server `lost link` records align
with each client's normal exit, rather than indicating a separate client crash.

Announcer generated its `.config` from JAR defaults at 19:05:07.514. The final
manifest reports `enabled=false`, no pending transaction, and the config,
descriptor and JAR under `android-mods/disabled/announcer`. The original on/off
flow therefore retained the generated config. Re-enabling and reading it back
is still a separate device check.

The configured values are `announcePlayers=true`, `announcePlayerLogout=true`,
and `announceMaxPower=0`. The upstream Announcer source broadcasts login/logout
messages to the game; it does not log each broadcast to the server console.
These reports contain no captured announcement text. A normal player meeting
the configured power limit should be used for the visible announcement check.
The client disconnects about 21 and 11 seconds before server shutdown,
respectively, while the server reports a 60-second logout delay. These sessions
are therefore not a strong test of Announcer's logout notification.

## Remaining reliability and performance observations

- No fatal native crash, OOM, mod-loader failure or server SEVERE record was
  found in these two sessions. Both client graphics/audio contexts shut down
  and both entry processes returned 0.
- Client rendering/presentation settles near the 30 FPS target at 1280x720.
  Across all retained timing samples, including startup, the render-FPS medians
  are 30.0 (29 samples) and 29.3 (7 samples). Different durations and workloads
  prevent a controlled estimate of Announcer's overhead. The first session's
  final five render samples are all 30.0 FPS.
- Client graphics errors remain recoverable: the enabled run has nine
  `CLIENT_GL_ERROR before readback=0x502` exceptions between 19:05:49 and
  19:05:58, after which rendering continues. Both runs record startup
  `0x500` and `0x502` observations. Some first-run attribution names
  `gl4es_glBindFramebuffer:263`; as established in the earlier baseline review,
  that site reads an existing driver error and does not prove binding caused
  it. Driver candidate lists likewise do not identify a proven failing call.
  The reports do not establish that Announcer caused the graphics errors.
- Missing dirt/cobble normal textures and hair mappings appear in both runs.
  Twelve clothing-add warnings appear during the first character initialization.
  Duplicate eye/guard-tower templates and the login-server TimeSync warning
  occur with the mod both on and off. These are follow-up graphics/content or
  baseline warning items, not a newly demonstrated loader failure.
- Android ignores unused JRE `DT_RPATH` entries. OpenAL reports inability to
  obtain its requested thread scheduling priority but continues initialization.
  `OpenAL was already stopped` follows explicit audio teardown and precedes
  exit 0 in each run; it is redundant shutdown work/logging, not evidence of
  an audio crash. OpenAL INFO messages about absent optional configuration keys
  must not be counted as failures.
- First-client RSS levels near 1.56 GiB by the 60-second sample and remains
  around that level through 150 seconds; direct-buffer bytes level near 205 MiB.
  The final sampled heap is about 696 MiB of a 990 MiB maximum. No swap is
  recorded. Startup Serial-GC pauses are still visible (for example a 254 ms
  full collection at 19:05:45); explicit collections near exit are shutdown
  work and should not be counted as gameplay stalls.
- First-server heap rises from about 353 MiB at 30 seconds to 679 MiB at
  180 seconds, with no further recorded collection in that interval; its
  maximum is 4 GiB. File descriptors rise from 82 to 130. The disabled run
  also rises, from 79 to 88 descriptors between 30 and 60 seconds, with heap
  374 to 442 MiB. This short test does not establish a leak. Earlier long-run
  evidence showed descriptor counts falling around young collections. The
  current memory sampler closes both its proc readers and directory stream.
  Follow ownership/lifetime if counts continue growing after collections.
- TCP readiness polling retains the 15-second steady cadence: 22 and 12
  checks respectively, including faster startup checks. The second client's
  embedded server report and repeated memory/error-history sections enlarge
  exported reports; they must be deduplicated when reviewing trends.

## Next physical test

No new APK or speculative runtime change is required for the next check.

1. On 0.10.37, with the server stopped, re-enable the existing Announcer entry
   without reimporting it. Run the Mods file/dependency check, start the server,
   and confirm its retained configuration and readiness markers.
2. Confirm a login announcement in game. To test logout, leave the server
   running through the logout delay; another connected observer is the clearest
   way to see the broadcast. A missing self-login message alone is not proof
   of hook failure, because the reports do not capture chat display/delivery.
3. Play for about 15–20 minutes with Announcer alone, normally stop and restart,
   then export both reports from Diagnostics. This also extends the memory/FD
   observations beyond initial allocation and checks config retention.
4. After that pass, proceed to Announcer plus CropMod using the existing
   [two-mod checklist](MOD_LAUNCHER_TEST.md). Preserve the pre-mod world export;
   disabling a mod does not reverse world/database changes. CropMod's actual
   farming behavior requires observation beyond loader readiness.

Client mod execution remains deferred. Main is unchanged. No host tests were
rerun for this documentation-only review.
