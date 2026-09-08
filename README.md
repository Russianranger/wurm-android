# Wurm Android — rooted server launcher

Minimal Kotlin launcher for an **existing** Wurm Unlimited ARM64 Termux server
POC. Milestone 1 is server-only: **no client, game downloader, embedded Java
runtime, or proprietary Wurm files** are included.

## Included

- Start Server and Stop Server buttons.
- Stopped, Starting, Running, Stopping and Error states.
- Live merged stdout/stderr, bounded to the latest 500 lines (4 KB per line).
- Foreground service, persistent notification with Stop, and a partial wake lock.
- Root execution with `su -c`, using native Termux Bash and Java.
- Saved runtime directory and Java executable fields.
- Runtime lock, missing-file checks, and TCP 3724 availability/readiness checks.

The target device for this milestone is **rooted Android 13 / ARM64**, with the
standard `com.termux` installation in the primary Android user profile. Minimum
and target SDK are 33; compile SDK is 34. This is a sideloading POC, not a Play
Store submission or a claim of testing on later Android versions.

## Build the APK on your device

Follow **[the complete native Termux build guide](docs/BUILD_TERMUX.md)**. No
computer, Android Studio, proot distribution or NDK is required. A standard
Gradle wrapper is included, pinned to Gradle 8.2.1 with a distribution checksum.

After the one-time tool setup:

```bash
cd "$HOME/wurm-android"
bash scripts/build-termux.sh
```

The debug-signed APK is `app/build/outputs/apk/debug/app-debug.apk`.

There is also a [GitHub Actions build](../../actions) that compiles, runs unit
tests and lint, and uploads the debug APK. This can produce the APK without
installing build tools on your handheld. See the build guide for download steps.

## First launch

1. Stop the server you currently run manually. **Back up the stopped world/runtime
   before the first app-driven launch.** Do not copy a live SQLite database as
   your only backup.
2. In normal Termux, install `bash` and `util-linux` if needed (`pkg install bash
   util-linux`). Confirm the existing Java runtime is still installed.
3. Install/open the launcher, allow notifications, and leave the runtime field as:
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

Supply your own existing `wurm-arm64-poc.jar`, both SQLite JARs, `server.jar`,
`common.jar`, `lib/`, `Adventure/`, and the rest of the POC runtime. Nothing is
downloaded, patched, imported or uploaded by the launcher. It streams console
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

- `MainActivity.kt`: platform-widget UI and notification permission.
- `ServerService.kt`: foreground lifecycle, notification, wake lock.
- `RootServerController.kt`: root session, bounded log reading, TCP probe.
- `server-supervisor.sh`: validation, environment, lock, JVM and targeted stop.
- `LaunchConfig.kt` / `ServerState.kt`: quoted configuration and bounded state.
- `app/src/test/`: JVM tests; `tests/test_supervisor.py`: host-only shell tests.
- [Device test checklist](docs/TESTING.md): checks that require your rooted Thor.

This repository does not include a license grant for Wurm or third-party game
files. The generated Gradle wrapper retains its upstream license headers.
