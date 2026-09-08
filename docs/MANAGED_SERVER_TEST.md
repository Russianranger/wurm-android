# Managed server preview: AYN Thor test

## What is implemented

The 0.4.0 `managedPreview` build installs separately from both earlier apps.
It integrates the existing importer and POC with an APK-installed native Java
runner. The original root controller, source/artifact pins and SQL-patched user
files are preserved. The Client tab reuses the existing document/settings UI.

| Requirement | Concrete implementation |
| --- | --- |
| POC classes | Gradle decodes and verifies the tracked base64 asset. Both handwritten classes remain byte-identical. A separately compiled Java 17 helper supplies diagnostic/control entry points without compiling against Wurm. |
| User files | Document picker imports the stopped prepared ZIP into private storage. The original import is retained; Wurm receives a separate copied runtime. No game download, game upload or proprietary CI dependency. |
| Java/process | OpenJDK 17.0.20 GA plus the pinned FCL Android port, built by `runtime-build/build.sh`. ELF files execute from the APK's installed native library directory. The Thor-tested JLI environment and image-layout adapter are reused. |
| Lifecycle/UI | A non-sticky foreground service owns one child, notification and wake lock. Start runs Java/SQLite preflight, writes a checkpoint, then calls the existing POC. Stop requests Wurm shutdown; Restart waits for a requested exit 0 and creates a fresh checkpoint. Logs are bounded and exportable. |
| World/configuration | Persisted imported-world selection, maximum heap (default 4096 MiB), expected TCP port (default 3724). The port setting only controls readiness checks. Existing Wurm configuration bytes remain untouched. |
| Recovery | Export the stopped working runtime or the before-start ZIP. Restore original/checkpoint atomically. Unconfirmed exit leaves a recovery marker and blocks the next Start until restore, protecting the last checkpoint. |
| Device gates | New JRE preflight; Wurm GameFolder/SQLite/Steam shim; real item SQL operations; port binding; save/reopen; background behavior, memory and recovery. These require the Thor. |

The first preview accepts the exact server/common/SQLite/POC hashes previously
recorded on the Thor. This prevents an accidental switch to stock, unpatched
desktop JARs. It does not implement or infer the missing SQL patch recipe; all
imported resources and library overlays are copied unchanged.

## Copy, install and run

1. Keep both installed preview apps and the successful 0.3.2 report. Do not clear
   their storage. Download **Wurm-Server.apk** from the **v0.4.0-managed-preview**
   release and install it. It has package suffix `.managed`, so no uninstall is
   required. Its screen identifies version 0.4.0 despite sharing the Wurm Server label.
2. Keep your existing **wurm-runtime-20260908-052948.zip** in Downloads. No new
   Termux commands or additional runtime downloads are required. Stop any server
   already occupying TCP 3724 before testing. The source runtime must be stopped
   when creating any replacement archive.
3. Ensure at least **4 GiB free internal storage** for this test. Your approximately
   661 MiB import needs an original copy, working copy, JRE, checkpoint and temporary
   recovery space. Large changed worlds need more; the app checks available space.
4. Open the 0.4.0 app and **Import Server ZIP**. Select the existing ZIP and wait.
   This preview intentionally retains one original import. Replacing that original
   is not enabled. Select **Adventure**, close/reopen once, and confirm selection.
5. In **Server Settings**, leave maximum heap **4096 MiB** and expected TCP **3724**.
   Tap **Start Server**, permit notifications, and keep the screen/app open for this
   first startup. No root prompt should appear.
6. Export the session report after the first startup attempt, including failures.
   The key stages are listed below. Do not install the separate runtime archive;
   it is already packaged inside the APK.

| Log evidence | What it establishes |
| --- | --- |
| Java version 17.0.20, `JAVA_OK`, `SQLITE_OK`, `PREFLIGHT_PASS` | New runtime can initialize, run threads and commit/reopen its disposable SQLite database under the app UID. No Wurm world was opened by this test. |
| `WORLD_LOCK_OK` | The native child owns the private runtime's POSIX record lock. Copy/export/restore and another child cannot acquire it concurrently. |
| GameFolder recognized/current, personal-server/offline messages | Progress through the unchanged POC entry point. |
| `TCP_READY`, Running | The POC returned and the expected loopback TCP port is reachable while this child remains alive. This is not yet a Wurm protocol/client or playability test. |
| `SHUTDOWN_REQUESTED` and optionally `SHUTDOWN_RETURNED`, `SERVER_EXIT=0` | The stop control reached the detected Wurm shutdown API and the process exited normally. Wurm may exit inside the API before the returned marker; saving must still be tested. |

The UI can remain Starting for a large world. Export logs if progress stalls.
The preflight has a 90-second timeout; Wurm world loading has no automatic kill
deadline. Stop during incomplete startup cancels with SIGTERM and requires
recovery, because safe saving during partial initialization is unproven.

## Stop, restart, persistence and background checks

Once Running, tap **Stop Server**. Wait for Stopped/child exit; if shutdown hangs
for a minute the UI offers Force Stop, but do not use that as evidence of saving.
Export the report before forcing. Use **Export working runtime ZIP** only after
the child exits. Then Start again and confirm the same world loads. Test Restart
while Running and verify the old `SERVER_EXIT` precedes the new preflight/start.

World selection persistence is already proven. World-data persistence requires
separate evidence: compare the exported databases with the working copy after a
normal stop/reopen and exercise the previously problematic item insert/update
paths using the existing supported server/admin or external client workflow.
The Android client is not available yet. Do not invent a SQL patch or treat a
successful SQLite diagnostic as proof that Wurm's item SQL paths work.

Only after the foreground test passes, try switching apps, returning to the UI,
and locking the screen briefly. Confirm the notification stays active, the child
and port remain alive, logs resume, and Stop still works. Record time/memory and
any Android battery-policy interruption. A foreground service/wake lock cannot
guarantee survival under Android memory pressure or an explicit force-stop.

## Failure and recovery

- A new-runtime preflight failure never starts Wurm. Export `wurm-server-report.txt`.
  The old passing diagnostic APK remains installed for comparison.
- After unconfirmed Wurm exit, **Start is disabled** to preserve the existing
  checkpoint. Export the session report, working ZIP and before-start ZIP first.
  **Restore before-start checkpoint** creates and selects a complete replacement
  copy only after validation. **Restore original import** returns to the initial
  uploaded snapshot. Successful restore clears the recovery marker.
- A failed restore retains the selected working copy and recovery marker. Low
  storage, bad ZIPs and symbolic links fail closed instead of replacing it.
- App-process death kills its owned native child using the existing parent-death
  mechanism. That is an unclean stop: it leaves recovery required. No stale PID
  file is used to kill other processes and no automatic restart occurs.
- Exported runtime ZIPs contain your game/world data and stay wherever you save
  them. Session reports contain raw Wurm logs. No report or runtime is uploaded by
  the app. Keep game files out of public GitHub issues and this repository.

## Build and verification

The maintained JRE source build runs on **Ubuntu 22.04, Linux x86_64**, using JDK
17 and the pinned Android-port NDK r21 toolchain. See `runtime-build/README.md`.
The successful runtime release is SHA-256 pinned by `runtime-build/runtime.json`.
APK builds use the existing Gradle/SDK34 setup plus NDK **26.1.10909125**:

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.1.10909125"
python3 -m unittest discover -s tests -v
bash ./gradlew --no-daemon :app:assembleManagedPreview \
  :app:testManagedPreviewUnitTest :app:lintManagedPreview
python3 scripts/verify-managed-apk.py app/build/outputs/apk/managedPreview/app-managedPreview.apk
```

GitHub Actions builds, tests, lints, verifies packaged hashes/class files/notices,
and publishes the first passing APK and corresponding runtime sources under the
immutable preview tag. The legacy native Termux guide still builds `debug`
(import/root controls); the embedded variants' native packager needs Linux x86_64
NDK tools. Installing/running the published managed APK needs no Termux.

Host tests cover working-copy isolation, original/patched JAR preservation,
checkpoint round trips, failed restores, ZIP export boundaries, symlink refusal,
operation exclusion, world arguments, native lock ownership/release and the
reflection control adapter using handwritten fake APIs. The full Kotlin sources
also compile against Android API34. These are not physical Wurm acceptance tests.

Development signing remains ephemeral. This separate package protects your old
apps, but it is not a live-world migration promise. Establish a durable signing
key and upgrade/export flow before keeping long-lived worlds here.

## File-change inventory

Paths below are relative to the repository root; launcher Kotlin files share
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/` unless noted.

| File | Change |
| --- | --- |
| `runtime-build/build.sh` | Pinned source/dependency downloads and strict Android 17.0.20 cross-build; source/notices output. |
| `runtime-build/android-17.0.20.patch` | FCL Android patch rebased onto 17.0.20, preserving new upstream changes and app-private TMPDIR support. |
| `runtime-build/README.md` | Source attribution, patch differences, licenses, build procedure and qualification limits. |
| `runtime-build/runtime.json` | Exact source-built runtime release URL and SHA-256 consumed by APK packaging. |
| `.github/workflows/runtime.yml` | Build and publish the maintained runtime with corresponding source archives. |
| `.github/workflows/android.yml` | Build/test/lint/verify the managed variant and publish its APK with JRE sources. |
| `app/build.gradle.kts` | Managed variant/package, shared diagnostic helpers and runtime packaging tasks; version 0.4.0. |
| `app/src/managedPreview/AndroidManifest.xml` | Managed launcher activity and foreground service; extracted native libraries. |
| `ManagedActivity.kt` | Server controls, saved selection/settings, recovery/export UI, report and live log display. |
| `ManagedLaunch.kt` | Validated world/heap/port and exact preflight/server argument lists. |
| `ManagedWorkspace.kt` | Original/working separation, file lock, checkpoint/export, atomic restore and recovery marker. |
| `ManagedSession.kt` | Single operation/process owner, bounded persisted session log and UI state. |
| `ManagedServerController.kt` | Pinned input verification, preflight gate, child lifecycle, readiness, stop/restart and failure recovery. |
| `ManagedServerService.kt` | Foreground notification, Stop action, wake lock and non-sticky lifecycle. |
| `app/src/main/java/io/github/russianranger/wurmlauncher/HomeActivity.kt` | Reuse Client tab from managed UI; Server tab returns to its caller. |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeRuntime.kt` | Read provider identity from the runtime manifest; generic integrity-error guidance. |
| `runtime-probe/src/server/ManagedServerMain.java` | Reflection-based control wrapper around the unchanged POC, exact shutdown API check and startup-failure exit. |
| `runtime-probe/native/world_lock.c` / `world_lock.h` | Native POSIX record lock shared with the Kotlin storage lock. |
| `runtime-probe/native/jvm_runner.c` | Acquire optional workspace ownership before loading Java. |
| `scripts/prepare-jvm-probe.py` | Share native runner compilation and include the lock helper; old JRE pins remain unchanged. |
| `scripts/prepare-managed-runtime.py` | Package pinned 17.0.20 data/native helpers/notices and verify Android dependency closure. |
| `scripts/verify-managed-apk.py` | Verify APK runtime hashes, modules, notices, ARM64 runner and handwritten JVM assets. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedWorkspaceTest.kt` | Recovery/isolation/arguments tests with synthetic data. |
| `tests/test_managed_bootstrap.py` | Control adapter tests against handwritten fake server/POC APIs. |
| `tests/test_world_lock.py` | Native lock exclusion and release after process death. |
| `README.md` | Current managed preview entry point and variant distinction. |
| `docs/IMPLEMENTATION_PLAN.md` | Link implemented milestone and remaining physical-device gates. |
| `docs/RELEASE_MANAGED_PREVIEW.md` | Download/install notes and concise first test. |
| `docs/MANAGED_SERVER_TEST.md` | Implementation, full test/recovery/build instructions and this inventory. |

No file under `poc/` changed. No proprietary server/common/SQLite runtime input
was added or edited in the repository.
