# Wurm Android handoff

Updated: 2026-09-12. Keep this file current when investigating, changing, or releasing the app. Start here when continuing in a new chat; then read the linked release/review documents and current source. Do not rely on a previous chat being available.

## Current branch test — 0.10.37 mod hook ordering, awaiting device test

User's first 0.10.36 server test failed with Announcer enabled. Continue only on **mod-launcher-test**; main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- Report: `upload/wurm-server-report(20260912-183958).txt`; preflight passes, Announcer loads, then `ProxyServerHook.registerOnMessageHook` throws `Communicator class is frozen` at 18:38:53.314Z. Controlled server child exit 1 after 604 ms; no normal Android game-entry/readiness marker. The before-start checkpoint was retained.
- Root cause reproduced locally: our `ServerHook.getMethod("createServerHook")` lookup resolves all its public method signatures, including unrelated event methods with Communicator/Player types. This defines/freezes game classes before the hook factory instruments them. Earlier fixture omitted those signatures and missed this failure.
- Fix: exact `MethodHandles` lookup of the ServerHook factory and listener methods. No defrost workaround or skipped hooks. Four bounded stage markers identify mod initialization, hook installation, callback initialization and Android entry.
- Expanded actual-Ago/Javassist regression reproduces the old frozen-class failure and passes with the fix, including the actual hook effect, two mod transformations/order, class identity, and failure/no-vanilla-fallback behavior. Fixture game/lifecycle code is authored; real Wurm/device startup remains unverified.
- Released **0.10.37**, code **51**, package `io.github.russianranger.wurmlauncher.modhookfix`, tag `v0.10.37-mod-hook-order`. Implementation commit **`e7ee1eb03fb9911c863e56651540db25b98ed0ac`**, tree `eaaaf6b17ba69549c7ba4fc7ed845baf470e1add`. Separate package preserves 0.10.36 data because CI debug signing keys vary between builds.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.37-mod-hook-order/Wurm-Server.apk), **53,760,901 bytes**, SHA-256 **`8c6f02d5644fd8bca9406f3ad363b793a0eabe19812dea612b5bfebefb4810a0`**.
- [CI run 34712199899](https://github.com/Russianranger/wurm-android/actions/runs/34712199899): build job `103602858718` and mod release job `103604204935` succeeded. Older import/JVM preview publishers were intentionally skipped. Host suite: **164 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed unit/lint gates. The expanded frozen-class regression passed in CI. APK v2 signature verified; certificate SHA-256 `52bad60512cfa5ecf6b687e6a0fd115f58b218670f9f02d1e37d1268b4de2879`.
- Independently downloaded APK matches release SHA256SUMS, manifest version/code/package, corrected MethodHandles launcher and stage markers; full `verify-managed-apk.py` passed locally. Relative to 0.10.36, JRE members, server POC and 39 native libraries are byte-identical. GL4ES differs only in 24 bytes covering the GNU build ID and compile-time banner; remaining bytes identical. Handoff-only commits after the implementation commit do not change the immutable APK.
- Retest: export **before-start checkpoint ZIP** from 0.10.36, install 0.10.37 alongside it, import checkpoint as server runtime (loader/Announcer/manifest included), check Mods, then start Announcer alone. Import normal client ZIP only for login test. Export Server report from Diagnostics, plus Client report if used. Do not add CropMod yet. [Detailed cause and steps](MOD_HOOK_ORDER_FIX.md).
- Loader/Javassist pins, mod store/toggles, client staging, world data handling, SQLite/login patches, graphics/audio/native/GC policies remain unchanged. No additional proprietary files are committed.

## Previous branch test — 0.10.36 mod launcher (2026-09-12)

User confirms 0.10.35 functional/stable and authorizes a **separate mod test branch only**. Main remains at `2e41fb091ee75a76b9116e934abecff90bc735d9`. Branch `mod-launcher-test` was created from it. Do not merge to main without a later instruction.

- Implementation commit `ad1f0d7ad8aad88d62b4c308259ce71c82acbf15`, tree `0d4c4676d9ad016a8a491f6b08816ddef74e6122`.
- Version **0.10.36**, code **50**, separate package `io.github.russianranger.wurmlauncher.modtest`. Released tag: `v0.10.36-mod-launcher-test`. [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.36-mod-launcher-test/Wurm-Server.apk), **53,760,457 bytes**, SHA-256 **`e95bed95a7525b023d2ff7c8235ceeed84362c656261297e466710a5c934aab5`**.
- [CI run 34710257609](https://github.com/Russianranger/wurm-android/actions/runs/34710257609): build job `103597617425` and mod release job `103598991469` succeeded; the two unrelated older preview publishers were intentionally skipped on this branch. Host suite: **164 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed their unit/lint gates. APK v2 signing passed; certificate SHA-256 `7101452a6220b9032618bbe5a057f60bbe1736f8b5adb49ff460d82e934eecf9`.
- Independently downloaded release APK matches SHA256SUMS, manifest version/code/package, required JVM assets and Mods classes. Full `verify-managed-apk.py` passed again locally. Handoff-only commits after the implementation commit do not change this immutable APK.
- Binary comparison with stable 0.10.35: JRE archive members and server POC are byte-identical; 39 native libraries are identical. GL4ES differs in exactly 25 bytes: GNU build ID and compile-time banner; all remaining bytes are identical.
- Implemented tab order: Server, Client, Mods, Diagnostics. Mod-specific file/dependency checks and manifest viewer are in Mods; existing Server/Client exports remain in Diagnostics and include mod manifests/startup evidence.
- ZIP import records each mod separately and starts it disabled. Toggles move owned folder, descriptor and config between `mods/` and `android-mods/disabled/<name>/`. A durable journal completes interrupted moves. Both sides share their existing ownership/native/file locks; no mutation during an affected runtime session. Server checkpoints/exports include the entire mod store and runtime-owned mod data.
- Server: user imports Ago 0.47 loader ZIP. Only its pinned core is installed; bundled optional mods, desktop scripts and old Javassist are not installed. APK carries unmodified Javassist 3.30.2-GA as a Java child-runtime asset with MPL notice/license and release source JAR. The transforming loader is entered before Wurm/POC classes resolve, while existing overlays/SQLite driver/console identity and Android STOP flow are retained. Mod initialization errors exit; no silent vanilla fallback.
- Client: import/manifest/toggle framework is implemented and explicitly labeled **staging only**. Client loader installation and execution are deferred until server physical tests pass. Existing client runtime ignores staged mods; report logs `CLIENT_LOADER_DEFERRED`.
- Compatibility limits: standard descriptor + per-mod JAR layout/direct side interface; duplicate imports do not overwrite existing mods; native/desktop/shared-classloader/ScriptRunner/legacy packages deferred. Metadata merges JAR defaults, `.properties`, `.config`/template. Required/imported dependencies and conflicts checked locally; exact version/order handling remains upstream. Structural config edits are rejected before launch. Mod config editor, update/removal and automatic downloading are not in this pass.
- Local evidence: 13 ModStore cases + 4 ManagedLaunch cases pass, including interrupted import/toggle recovery, config/data retention, dependency/conflict rejection and export/restore. Two Java tests use the actual pinned Ago discovery/resolver/classloader and Javassist with authored game/lifecycle fixtures; two mods transform one class in dependency order before game loading, and initialization failure cannot fall back. Five existing server diagnostics tests pass. Android API compilation passes with one pre-existing resize deprecation warning. Real upstream loader/Announcer/CropMod ZIP import, both-on validation, and both-off file removal pass. Stable APK's exact Java runtime already includes `jdk.zipfs`.
- Limits of evidence: authored game hooks in the host test do **not** demonstrate real Wurm lifecycle hooks, native Android behavior or mod gameplay. The initial physical Announcer test subsequently failed during hook installation; see the 0.10.37 correction above. Native/graphics/audio/GC/default-resolution policies are unchanged.
- Original 0.10.36 test plan (superseded by the 0.10.37 retest above): import normally stopped working export into this separate app; baseline start/stop; install loader; Announcer alone and toggle off/on; then Announcer + CropMod; verify clean STOP/restart and return both reports from Diagnostics. Keep a separate pre-mod export: disabling mods cannot undo persistent world/database changes. [Downloads, storage design and detailed checklist](MOD_LAUNCHER_TEST.md).
- Private server JAR recovered for metadata/reference outside git (`../wurm-mod-inputs`); it was not used to claim a complete running-server test. Upstream source/ZIP/compiler scratch in `../wurm-mod-research`. No proprietary runtime files are committed. Scratch may vanish; implementation and this handoff are repository-backed.

## Latest stable release — 0.10.35

- **0.10.35**, versionCode **49**, package `io.github.russianranger.wurmlauncher.worldcontrols`.
- Release tag `v0.10.35-world-controls`; implementation commit `25ad1233365001032eccda680340a7c9a418a845`, tree `c10f58dfaebdda0a7b3dc0f5e3aa63a1bfc5eccf`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.35-world-controls/Wurm-Server.apk), **52,915,761 bytes**.
- APK SHA-256 **`685c0fef8d58e961038d53cddd7b791a2e4399703cd9e4557817409e0f09714f`**.
- [CI run 34663722528](https://github.com/Russianranger/wurm-android/actions/runs/34663722528), build job **103471299895**: all four jobs succeeded. Host suite: **162 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed unit/lint gates. APK v2 signing verified; certificate SHA-256 `f5ea58b940536a39fb087fc3eee4a19eb303ab684ed18e339e35f84ed3284699`.
- Downloaded APK independently checked against release SHA256SUMS, manifest version/package, new menu/database/keybind classes and controller-reload marker. Host-test SQLite JDBC did not enter the APK. JRE archive members are byte-identical to 0.10.34, although ZIP packaging changes its outer hash. Server POC and 39 native files are byte-identical. GL4ES differs in exactly 25 bytes: GNU build ID and compile date/time banner; the remaining binary is identical. Existing graphics/audio/native-memory policies are preserved.
- Handoff-only commits after the implementation commit do not change this APK.
- **Device feedback:** user subsequently reported everything working and authorized the separate mod branch above. Original controls checklist: [world settings and bindings](WORLD_SETTINGS_AND_BINDINGS.md).

## Completed request — 2026-09-12

User reports 0.10.34 stable in both server and client. All four requested changes are implemented and released:

1. Server → World gameplay settings: 16 controls, including skill/action rates, five starting-skill values, breeding/field/tree settings, creature population and deed options. Displays verified bounds; three extra app minimums are labeled. Reads actual values, preserves untouched values, edits only while stopped under existing ownership/lock, captures a SQLite-managed WAL-inclusive backup, then updates changed columns in one transaction. Applies on next server start. Worlds sharing DB_HOST share settings; the actual database and local server ID are shown.
2. Default resolution is 1280x720 when absent/invalid; explicit previous choices are retained. A resolution change still requires restarting the client.
3. Gear → Game keybindings: dynamic native catalog, categories, key picker, modifiers and multiple keys; detects aliases/duplicate assignments, protects against stale/external edits, saves through existing atomic storage, and reloads the active game's bindings on its thread with an acknowledgment. Custom-console-command files remain read-only. A saved-but-failed live reload explicitly requests client restart.
4. Gear → Controller mappings: existing mapping editor, held-input release, and saved-profile reload on viewer resume; no client restart needed.

Key source: `WorldSettings.kt`, `WorldSettingsStore.kt`, `WorldSettingsDatabase.kt`, `AndroidWorldSettingsDatabase.kt`, `WorldSettingsDialog.kt`; `GameKeybinds.kt`, `GameKeybindsActivity.kt`, `runtime-probe/src/client/ClientKeybindings.java`; editor extensions in KeybindStore/WurmSettingsFX. SQLite JDBC is a host-test-only dependency.

Focused validation: 14 Kotlin/JUnit cases including real SQLite WAL backup and rollback, 15 keybind/storage cases, and 11 client startup cases. Initial host run caught four old 960x540 viewport-fixture expectations; they were corrected before the successful release CI. Local Android API compilation passed with only an existing deprecation warning. Real imported metadata: 351 built-in actions and 93 nonempty base keys. Actual default binding file: 70 bound actions / 78 keys; the combined editor catalog has 365 actions. Catalog read and no-op save preserved the actual default file bytes. That private check used real metadata/file content with an authored engine/HUD fixture, not a running game.

The earlier runtime JARs were recovered from persistent uploads after scratch cleanup. No proprietary JARs, disassembly or assets were committed. See [implementation details, bounds and device checks](WORLD_SETTINGS_AND_BINDINGS.md). The user subsequently reported this version working; the current unverified physical test is mod support above.

## Earlier device-confirmed stable release — 0.10.34

- **0.10.34**, versionCode **48**, package `io.github.russianranger.wurmlauncher.graphicstabs`.
- Release tag: `v0.10.34-graphics-tabs`; implementation commit `3c586f9ccc36b54ae49fa2cffd7046d34d0fc423`, tree `f64b127f4698ff81fc19b7b6a311e5ebbbfb10cc`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.34-graphics-tabs/Wurm-Server.apk), 52,831,145 bytes.
- APK SHA-256: `40396cd757aee2c1053c0f2e273459fe6b863464eddb44dc6f678890f74ecb7a`.
- [CI run 34619922092](https://github.com/Russianranger/wurm-android/actions/runs/34619922092): all four jobs succeeded. Build job 103331043313. Host suite: 154 tests with 17 expected initial fixture/platform skips; subsequent required native and actual LWJGL regressions passed, including both depth tests. Three Android variants built and passed unit/lint gates. APK v2 signature verified by CI.
- Downloaded APK independently checked against release SHA256SUMS: correct package/version, new page classes, 47-option adapter including brightness, native depth preference and complete patch metadata, dark resources and runtime packaging. JVM, ASan runtime and server POC are byte-identical to 0.10.32.
- Handoff-only commits after the implementation commit do not change this APK. Preserve this exact release/commit distinction.
- **Device feedback, 2026-09-12:** user reports both server and client stable and asks for world settings and in-game bindings. No separate explicit confirmation of the beam flicker was provided. Next device checks are for 0.10.35 above.

## Earlier measured baseline — 0.10.32

- Repository: Russianranger/wurm-android, branch main.
- Earlier measured baseline: **0.10.32**, tag `v0.10.32-dark-theme`, commit `4f66c65a1eb52d565af067db8697b8fd98ffc88b`, versionCode 46, application suffix `.darktheme`.
- [APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.32-dark-theme/Wurm-Server.apk), SHA-256 `fb706f5fea9f32f23f173078c18a6ada9484d8715011e99f81eaa0b768d3ee66`.
- CI run 34605432654 passed all four jobs; host tests, actual native regression checks, three Android variant unit/lint/build gates and APK verification passed.
- Physical device: AYN Thor Max, Android 13/API 33, Snapdragon 8 Gen 2/Adreno 740, 16 GB RAM.
- User now confirms repeated logout/login, app quit/reentry, movement and interaction work. Audio works. Object pop-in is resolved. Latest remaining visual issue: cross-beams flicker with viewing distance.

## Previous completed request — 0.10.34

The user authorizes changes and continued device-test releases. Requested on 2026-09-11:

1. Maintain this handoff document.
2. Review the latest stable-session reports and the cross-beam flicker video.
3. Audit the underlying client's graphics options; expose usable options and explain compatibility restrictions.
4. Organize the launcher into Server, Client and Diagnostics tabs. Move tests, reports and diagnostic output into Diagnostics; preserve basic server/client controls and running sessions.

Status: implementation, validation and release completed for 0.10.34; user reports it stable on 2026-09-12. The handoff was first persisted in commit `6c1a9980c3e1f7905818af408d86baf1230bcd8a`. Do not confuse that documentation checkpoint with the release commit.

An intermediate 0.10.33 build at `0505ca4f8749d8834a7d0c1bc720317ae0281861` started CI before the final audit found additional startup-only consumers and postprocess brightness. Its Android unit gate failed on the obsolete 17-field assertion; it was not released. 0.10.34 includes these extra controls and a complete depth-patch manifest marker.

Changes: one launcher host with persistent Server/Client/Diagnostics pages; 47 graphics controls (17 existing + 30 new) with restart-only deferral; verified EGL depth24 preference/depth16 fallback; pinned GL4ES unsized texture/renderbuffer precision changes. Runtime collectors, native memory checking and occlusion-query restrictions are preserved. ClientPage/DiagnosticsPage are new view controllers, not Activities. Return to Game reopens the viewer without starting a new client.

Validation is complete as recorded above. The initial Android run caught an obsolete 17-field test expectation; the corrected Kotlin/JUnit tests passed locally and all CI gates passed on the final implementation commit. See [the detailed audit and test plan](GRAPHICS_AND_TABS.md). Specific visual correction was not explicitly confirmed; general stability is now reported by the user.

Current attachments in the active scratch workspace:

- `upload/wurm-client-report(20260911-152658).txt`
- `upload/wurm-server-report(20260911-152659).txt`
- `upload/Wurm Server_2026-09-11 10_20_44.mp4`

The latest client header is 0.10.32 and reports a normal entry exit code 0, with game-loop observation true. Detailed review is in GRAPHICS_AND_TABS.md: three client entry exits 0, two requested server exits 0, median recorded presented FPS 29.8; recoverable startup GL errors remain, and samples are too short for long-term memory conclusions. Read uploaded files from scratch; do not fetch them through Library. Scratch may disappear between sessions. Recover previous uploads from their persistent file records when available before asking for a reupload.

## Established architecture and constraints

- Managed Android activities and foreground services own separate client/server JVM processes; navigating pages must not stop either process.
- Imported, user-owned Wurm runtime JARs/worlds stay private. Never commit them or game assets. The public repo contains original compatibility code and source-backed probes.
- Packaged OpenJDK 17, LWJGL/Pojav/GL4ES bridge, OpenAL; game renders to a raw RGBA frame file read by Android. Fullscreen 1280 x 720, collapsible gear controls and opacity are implemented.
- Keep downstream GL4ES fixes and compatibility limits. Unsupported occlusion queries are disabled; enabling them previously caused disappearing nearby objects.
- Client uses Serial GC; server uses G1. Do not silently switch collectors, remove ASan, globally disable explicit GC, or alter working runtime/import behavior during UI work.
- Scope is software reliability, performance and usability. The user explicitly excludes offensive-security/authentication-bypass research.

## Key source locations

- `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`: launcher activities, services, reports, settings and frame viewer.
- `ManagedActivity.kt`, `ClientActivity.kt`: single three-tab host plus compatibility client redirect; `ClientPage.kt` and `DiagnosticsPage.kt` own their page controls.
- `GraphicsOptions.kt`, `GraphicsSettingsDialog.kt`, `runtime-probe/src/client/ClientVisualOptions.java`: Android graphics catalog, settings UI and runtime option application. Keep wire order/validation consistent and extend meaningful tests with new options.
- `GraphicsTestActivity.kt`, `GameFrameView.kt`, `GraphicsFrame.kt`: fullscreen gear panel, frame consumption, pointer and input.
- `graphics-compat/`, `scripts/patch-gl4es.py`: graphics compatibility and checked downstream source fixes.
- `RuntimeMeasurements.java`, `AppMemoryMeasurements.kt`, `RuntimeObservationLog.kt`, `ServerProbeSchedule.kt`: periodic observations and throttled probes.
- `.github/workflows/android.yml`, `scripts/verify-managed-apk.py`: build/release gates.
- [Latest baseline review](DARK_THEME_AND_RUNTIME_REVIEW.md), [release history](RELEASE_GRAPHICS_PREVIEW.md).

## Important previous fixes

- 0.10.17: touch/controller input enabled character creation.
- 0.10.18–19: shader/pointer fixes; player-name editor moved to a dialog to stop scroll focus stealing.
- 0.10.20–22: native graphics menu avoids desktop JavaFX crash; frame sequence tracking fixes apparent 1 FPS; fullscreen/gear/opacity controls.
- 0.10.23: GL4ES VAO buffer-offset bug fixed with native guard-page regression; OpenAL lifecycle brings audio online.
- 0.10.24: unsupported occlusion queries disabled, fixing pop-in.
- 0.10.25–29: working Android ASan diagnostics, BTI/native ELF TLS fixes and isolated native startup test.
- 0.10.30: ASan identified shader-cache cleanup overread. Free hash-map values, then clear maps; retain capacity. Actual source regression reproduces old failure and passes fixed cleanup repeatedly. Stable baseline since this fix.
- 0.10.31: TCP checks slow from 500 ms to 15 seconds after readiness; JVM and viewer memory measured every 30 seconds; bounded GL error attribution retained separately from rotating console.
- 0.10.32: dark Android theme and white outlined pointer. No collector, graphics policy or runtime changes.

## Outstanding performance observations from the preceding run

- Client ran approximately 36 minutes and exited normally. Steady rendering/viewing approximately 29.9 FPS at 1280 x 720. No fatal ASan, OOM or frame-readback failures.
- Post-warmup PSS: client 1592–1777 MiB, server 1007–1030 MiB, Android viewer 111–143 MiB; no swap or proven continuing leak.
- Client Serial full GC pauses roughly 0.38–0.64 seconds and young pauses often 90–215 ms. `World.tick()` calls `System.gc()` about every ten minutes; disabling this globally may affect direct-buffer reclamation. Controlled collector comparison is future work, not an established fix.
- Server file descriptors fluctuate and drop around young collections; inspect owners before claiming a leak.
- Recoverable GL errors occurred during startup only. A GL4ES `glBindFramebuffer` attribution point reads existing driver error state, so it is not conclusive proof that binding caused the error.
- Remaining warnings include missing optional textures/models, unsupported normal-matrix notices, duplicate server templates and role-specific time-sync warning. Details are in the baseline review.
- Potential future work: avoid a new 3.5 MiB frame byte array every frame; reduce unchanged status/shader logging. Maintain ownership/lifecycle correctness. No speculative optimization has been applied.

## Build, publish and continuation

1. Check branch/worktree and remote head; preserve unrelated work. No owned-repo AGENTS.md was present at baseline.
2. Read current release workflow and test scripts. Run relevant host/Kotlin regressions; CI supplies the Android SDK and full native fixtures.
3. Bump version/code/suffix, report labels, workflow tag/title/instructions and release documentation consistently. Releases use distinct application suffixes: importing the existing exported working runtime may be needed in a new variant. Preserve backups.
4. Commit only original source/docs. GitHub plugin Git-tree/commit/ref tools can publish text changes when shell push is unavailable; compare the returned tree SHA with local `git write-tree` and update main without force.
5. Follow the exact commit's `Wurm Server APK` Actions run through all jobs. Download the release APK and checksum; verify version, package, signatures, expected native/runtime assets and source identity. Do not call an untested device fix confirmed.
6. Update this handoff with findings, implementation decisions, release link and remaining device checks. Give the user the APK and a short focused checklist.

Useful local investigation caches (not guaranteed to persist): `../wurm-review-inputs/client.jar` for private javap inspection; `../wurm-investigation/graphics-018/gl4es.tar.gz`; `../wurm-investigation/graphics-026/android-ndk-r26b`; `../wurm-investigation/graphics-021/tooling/` for Kotlin/JUnit; `../wurm-investigation/dark-032/release/` for the baseline APK. Use `java com.sun.tools.javap.Main` if the javap executable is absent.
