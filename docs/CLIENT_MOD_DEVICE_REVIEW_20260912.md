# 0.10.38 — first Live Map device review

Reviewed the supplied client/server reports and visible screenshot on 2026-09-12.
This is a software reliability/performance review. No runtime changes or new APK
are needed for the next test on the evidence available. Continue only on
`mod-launcher-test`; the release remains `ed0267310d247a45e42459accd6674256857044b`.

## Confirmed outcome

Live Map 1.8 initializes successfully in both client sessions. Each has
`CLIENT_MOD_READY livemap` and `CLIENT_LOADER_READY count=1`, followed by Android
graphics/audio initialization and the game loop. The screenshot establishes
visible map rendering with its terrain, window and buttons on the Thor. No
loader failure, missing mod class, frozen-class error, fatal native crash,
ASan memory-error report, OOM or actual server SEVERE record was found.

Both clients exit 0. Both servers explicitly save after a normal stop request
and exit 0, with `force=false`. The second client receives the welcome-back
message for Thor. The active server mod in these reports is **Survival only**;
Announcer is not in this app's server manifest. `survival@unknown` is missing
version metadata, not a loading failure. Neither manifest has a pending move
transaction. Both client attempts have Live Map on; loader-only and disabled
client-mod tests are not present in these reports.

## Correlated timeline (UTC, September 12)

| Session | Server | Client |
| --- | --- | --- |
| First | Survival ready 23:27:39; normal save/exit 23:35:13; runtime 7m35s | Live Map ready 23:28:34; game loop 23:28:41; normal exit 23:34:16; approximately 5m42s from loader entry |
| Second | Survival ready 23:36:29; normal save/exit 23:38:25; runtime 1m57s | Live Map ready 23:36:49; game loop 23:36:58; normal exit 23:38:16; approximately 1m28s from loader entry |

The client report embeds a server snapshot and repeats observations in console
history. Counts below use distinct timestamps/PIDs, not repeated report sections.
The screenshot's device time 18:29 corresponds to the first session's 23:29 UTC.

## Errors and warnings still present

- **Graphics:** seven distinct native GL error observations: five in the first
  client, two in the second. Codes are `0x500` / invalid enum and `0x502` /
  invalid operation. Three first-session observations cause recoverable
  `CLIENT_GL_ERROR before readback` exceptions at 23:28:58.834, 23:29:00.952
  and 23:29:01.085. These skip that capture attempt; later frames continue.
  No `FRAME_READBACK_ERROR` was found. No further native errors are logged
  after 23:29:01 in the first session or 23:36:57.922 in the second.
- The framebuffer error collection point and recent driver-call candidates
  do not prove which operation originally caused the error. Similar frame
  exceptions, GL codes and clothing warnings existed before client mods in
  [the earlier mod review](MOD_DEVICE_REVIEW_20260912.md). This run does not
  establish Live Map as their cause. Preserve checks and investigate exact
  pending/error-after-call attribution if they begin recurring during play.
- **Spawn selection:** at 23:28:51.710 the server reports that the tile where
  Thor should be did not contain him. Its stack runs through
  `Zone.deleteCreature`, `Creature.startTeleporting` and
  `SelectSpawnQuestion.answer`. It then creates a vision area and continues.
  This is one warning with a diagnostic exception, not an uncaught server
  failure. It does not recur in the retained second session. Check spawn
  position, movement/interactions and persistence; investigate if reproducible.
  There is no demonstrated causal connection to Live Map or Survival.
- **Content/world:** twelve clothing-add warnings occur during first character
  initialization, along with missing hair mappings and unsupported normal-matrix
  notices. Duplicate eye/guard-tower templates and the login-server TimeSync
  role warning persist. Three first-start Epic mission warnings report an empty
  backup map when obtaining mission difficulty (Fo, Magranon and Libila);
  mission creation continues. These may affect appearance or that mission
  subsystem, but do not establish general world corruption or a mod-loader
  failure. Watch them if testing those features.
- **Platform/audio:** ignored JRE `DT_RPATH` entries, unavailable optional
  OpenGL/OpenAL entry points, and denied audio scheduling priority remain.
  Both sessions create and free OpenAL contexts and close EGL. The deliberately
  absent capability fixture is followed by `CAPABILITY_LOOKUP_PASS`; it is
  an intentional check. `OpenAL was already stopped` follows teardown and is
  redundant cleanup logging. INFO-level server shutdown stack traces occur
  after explicit stop requests and are not crashes.

## Performance and resource observations

- Both sessions have median rendered, presented and displayed rates of **30.0
  FPS** at 1280x720. There are 67/16 renderer timing samples and 68/17 viewer
  samples respectively. Startup includes much lower rates; these medians are
  not a minimum-FPS guarantee or a controlled estimate of Live Map overhead.
- First-client sampled heap peaks at **814 MiB**, then ends at **682 MiB** of
  approximately 990 MiB maximum. Direct-buffer bytes peak at 184 MiB, settle
  near 156 MiB, and buffer count falls from 23,434 to 17,981 after collection.
  RSS peaks at **1.58 GiB**, ending at **1.53 GiB**. FDs stay 52 after startup;
  native thread count stays 48. Second-client last sampled RSS is 1.45 GiB and
  FDs 53; its three samples are too few for a trend conclusion.
- First-server sampled heap peaks at 698 MiB and ends at 359 MiB. FDs peak at
  135, fall to 75 and end at 85. RSS ends around 1.05 GiB. No sampled client
  or server swap, heap exhaustion or continuously growing descriptor count is
  demonstrated. Longer runs remain necessary to exclude a slow leak.
- First-client gameplay young collections pause for **207 ms** at 23:29:42
  and **184 ms** at 23:33:11. Startup full collections reach 266/309 ms.
  The longest full collections (332–470 ms) occur during shutdown and must
  not be counted as steady gameplay stalls. GC's `Allocation Failure` reason
  is a collection trigger here, not an OutOfMemoryError.
- Frame readback medians are **6.83/6.80 ms** and publication medians
  **3.18/3.07 ms**. These are meaningful portions of the 33.3 ms frame budget,
  but the target is met. Future optimization can address frame copying and
  allocation only with ownership/lifecycle regression coverage.
- Client output contains **12,166 graphics-trace lines** across the two runs,
  plus repeated connection/status/frame and input messages. Keep readiness,
  errors and periodic measurements; an opt-in verbose trace and lower cadence
  for unchanged status are reasonable future reductions. These logs alone do
  not establish logging as the primary performance bottleneck. TCP readiness
  polling already uses 15-second intervals after ready (41/19 total checks,
  including startup), as intended.

## Next device test

Use the same APK and Live Map defaults (`hiResMap=false`). Run 15–20 minutes
while moving, zooming, switching map modes and hiding/showing the map. Check
spawn/movement and then normal logout, server save/restart and reentry. With
the client stopped, disable Live Map and verify it disappears after restart;
re-enable without reimport and confirm it returns. Export both reports from
Diagnostics. Loader-only/off and longer-run behavior remain unconfirmed.

No code, graphics, GC, heap, audio or server-world settings were changed in
this review. Only review/handoff documentation was updated.

## Evidence identity

- `wurm-client-report (1)(10).txt`: 1,532,652 bytes; SHA-256
  `188fa22fd88657df1be2a4a15f2facd1bf5b01b72c6af6770a8a98150b096f77`.
- `wurm-server-report (1)(8).txt`: 263,694 bytes; SHA-256
  `ef7872bf5aa62e3ec2a6734ea0fc5f0d6390f1d1fef6ff912e8e5948b63891d7`.
- `Screenshot_20260912-182923.png`: visible Live Map window and 30 FPS HUD.
  Uploaded inputs stay outside the public repository.
