# Production readiness proposal — 2026-09-13

This is a proposal, not an implemented release. Reviewed `mod-launcher-test` at
`da2c5bb370c9350492d9f83e9d5e9cebdb312dd9`; the APK remains **0.10.38**.
Keep implementation on this branch unless the user authorizes otherwise.
Only this plan and HANDOFF.md change in this review.

## Evidence and current status

The user now reports that the previously suggested tests worked. Record this
as additional **user-reported success**, including the suggested Live Map and
restart checks, not a newly measured soak test: no new reports or duration were
provided. The latest inspectable reports remain the September 12 two-session
[Live Map review](CLIENT_MOD_DEVICE_REVIEW_20260912.md). They show successful
client/server startup, visible Live Map, normal exits and median 30 FPS.

GUI findings below come from the current Android source and previously supplied
screenshots, not a fresh emulator/device walkthrough. The current application
is a functional device-tested preview. Production work should concentrate on
setup, navigation, updates, recovery and input completeness, while preserving
the graphics/audio/mod-loader fixes that established stability.

## Proposed everyday flow

Retain the requested tab order: **Server, Client, Mods, Diagnostics**. After
initial setup, remember the user's tab, defaulting a new completed setup to
Client. Do not start or stop a server merely because the user changes tabs.

| Surface | Current friction | Proposed behavior |
| --- | --- | --- |
| First use | Prepared-server ZIP terminology and separate imports assume prior knowledge. | Offer Play on this device, Server only, or Restore backup. Guide only the selected path; explain missing files before launch. |
| Client | Import, settings and several start buttons have equal prominence; Play is below setup actions. | A selected world/character card and one prominent **Play** or **Resume** action. Secondary settings, character selection and runtime management. State exactly whether Play starts the local server. |
| Server | Start/stop, world options, import/export and restore form a long list. | Status/world card, contextual Start or Save & Stop, then grouped World settings, Runtime and Backups. Put restart/force stop and destructive restores under explicit secondary actions. |
| Mods | Server/client sections and verbose loader diagnostics are stacked together. | Server/Client selector within Mods; installed-mod cards showing name, version and enabled state. Distinguish desired next-start state from observed loaded state. Keep toggles locked during the affected session. |
| Diagnostics | Tests, reports and live output compete on one long page. | Health summary and **Export support bundle**, then collapsible checks and filtered logs. Keep separate client/server exports available. Correlate session IDs/timestamps without duplicating embedded server logs. |

Use explicit states: missing runtime, ready, starting, playing, stopping and
failed. A failure should show the short cause, retained-world/checkpoint status
and the relevant recovery/report action. Avoid a general Retry button during
healthy play. A canceled or failed import must leave the prior installation
usable. Show progress and cancellation for long operations.

Current “Start Client” still connects to the fixed local endpoint. Label it
**Connect to running local server** if retained; remote/LAN server selection
would require separate implementation. “Expected existing Wurm TCP port” is a
readiness setting, not a control that changes the world's actual listening
port. Move it into advanced diagnostics or replace it with a verified real
connection configuration; do not imply it changes the server.

Keep the dark theme, white outlined cursor, 1280x720 default and full-screen
game. Use consistent dp spacing, readable text, at least 48dp touch controls,
visible controller focus and a compact landscape layout. Avoid raw-pixel layout
constants and reset-on-navigation behavior. Rename the launcher from the
server-only label when a stable client/server identity is selected.

## In-game menu and settings

The existing gear is the correct entry point. Make its first level compact:
**Resume, Graphics, Controls, Display, Back to launcher**. Put session-ending
actions below these, with clear client/server consequences. Returning to the
launcher must not imply stopping a running server. Opening the gear does not
pause the world; input must remain captured by the menu without reaching the
game underneath it.

- Preserve the existing full-screen and input-release behavior. Allow gear
  position adjustment so it can avoid the compass or mod windows. Apply opacity
  to panel backgrounds while keeping text legible; retain an easily discoverable
  reset. Put Restore game UI and detailed diagnostics under Support.
- There are already **47 exposed graphics controls**. Group them into Display,
  View distance, Terrain/vegetation, Lighting/shadows, Effects and Advanced,
  with search and group reset. Show effective values separately from preset
  inheritance. Show **Applied**, **Saved; restart required**, or a failed live
  application acknowledgment next to the setting. Keep resolution at 1280x720
  by default and preserve explicit user choices.
- Do not expose desktop-only controls as working features. Occlusion queries
  remain disabled for the established pop-in fix; desktop MSAA/VSync and an
  unsupported modern OpenGL renderer are not useful toggles in this pipeline.
  Explain supported alternatives such as FXAA and the existing frame limiter.
- Controls should contain Game keybindings and Controller mappings in one
  consistent editor flow. Add action search, capture the pressed key/button,
  conflict explanations, per-action reset and profile import/export. Preserve
  custom-command binding files and the existing acknowledged live reload.
- Complete the input path before calling this a general Android client. The
  current game view handles touch pointing/dragging and mapped controller
  events; there is no complete Android text/IME or conventional keyboard/mouse
  path. Add a keyboard button for chat and text fields, composed text/delete
  handling, and hardware keyboard, hover, right-click and wheel routing.
  Validate focus transitions and release held input when a menu opens or the
  viewer loses focus. Android's [InputConnection contract](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
  is the appropriate interface for software-keyboard text, not only synthetic
  key presses.

## Reconstructing the server preparation process

Personal-context searches did **not** retrieve the original conversation's
exact commands. The reconstruction below is supported by maintained repository
instructions, build source and the retained prepared server JAR. It should not
be presented as a recovered transcript.

The historical [Thor import instructions](THOR_IMPORT_TEST.md) package the
already working Termux directory
`/data/data/com.termux/files/home/wurm-arm64-poc/runtime` into a ZIP. The documented
steps install `zip`, enter that directory, then archive `.` into Downloads and
record hashes for `server.jar`, `common.jar` and `poc-lib/*.jar`. This is an
export of a prepared installation; it does not explain how a clean desktop
server JAR originally received all of its SQLite changes.

Three different components must be distinguished:

| Component | What exists now | What the app can do |
| --- | --- | --- |
| `wurm-arm64-poc.jar` | Source for the Android server entry and existing compatibility shim is in `poc/src`; deterministic builder is `scripts/build-poc.py`. | Already bundled and inserted when absent. A known older version is upgraded. Users do not need a compiler or a newly built custom JAR for each import. |
| Earlier item-database changes in `server.jar` | Import/readiness currently expects the known prepared JAR. Full original patch recipe is not documented in the repo. | Reconstruct and qualify a versioned transformation before accepting a clean server archive. |
| Creature-position SQLite overlay | `ServerPreflight`/`ServerSqlitePatch` already generate `server-sqlite.jar` in the temporary child session with exact class checks. | Reuse the existing automated overlay model and its database tests; preserve classpath order with mods. |

The retained prepared JAR provides a useful reconstruction lead: active
`ItemDbStrings`, `BodyDbStrings`, `CoinDbStrings` and `FrozenItemDbStrings`
classes coexist with older reference copies under `target/classes`. Each
reference contains two MySQL-style duplicate-key SQL constants absent from the
active counterpart. This is evidence of the affected classes, **not proof that
those reference copies equal a pristine vendor release**. Compare a clean,
user-owned supported server ZIP, recover the exact method changes and validate
their bindings and database effects. Do not use broad SQL string replacement.
No game classes or proprietary archive contents should be committed.

The import also currently requires `common.jar`, the complete `lib/` tree,
world folders/configuration/resources, and both
`sqlite-jdbc-3.53.2.1.jar` and
`sqlite-jdbc-3.53.2.1-natives-android.jar` in `poc-lib`. The app provides the
Android Java runtime. The Android SQLite dependency pack still needs a
reproducible build/provenance and distribution decision; the presence of a
public desktop JDBC dependency is not evidence that our Android native pack
can be recreated automatically today.

Engineering can rebuild the small POC using the documented command in
[poc/README.md](../poc/README.md):

```sh
python3 scripts/build-poc.py --classpath /path/to/server.jar:/path/to/common.jar --output poc/artifacts/wurm-arm64-poc.jar.base64
```

That is build-time work. `scripts/build-termux.sh` builds the Android APK; it
should not be described as the POC or stock-server preparation recipe.

## Recommended feature: Prepare server runtime

Expose **Prepare server runtime**, with **Import prepared backup** alongside
it. An optional final **Export prepared runtime** supports reuse elsewhere.
Do not expose “compile custom JAR” as an ordinary user task.

1. Select a ZIP containing the user's Wurm Unlimited dedicated server files.
   Identify a clean supported installation versus a previously prepared backup;
   accept a root folder or one enclosing folder. List missing components and
   unsupported versions before any world changes.
2. Show detected version/worlds and calculated peak storage requirements. The
   current static 4 GiB prompt does not account for original, working, staging
   and checkpoint copies. Select an included world; generating a new world is
   a separate capability, not promised by ZIP preparation.
3. Stage files privately. Install the app-bundled POC and a vetted Android
   SQLite dependency pack. Prefer bundled dependencies for offline setup if
   package size permits; otherwise use a verified pack import/download step.
4. Apply a **versioned compatibility recipe** for known inputs. Prefer a small
   generated overlay over editing the user's original JAR, if verified loader
   ordering supports it. Validate the item and position database transformations
   together with server mods. Preserve database row identity and update
   semantics; exercise actual SQLite operations, including rollback.
5. Run compatibility checks in a temporary directory/database, then atomically
   activate the prepared runtime. Keep the untouched source and a recoverable
   checkpoint. Never silently discard a world to get through setup.
6. Write a preparation manifest containing input hashes, recipe/POC/dependency
   versions and preparation result. Show Ready and offer Start server or Play.
   Preserve normal mod-store paths, manifests and disabled files in exports.

This is feasible without Termux on the target device. **Generic stock-ZIP
support is not already implemented.** Its prerequisites are the verified
item-patch recipe, a reproducible Android SQLite pack and compatibility/version
tests. Retain exact input checks through explicit recipes rather than deleting
the existing prepared-JAR hash checks. Already working exports remain supported.

## Release and recovery work

1. **Stable updates.** Choose a stable application ID and persist a release
   signing key in the release pipeline. Recent previews use different package
   names because their CI debug signing certificates vary. Android requires
   compatible signing for an installed-app update; this needs a deliberate
   release process, not another preview-package rename.
   [Android app signing](https://developer.android.com/studio/publish/app-signing).
2. **Complete migration/backup.** Add a full backup exporter to the existing
   preview before moving to the stable package. Current server exports are
   useful, but do not form a complete backup of client PlayerFiles, bindings,
   controller/graphics/overlay preferences and client mods. Include both sides,
   manifests and configuration; exclude transient frames, locks and caches.
   Restore transactionally and verify character/world persistence. Never rely
   on uninstall/reinstall as a migration plan.
3. **Lifecycle clarity.** Add an explicit keep-screen-awake policy while playing
   and a visible background policy. The client currently has a 15-minute timed
   partial wake lock and no game-view keep-screen-on flag; this is a test gap,
   not an established cause of a past crash. Exercise screen lock, recording,
   focus loss, controller reconnect, activity recreation and a surviving local
   server. Notification actions should resume the game or save/stop explicitly.
4. **Separate diagnostic and normal builds carefully.** Native ASan remains in
   the current runtime and adds overhead. Produce matched native variants and
   variant-aware packaging checks; compare on the Thor before making the normal
   build the default. Keep diagnostics available. Do not merely remove the
   sanitizer preload from libraries built to require it. Official Android
   guidance describes its CPU/memory cost, but that does not predict this app's
   measured improvement. [Android ASan guidance](https://developer.android.com/ndk/guides/asan).

## Remaining log findings and disposition

Counts are deduplicated across embedded report sections. The latest supplied
logs cover short sessions, not the additional tests reported in this message.
See the [dated review](CLIENT_MOD_DEVICE_REVIEW_20260912.md) for exact timestamps.

| Finding | Disposition |
| --- | --- |
| Seven startup/initial-entry GL errors; three recoverable pre-readback exceptions | Investigate before declaring a clean production release. Rendering recovers, but the collection point does not identify the originating GL call. Do not suppress them or blame Live Map without evidence. |
| Young GC pauses of 184–207 ms during play | Real hitch candidate. Reduce allocation first; keep the established collector until a separate controlled comparison justifies changing it. No OOM or proven continuing leak. |
| Readback about 6.8 ms plus publication about 3.1 ms per sampled frame | Worth optimizing after profiling. The viewer allocates a fresh approximately 3.52 MiB byte array per 720p frame: about 105 MiB/s at 30 FPS. Reuse bounded buffers with explicit ownership; this viewer allocation is distinct from child-JVM GC measurements. |
| 12,166 graphics-trace lines in two runs, repeated status output | Add normal/verbose modes and throttle unchanged records. Retain bounded crash breadcrumbs, errors and periodic memory/GC measurements. Logging is not proven to be the primary bottleneck. |
| One missing-tile warning during spawn selection; subsequent movement/vision and reentry succeed | Track and reproduce with new-character/teleport checks. Investigate if repeated or accompanied by wrong position/persistence. It is not an uncaught server crash. |
| Clothing/hair/normal-matrix and duplicate-template notices | Feature-specific compatibility/content issues. Defer if corresponding appearance behaves correctly; do not promise they are universally harmless. |
| Three initial Epic mission-difficulty warnings | Mission creation continues, but qualify Epic mission behavior if it is part of the supported feature set. Not evidence of general world corruption. |
| Ignored JRE RPATH, unavailable optional capability, audio scheduling-priority fallback | Can be reduced to informational/once-per-session output when the documented fallback succeeds. Audio and graphics initialized in these reports. |
| Intentional missing-capability fixture followed by PASS; already-stopped OpenAL; stop-request INFO stacks | Expected checks/redundant normal teardown, not crashes. Keep meaningful failure checks and clean up misleading presentation. |

TCP probe throttling already reaches 15-second intervals after readiness. Do
not reimplement it or combine speculative heap/GC changes with GUI work.
No retained evidence requires another emergency crash-fix APK before continued
personal testing. That is narrower than declaring all remaining warnings safe
to ignore or the application production-ready.

## Suggested implementation sequence and acceptance

1. **Flow and migration foundation:** four-tab reorganization, contextual
   Play/Resume, grouped gear/settings, full backup/restore and stable-release
   packaging plan. Verify existing worlds, settings, mods and input survive
   navigation and an upgrade/restore. Preserve current runtime binaries here.
2. **Input and lifecycle completion:** software/hardware text and pointer input,
   menu focus, background/recording recovery and keep-awake behavior. Verify
   chat, dialogs, controller transitions and save/stop semantics on the Thor.
3. **Automatic server preparation:** qualify the clean supported archive and
   dependency/patch recipe first, then implement the wizard. Acceptance is a
   clean ZIP to playable local world without desktop launcher or Termux; repeat
   preparation, canceled/failed import and restored-backup paths must also work.
4. **Performance and release qualification:** improve logging/buffer reuse,
   resolve or precisely document startup GL limitations, then compare matched
   normal/diagnostic native builds separately. Run a 60–90-minute mixed play
   session, restart/restore, mod off/on and new-character checks while recording
   frame-time percentiles, GC pauses and memory/FD trends. Longer runs establish
   more than a median FPS number. Broader Android support needs its own matrix.

Implementation still needs the user's authorization following this proposal.
No new APK, application behavior, server world, graphics policy, native runtime
or GC setting was changed while preparing it.

## Source starting points

- UI: `ManagedActivity.kt`, `ClientPage.kt`, `ModsPage.kt`, `DiagnosticsPage.kt`,
  `GraphicsTestActivity.kt`, `GraphicsSettingsDialog.kt`,
  `GameKeybindsActivity.kt`, `ControllerSettingsActivity.kt` under
  `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.
- Input/lifecycle/performance in that directory: `GameFrameView.kt`,
  `ControllerCapture.kt`, `ClientService.kt`, `GraphicsFrame.kt`.
- Runtime import: `app/src/main/java/io/github/russianranger/wurmlauncher/ManagedRuntimeStore.kt`;
  exact readiness inputs: `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeInputs.kt`.
- Preparation: `poc/README.md`, `scripts/build-poc.py`,
  `runtime-probe/src/server/ServerPreflight.java`, `ServerSqlitePatch.java`,
  `tests/test_server_sqlite_patch.py`, and `docs/THOR_IMPORT_TEST.md`.
