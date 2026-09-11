# Wurm Server for Android

**Continuing this project in a new chat? Read [the maintained handoff](docs/HANDOFF.md) first.**

**Current test: 0.10.34 — three tabs, expanded graphics and depth precision.**
The Thor passed repeated login/logout and app reentry on 0.10.32; object pop-in
is resolved. This build separates Server, Client and Diagnostics, expands the
menu to 47 graphics controls, and prefers supported 24-bit depth for the reported
cross-beam flicker. The visual correction still needs device confirmation.
[Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.34-graphics-tabs).
[Graphics audit, report findings and device checks](docs/GRAPHICS_AND_TABS.md).

**Previous test: 0.10.32 — dark app theme and white pointer.** The Thor completed
approximately 36 minutes on 0.10.31 with normal client/server exits and about
29.9 FPS after startup. This release makes the app dark by default and changes
the pointer to white with a black outline. GC stalls remain the highest-priority
performance follow-up; existing runtime and graphics settings are retained.
[Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.32-dark-theme).
[Report findings and test instructions](docs/DARK_THEME_AND_RUNTIME_REVIEW.md).

**Previous test: 0.10.31 — runtime observations.** The Thor completed about ten
minutes on 0.10.30 with normal client/server exits. This build reduces ready-state
TCP checks from twice per second to once every 15 seconds, records bounded GL
error origins, and samples client/server/Android memory every 30 seconds. It
keeps the current rendering, heap and collector settings.
[Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.31-runtime-observations).
[30–45 minute device test](docs/RUNTIME_OBSERVATIONS_TEST.md).

**Previous test: 0.10.30 — shader cache cleanup fix.** The Thor passed
0.10.29's native startup test. ASan then stopped both splash-screen attempts at
the same GL4ES shader-relink cache overread. Cleanup treated uniform location
keys as hash-table bucket indices. This build frees cached values and clears
the maps correctly. A host test reproduces the old 32-byte allocation overread
and passes repeated cleanup/reuse with the correction. Retry local play; the
standalone startup check does not need repeating. Other heap faults and
sustained gameplay remain unverified.
[Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.30-program-cache).
[Test instructions and limits](docs/CLIENT_NATIVE_HEAP_TRACE.md). Keep older apps and saved data.

**Previous test: 0.10.29 — native heap startup recursion fix.** Native ELF
TLS corrected ASan's allocator recursion. The Thor report confirms allocation
redzones, thread creation/join, and standalone exit zero. The checker also ran
inside the actual client and produced an actionable GL4ES memory-error report.

**Previous test: 0.10.28 — isolated native startup test.** Its preinit recorder
worked on the Thor and obtained registers, maps, and a 183-frame stack trace.
The fault is now localized to ASan's emulated TLS allocation recursion. Neither
Java nor graphics loaded in that standalone test.

**Previous test: 0.10.27 — BTI entry correction and parent PID capture.** All
1,518 public ASan entries pass the expanded BTI check. On the Thor, startup now
fails with SIGSEGV/SEGV_MAPERR before native markers. No Java or game was reached.

**Previous test: 0.10.26 — heap diagnostic thread startup fix.** Backported
LLVM's prctl/PAC correction. The next device report exposed missing BTI landing
instructions in the source-built runtime's assembly entry points. No game or
ASan allocation/thread startup was reached in those attempts.

**Previous test: 0.10.25 — native client heap diagnostic.** Allocation redzones
initialized, but the bundled NDK ASan runtime crashed during thread startup on
the Thor. Neither reported attempt reached Java initialization or game rendering.

**Previous test: 0.10.24 — nearby object visibility.** Disabled unsupported
GL4ES occlusion queries while retaining distance/frustum culling. The next report
confirms the policy was active, but early native crashes prevented a visual test.

**Previous test: 0.10.23 — audio and vertex buffer fix.** Audio is confirmed
working on the Thor. This release corrected a reproduced VAO buffer-offset
error and added Android OpenAL. The next report still contains separate native
allocator failures at shader startup and after quitting; those remain unresolved.

**Previous test: 0.10.22 — fullscreen and graphics controls.** The next Thor test
confirmed graphics changes and sustained gameplay at 1280 × 720. Its client crash
and silent audio startup are the focus of 0.10.23.

**Previous test: 0.10.21 — frame display and game UI recovery.** Fixed the viewer's
whole-second timestamp gate. The next Thor test confirmed the display-rate gain
and showed the HUD remaining visible. The report still ends with an EGL cache /
native allocator abort after window closure; clean native shutdown is unresolved.

**Previous test: 0.10.20 — Android graphics settings and frame delivery.** Replaced
the failing JavaFX Settings route, added a Performance preset, and delivered raw
RGBA frames at a 30 FPS target. The next Thor report confirmed settings application
and 30 producer FPS, exposing the separate 1 FPS Android viewer bottleneck. Wurm
used 800 × 480 despite the old 800 × 450 label; that label is corrected in 0.10.21.

**Previous test: 0.10.19 — vertex pointer and name editor fix.** Corrected GL4ES
rebasing internal host pointers as VBO offsets and moved name editing into a
dialog. The next report reached five minutes of gameplay and then failed while
opening JavaFX settings; the preceding native draw failure did not recur in that run.

**Previous test: 0.10.18 — shader and frame performance fix.** Reproduced shader
errors were corrected and the viewer's target increased from 5 to 15 FPS. The
next device report showed no recurrence of those shader errors and approximately
10–12 presented FPS in the later game loop, followed by a native vertex-pointer
crash. See [the earlier changes](docs/CLIENT_SHADER_PERFORMANCE_FIX.md).

**Previous test: 0.10.17 — touch and visible pointer controls.** The Thor's 0.10.16
imported successfully, created player Thor and reached the in-game character dialog.
The viewer lacked touch handling and a visible mouse pointer. This release adds
direct touch selection, a pointer using actual game-thread coordinates, and a larger
game view. [Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.17-touch-input).
Follow [the character-creation controls test](docs/CLIENT_TOUCH_INPUT_FIX.md).
Final setup, terrain rendering and persistence remain unverified; GL4ES shader
errors are the next known graphics issue.


**Previous test: 0.10.16 — existing runtime import fix.** 0.10.15 rejected existing
ZIPs containing the previous bootstrap before startup. This release upgrades only
that exact known bootstrap in the staged copy while preserving game/world/configuration
bytes and the original ZIP. The personal-server character-creation fix is retained.
[Download 0.10.16](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.16-import-upgrade).
Follow [the import and local-game test](docs/SERVER_IMPORT_UPGRADE_FIX.md).
World entry and persistence remain unverified.


**Previous test: 0.10.15 — [personal-server character creation](docs/SERVER_PERSONAL_MODE_FIX.md).** The Thor's 0.10.14 test passed local authentication but rejected missing player Thor. The POC was turning personal-server mode off inside `runServer(false, true)`, overwriting its earlier true setting. The corrected `runServer(true, true)` enables Wurm's existing creation path and verifies the retained mode. [Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.15-personal-server). Import the same two ZIPs, select Adventure/Thor and use this version's **Start Local Game** with older servers stopped. Complete in-game setup if shown; send Client Report, Server Session Report and a screenshot. World entry/persistence still need testing; the earlier native exit issue remains unresolved.

**Previous test: 0.10.14 — [local login credential fix](docs/CLIENT_LOGIN_IDENTITY_FIX.md).** The Thor's 0.10.13 server used the encoder fix successfully and accepted local authentication, then rejected the launcher's blank login credential. The direct launcher now supplies the persisted local identity already used by its Steam shim. [Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.14-local-login). Import the same server and complete client ZIPs into this version, select Adventure/Thor and use its **Start Local Game** with older servers stopped. Export **Client Report** and **Server Session Report**. Login/world entry still need testing; the later EGL abort during exit remains unresolved.

**Previous 0.10.9 milestone:** the Thor passed material/GUI/terrain setup and reached Connecting with at least 375 frames. Its final exit followed the two-minute app timeout. That milestone exports real authentication/login/retry messages and allows five minutes for startup. Follow the [local connection test](docs/CLIENT_CONNECTION_TEST.md). Earlier instructions below are historical.


**Client milestone 0.10.0:** [download the window/input preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.0-client-window)
and follow the [AYN Thor test](docs/GRAPHICS_THOR_TEST.md). The Thor passed the
previous graphics diagnostic twice. This preview connects the Pojav window layer
and real LWJGL input queues, then attempts Wurm startup; playable Wurm/login is
not yet qualified. Keep the working server app installed.


**0.9.1 graphics correction and diagnostics:** [Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/tag/v0.9.1-graphics-diagnostics).
The 0.9.0 Thor report passed native/context/shader startup, then failed the aggregate
draw check. This build fixes an empty-shader-log request bug reproduced on host
GL4ES and reports the exact GL operation/error if another failure remains.
Open **Client tab → JVM Graphics Test → Run Graphics Test**. The new test runs real
ARM64 LWJGL/Pojav bindings and GL4ES in the managed JVM, draws a desktop GLSL triangle,
verifies pixels at two sizes and shows the resulting frame in Android. Export
**Client Report** afterward. No game import, PC, Termux or root is needed.
Keep the working 0.6.0 server and 0.8.0 client; this is a separate `.graphicsfix1`
package. [Exact Thor steps, build/source details and every changed file](docs/GRAPHICS_THOR_TEST.md).
**Thor drawing/readback acceptance is pending; Wurm rendering/login/gameplay are not yet working.**

The following records the preceding client evidence and source milestone.

**0.8.0 Thor results:** controller events reached the diagnostic JVM, local Steam
handler/ticket initialization passed, resource JAR checks passed, and the server
answered at `127.0.0.1:3724`. Client startup fails before login with
`no lwjgl in java.library.path` during profile/display initialization. See
[the two reports and exact scope of these passes](docs/THOR_CLIENT_080_PASS.md).

**Next graphics work:** the pinned Pojav Java API candidate now compiles. Small
handwritten adapters bring the scoped audit against the user's client to 38/38
classes and 317/317 member signatures. [Build/audit tools and findings](graphics-compat/README.md)
are committed; the native Android render host, matching libraries and GL4ES context
remain to be integrated. This source milestone does not change the APK. The
current Thor reports need no repeat test or new import.

[Download the APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.8.0-client-launch),
follow [the exact no-PC Thor test](docs/CLIENT_THOR_TEST.md), and export
**wurm-client-report.txt** from the Client tab. The new `.clientlaunch` package
installs alongside earlier versions. Keep the working 0.6.0 server and its world;
the new app can connect to its listener at `127.0.0.1:3724` without migration.
The controller test needs no game import. Client launch needs the same complete
owned client ZIP imported into this new package.

[Architecture, remaining gates and every 0.8.0 changed file](docs/CLIENT_INTEGRATION.md)
include the reusable Pojav/LWJGLX/GL4ES path. No proprietary JARs or assets are
committed or bundled. **This is not yet a playable client.** Android native graphics,
gameplay input, server ticket acceptance and Wurm login remain unpassed gates.
[0.7.0 findings](docs/THOR_CLIENT_FINDINGS.md) retain the earlier
device evidence and now record private inspection of the supplied client JAR.

**0.6.0 passed on the Thor:** the world report survived app reopening, five
Adventure map files and the active `localhost/sqlite` databases were observed,
and the Java child exposed listeners on TCP 3724 and 48020. Normal Stop exited 0.
See [the device evidence, configuration limits and next client test](docs/THOR_WORLD_PASS.md).

**0.6.0 adds world/configuration reporting:** Start captures recognized GameFolder,
SQLite paths, map/database file observations, port evidence and candidate
configuration identities. View/export the saved report while running or stopped.
See [the exact Thor steps and every changed file](docs/WORLD_CONFIGURATION_TEST.md).
Install alongside 0.5.0 and import its stopped working-runtime ZIP; keep 0.5.0
and its tested data. No root or Termux command is needed.

**0.5.0 storage persistence passed on the Thor:** after a server run and normal
Stop, the post-stop and post-reopen reports match across all **12,007 files**.
Both checks passed all **36 SQLite databases**; the maps and database files that
changed during the run retained their bytes after closing/reopening the app.
See [the report comparison, active data paths and next milestone](docs/THOR_STORAGE_PASS.md).

**0.5.0 adds stopped storage verification:** capture a persistent file baseline,
compare after Start/Stop and app reopening, and check disposable copies of the
runtime's SQLite databases. View/export an audit report without editing game
files. See [the exact migration, test and file-change guide](docs/STORAGE_VERIFICATION.md).

**0.4.1 passed managed startup, working-copy reopen, Restart and requested shutdown on the Thor.**
Java 17.0.20, SQLite and networking preflight passed under an ordinary app UID.
Wurm loaded Adventure, progressed past the earlier native abort, reached TCP 3724,
then exited with code 0 after normal Stop. The cumulative follow-up now records
three successful start/exit cycles of the same working runtime, including Restart.
See the
[physical-device evidence and next lifecycle test](docs/THOR_SERVER_PASS.md).

The new build opts out of heap-pointer tagging only inside the Java child before
Java loads and adds a networking preflight. This is a compatibility test; the
underlying native pointer bug is not yet located or repaired. See the
[original abort analysis and compatibility change](docs/THOR_NATIVE_HEAP_FIX.md).
It installs alongside 0.4.0 under package suffix `.managedfix1`, retaining earlier
data. The user also confirmed **five minutes in another app** and **two minutes
with the screen locked**, returning to Running after both. File persistence after
normal Stop and app reopening also passed in 0.5.0. Verification of a specific
gameplay change and extended background/memory behavior remain open.

0.5.0 installs separately as `.storagepreview`. Export the stopped working runtime
from 0.4.1 and import that ZIP into 0.5.0; keep the earlier installation and backup.

The 0.4.0 foundation remains: import your prepared runtime ZIP, select a world and
Start/Stop/Restart through a foreground service, without root or Termux. The app
bundles source-built Android OpenJDK 17.0.20, retains the original import, creates
a before-start checkpoint and provides live logs, working-copy export and restore.
No proprietary Wurm files or functioning Wurm client are bundled. The Client tab
retains its import-reference and Settings groundwork.

- [Download Wurm-Server.apk 0.6.0](https://github.com/Russianranger/wurm-android/releases/tag/v0.6.0-world-preview)
- [World/configuration report: exact Thor procedure and every changed file](docs/WORLD_CONFIGURATION_TEST.md)
- [Storage verification: exact Thor procedure and every changed file](docs/STORAGE_VERIFICATION.md)
- [Thor startup/reopen/Restart/Stop PASS and remaining checks](docs/THOR_SERVER_PASS.md)
- [0.4.1 correction, every changed file and next device test](docs/THOR_NATIVE_HEAP_FIX.md)
- [Exactly what to copy/install/run on the AYN Thor, recovery and changed files](docs/MANAGED_SERVER_TEST.md)
- [Managed preview release notes](docs/RELEASE_MANAGED_PREVIEW.md)
- [Implementation plan: packaging, imports, JVM, UI, worlds and device gates](docs/IMPLEMENTATION_PLAN.md)
- [Handwritten POC source and current JAR](poc/README.md)
- [Maintained Java source build and provenance](runtime-build/README.md)

Import a complete ZIP of your **stopped, working, SQLite-patched** runtime from
Downloads. Game files are copied unchanged into private storage; the app supplies
the tracked POC JAR. The managed preview requires the known Thor JAR hashes and
does not patch stock Wurm files. Keep the ZIP and exported working copies. The
managed package retains one original import; replacing it is not enabled.

The [0.2.0 import-only APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.2.0-import-preview)
and [0.3.2 diagnostic APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.3.2-jvm-probe)
remain available unchanged. Their existing installation/data are not migrated.

## Existing rooted launcher

- Start Server and Stop Server buttons.
- Stopped, Starting, Running, Stopping and Error states.
- Live merged stdout/stderr, bounded to the latest 500 lines (4 KB per line).
- Foreground service, persistent notification with Stop, and a partial wake lock.
- Root execution with `su -c`, using native Termux Bash and Java.
- Saved runtime directory and Java executable fields.
- Runtime lock, missing-file checks, and TCP 3724 availability/readiness checks.

The target device for the **rooted regression path** is Android 13 / ARM64, with the
standard `com.termux` installation in the primary Android user profile. Minimum
and target SDK are 33; compile SDK is 34. This is a sideloading POC, not a Play
Store submission or a claim of testing on later Android versions.

## Build the legacy import/root APK on your device

Follow **[the complete native Termux build guide](docs/BUILD_TERMUX.md)**. No
computer, Android Studio, proot distribution or NDK is required. A standard
Gradle wrapper is included, pinned to Gradle 8.2.1 with a distribution checksum.

After the one-time tool setup:

```bash
cd "$HOME/wurm-android"
bash scripts/build-termux.sh
```

The debug-signed APK is `app/build/outputs/apk/debug/app-debug.apk`.

For the embedded managed APK, use the Linux x86_64 SDK/NDK workflow in
[MANAGED_SERVER_TEST.md](docs/MANAGED_SERVER_TEST.md#build-and-verification), or
download the GitHub Release. The native embedded packager requires NDK host tools;
the Termux script continues to build the legacy `debug` variant.

There is also a [GitHub Actions build](../../actions) that compiles, runs unit
tests and lint, and uploads the APK. The passing 0.2.0 build publishes an import
preview to Releases. It is debug-signed; see the release notes before upgrading
an older CI APK. Building/installing the app does not require proprietary Wurm
JARs: Gradle decodes and verifies the POC artifact into generated assets.

## Rooted regression launch (optional)

1. Stop the server you currently run manually. **Back up the stopped world/runtime
   before the first app-driven launch.** Do not copy a live SQLite database as
   your only backup.
2. In normal Termux, install `bash` and `util-linux` if needed (`pkg install bash
   util-linux`). Confirm the existing Java runtime is still installed.
3. Open **Open rooted POC controls and live logs**, allow notifications, and leave the runtime field as:
   `/data/data/com.termux/files/home/wurm-arm64-poc/runtime`.
4. The Java field defaults to `/data/data/com.termux/files/usr/bin/java`.
   Prefer the **resolved executable of your working POC** if you have multiple
   JDKs: obtain it in Termux with `readlink -f "$(command -v java)"`.
5. Tap **Start Server** and grant the **launcher app** root access in your root
   manager. Termux's own grant is not sufficient. The app immediately enters a
   foreground service while waiting for root permission.
6. Watch the logs. `Running` means the managed JVM has started; the status also
   shows whether `127.0.0.1:3724` accepts TCP connections. A TCP connection is not
   an authenticated Wurm health check. World loading can take time.
7. Tap **Stop Server** in the app or notification and wait for `Stopped`.

Only edit settings while stopped. They are saved when Start is pressed. Closing
or rotating the Activity does not stop a running server. Notification denial
does not bypass Android's foreground-service requirement; enable notifications
in Android settings if the Stop notification is not visible.

## Exact server payload

The supervisor changes into the selected runtime and invokes the selected Java
binary with the working POC arguments unchanged:

```bash
java \
  -Xms512m \
  -Xmx4g \
  -Djava.awt.headless=true \
  -cp "wurm-arm64-poc.jar:poc-lib/sqlite-jdbc-3.53.2.1.jar:poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar:server.jar:common.jar:lib/*" \
  poc.AndroidServerMain Adventure
```

For this **separate rooted screen**, supply your own existing `wurm-arm64-poc.jar`, both SQLite JARs, `server.jar`,
`common.jar`, `lib/`, `Adventure/`, and the rest of the POC runtime. Nothing is
downloaded, patched, imported or uploaded by that root controller. It streams console
output only; it does not tail log files written separately by Wurm.

## Root, shutdown and lifecycle limitations

- **The JVM runs as UID 0 in this first implementation.** Only run trusted JARs.
  Newly created databases/logs can be root-owned, which can prevent later
  non-root Termux access. The app does not recursively chmod/chown your files.
  Start with a backed-up test world. A later milestone can drop to the Termux UID.
- Stop closes the app-owned control pipe. The supervisor sends **SIGTERM to its
  own child JVM**, then waits. It never uses `pkill`, `killall`, or an unverified
  stored PID. JVM shutdown hooks can run, but Wurm-specific world saving on
  SIGTERM still needs validation on the actual POC. This is **not** an implemented
  in-game administrative shutdown/save command.
- There is deliberately **no automatic SIGKILL**. If the server ignores SIGTERM,
  the state stays Stopping and the foreground service remains active. Use your
  existing server administration procedure to investigate; do not repeatedly
  start another JVM on the same world.
- The runtime's `.wurm-launcher.lock` is held with `flock`, not a stale PID file.
  Never remove it while a process might be using the runtime. The empty file can
  remain after shutdown; the kernel releases the lock when its holders exit.
  This lock protects launcher sessions, not manual Java commands that ignore it.
- The supervisor attempts SIGTERM when the app's control pipe disconnects,
  including ordinary app-process death. Android force-stop, root-manager failure,
  SIGKILL of the supervisor, or device power loss can still leave an orphan or
  interrupt saving. No automatic restart, boot receiver, or orphan adoption is
  included. `Stopped` on a fresh app process means **not managed by this app**,
  not proof that no externally started JVM exists. Check Termux if uncertain.
- Start rejects an already-open TCP 3724 before launching. Do not run a manual
  server concurrently: an external startup can race this check.
- A wake lock costs battery; it does not make the server immune to low memory,
  OEM process killing, or Android's process restrictions. A 4 GB max heap also
  needs memory for native allocations and the rest of Android.
- Root implementations and SELinux policies differ. Permission/SELinux denial
  is reported in logs; the app does **not** disable SELinux or change system policy.
  Do not expose the root-run server to untrusted networks without reviewing its
  existing Wurm network configuration.

## Source layout and checks

- `HomeActivity.kt`: Server/Client tabs, document picker, world selection and reports.
- `ManagedRuntimeStore.kt`: bounded, transactional private ZIP import and identity report.
- `ImportCoordinator.kt`: serial background import surviving Activity recreation.
- `poc/`: both handwritten JVM classes and their reconstructable Java 17 artifact.
- `app/build.gradle.kts`: source/artifact verification and generated POC asset.
- `MainActivity.kt`: platform-widget UI and notification permission.
- `ServerService.kt`: foreground lifecycle, notification, wake lock.
- `RootServerController.kt`: root session, bounded log reading, TCP probe.
- `server-supervisor.sh`: validation, environment, lock, JVM and targeted stop.
- `LaunchConfig.kt` / `ServerState.kt`: quoted configuration and bounded state.
- `app/src/test/`: JVM tests; `tests/test_supervisor.py`: host-only shell tests.
- [Device test checklist](docs/TESTING.md): checks that require your rooted Thor.

This repository does not include a license grant for Wurm or third-party game
files. The generated Gradle wrapper retains its upstream license headers.
