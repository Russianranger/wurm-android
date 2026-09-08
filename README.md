# Wurm Server for Android

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
with the screen locked**, returning to Running after both. Changed-world
persistence and extended background/memory behavior still need validation.

0.5.0 installs separately as `.storagepreview`. Export the stopped working runtime
from 0.4.1 and import that ZIP into 0.5.0; keep the earlier installation and backup.

The 0.4.0 foundation remains: import your prepared runtime ZIP, select a world and
Start/Stop/Restart through a foreground service, without root or Termux. The app
bundles source-built Android OpenJDK 17.0.20, retains the original import, creates
a before-start checkpoint and provides live logs, working-copy export and restore.
No proprietary Wurm files or functioning Wurm client are bundled. The Client tab
retains its import-reference and Settings groundwork.

- [Download Wurm-Server.apk 0.5.0](https://github.com/Russianranger/wurm-android/releases/tag/v0.5.0-storage-preview)
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
