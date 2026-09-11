# Wurm Android handoff

Updated: 2026-09-11. Keep this file current when investigating, changing, or releasing the app. Start here when continuing in a new chat; then read the linked release/review documents and current source. Do not rely on a previous chat being available.

## Stable baseline

- Repository: Russianranger/wurm-android, branch main.
- Latest published baseline: **0.10.32**, tag `v0.10.32-dark-theme`, commit `4f66c65a1eb52d565af067db8697b8fd98ffc88b`, versionCode 46, application suffix `.darktheme`.
- [APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.32-dark-theme/Wurm-Server.apk), SHA-256 `fb706f5fea9f32f23f173078c18a6ada9484d8715011e99f81eaa0b768d3ee66`.
- CI run 34605432654 passed all four jobs; host tests, actual native regression checks, three Android variant unit/lint/build gates and APK verification passed.
- Physical device: AYN Thor Max, Android 13/API 33, Snapdragon 8 Gen 2/Adreno 740, 16 GB RAM.
- User now confirms repeated logout/login, app quit/reentry, movement and interaction work. Audio works. Object pop-in is resolved. Latest remaining visual issue: cross-beams flicker with viewing distance.

## Current work in progress

The user authorizes changes and continued device-test releases. Requested on 2026-09-11:

1. Maintain this handoff document.
2. Review the latest stable-session reports and the cross-beam flicker video.
3. Audit the underlying client's graphics options; expose usable options and explain compatibility restrictions.
4. Organize the launcher into Server, Client and Diagnostics tabs. Move tests, reports and diagnostic output into Diagnostics; preserve basic server/client controls and running sessions.

Status: investigation started; implementation and next APK are not yet complete. Update this section before release.

Current attachments in the active scratch workspace:

- `upload/wurm-client-report(20260911-152658).txt`
- `upload/wurm-server-report(20260911-152659).txt`
- `upload/Wurm Server_2026-09-11 10_20_44.mp4`

The latest client header is 0.10.32 and reports a normal entry exit code 0, with game-loop observation true. Detailed review is pending. Read uploaded files from scratch; do not fetch them through Library. Scratch may disappear between sessions: request missing reports or owned runtime JARs again when necessary.

## Established architecture and constraints

- Managed Android activities and foreground services own separate client/server JVM processes; navigating pages must not stop either process.
- Imported, user-owned Wurm runtime JARs/worlds stay private. Never commit them or game assets. The public repo contains original compatibility code and source-backed probes.
- Packaged OpenJDK 17, LWJGL/Pojav/GL4ES bridge, OpenAL; game renders to a raw RGBA frame file read by Android. Fullscreen 1280 x 720, collapsible gear controls and opacity are implemented.
- Keep downstream GL4ES fixes and compatibility limits. Unsupported occlusion queries are disabled; enabling them previously caused disappearing nearby objects.
- Client uses Serial GC; server uses G1. Do not silently switch collectors, remove ASan, globally disable explicit GC, or alter working runtime/import behavior during UI work.
- Scope is software reliability, performance and usability. The user explicitly excludes offensive-security/authentication-bypass research.

## Key source locations

- `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`: launcher activities, services, reports, settings and frame viewer.
- `ManagedActivity.kt`, `ClientActivity.kt`: currently separate server/client pages; diagnostic controls are mixed into both.
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
- Post-warmup PSS: client 1.59–1.78 GiB, server 1.01–1.03 GiB, Android viewer 111–143 MiB; no swap or proven continuing leak.
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
