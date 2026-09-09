# 0.10.10: capture the server side of the login wait

## What the paired Thor reports establish

`wurm-client-report(7).txt` is 0.10.9. Its entry stage begins at
2026-09-09T14:26:03Z. At about three seconds the read-only monitor reports
`authenticated=true`, `loggedIn=false`, and `LOGIN_WAIT`. The original client
has read six bytes, queued 109 payload bytes, and its remaining 33 outgoing
bytes drain by four seconds. It then waits through 64 seconds with no additional
bytes read, no login result and no rejection message. The user stops the test.
The game thread is in the normal performConnection/startup rendering loop.

This is **local authentication acceptance**, not just TCP reachability or ticket
creation, and not real Steam authentication. The current shim/observer does not
set that engine authentication flag. Login and world entry are still unproven.
The transport's connected flag is a client snapshot, not a fresh health check.
Audio has a separate 32-bit OpenAL load failure and falls back to silence.

`wurm-server-report(2).txt` is the older 0.6.0 server app. It includes multiple
historical sessions. The latest starts at 14:24:08Z, reaches TCP readiness and
an observed file/listener snapshot at 14:24:32Z, then records
`SERVER_EXIT=0; stopRequested=false; force=false`. **That exit has no timestamp**,
so the report cannot establish whether it caused this client's wait, nor why
the JVM exited. Earlier NPC ClassCastException and nextInt-bound errors belong
to earlier sessions; they are not evidence of the latest login's root cause.

The server prints that java.util.logging.config.file is unset and it is
hardcoding logging. The app currently exports the child's stdout/stderr, leaving
Wurm's detailed login logging absent from this pair. Changing Steam callbacks,
login packets, NPCs or database rows is not justified by these reports.

## Implemented change

- Before any Wurm class initialization, **ServerDiagnostics** writes a small
  Java logging configuration in the disposable session temp directory and sets
  java.util.logging.config.file. This uses the supported configuration path
  Wurm itself checks. The root logger sends records to the authored
  **ServerLogHandler**; INFO is the default and LoginHandler enables FINE.
- The handler sends timestamped logger/level/message and bounded exception
  causes/stacks to stderr, which the existing supervisor persists. Messages
  mentioning credential/ticket fields are omitted after parameter formatting;
  individual messages are single-line and bounded. It never closes stderr.
  No original game configuration, database, map or JAR is modified.
- **DIAGNOSE** uses the existing child control pipe and a daemon observer,
  independently of STOP. While an owned server's client is waiting for auth,
  login or retry, the client requests a snapshot after ten seconds and at most
  once every fifteen seconds afterward. It reports logger routing and at most
  twelve relevant server threads with twelve frames each. The observer only
  reads metadata/stacks; it does not process login queues or change server state.
- A JVM shutdown hook records UTC time, whether the app sent STOP, and bounded
  stacks prioritizing real System.exit callers when available. It does not
  intercept/prevent exit or call save/shutdown itself. SIGKILL/native hard exit
  may bypass the hook; the parent's timestamped SERVER_EXIT remains useful.
  A shutdown hook or exit zero **never proves a successful save**.
- The Android supervisor records launch, readiness and exit timestamps,
  elapsed time and session ID. Its checkpoint/recovery/normal-stop decisions
  remain unchanged. All control-pipe writes share a lock.
- A per-attempt client watch reports **Local server stopped** when the server
  this app observed owning finishes. It ends only the client attempt; it never
  stops/restarts a server or treats an external listener as an owned process.
- Export Client Report now also includes this app's persisted Server Session
  Report. Export Server Session Report remains available independently. Both
  include timestamps/history; report headers identify the installed version
  and package. Another app's server report cannot be read from this sandbox.

The standard Java [logging configuration mechanism](https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/LogManager.html)
supports named handlers/levels and reloads. The
[shutdown hook API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html#addShutdownHook(java.lang.Thread))
does not run for every termination mode and has no guaranteed ordering relative
to other hooks. Device evidence must confirm Wurm retains this logging route;
LOGGER snapshots make an override visible instead of silently claiming capture.

## What is unchanged and what is still blocked

The byte-identical POC JAR, handwritten Steam shim, pinned patched server/common
JAR checks, SQLite fixes, world lock/checkpoints, server startup/shutdown APIs,
client entry point, login packet methods, graphics API/native pins, overlays,
controller bridge and five-minute client startup budget are retained. The client
and server still run in separate owned JVM processes, with independent controls.
The existing 0.6.0 installation is not updated or removed.

This completes the **server login/exit observability** step of Gate 5. It does
not fix or claim completion of login, gameplay or full world rendering. A new
test against the proprietary server JAR was not possible here: that JAR is not
in the repository or the two current attachments. The paired device evidence
must reveal the actual handler failure or blocked server thread before a
targeted compatibility fix is made. Do not increase the timeout again or alter
Steam authentication based only on the previous wait.

## Verification

- **91 Python/Java/native tests pass**. Four new Java fixture tests verify
  logging/reload/parameter formatting, bounded credential-safe output, requested
  versus unrequested shutdown and a real System.exit caller while preserving
  exit code zero. Existing bootstrap tests exercise DIAGNOSE/INSPECT followed by
  STOP and verify the original save-control behavior and failure exits.
- **Seven Kotlin state tests pass**: four existing connection/deadline tests and
  three new owned/external server, exit/reset and diagnostic-rate tests.
- CI builds, unit-tests and lints all three Android variants before publishing;
  APK verification requires both new server diagnostic classes, the existing
  runtime/native hashes and exact POC bytes, and excludes proprietary files.
- These fixtures are authored test APIs, not evidence of an accepted Wurm login.

## Exact AYN Thor test — run both sides in 0.10.10

1. Keep **0.6.0 installed**. Stop its server if running and wait until it is no
   longer running. If it is already in Error after an exit, do not restore or
   delete anything just for this test.
2. In 0.6.0 select **Export working runtime ZIP** and save
   `wurm-working-runtime.zip`. This reads the stopped working files. Keep the
   original app and its before-start checkpoint; the prior unexpected exit has
   not confirmed a save. No Termux or PC is needed.
3. Install **Wurm-Server.apk** from **v0.10.10-client-login**. Verify **0.10.10**
   on the screen, version code **24**, package
   `io.github.russianranger.wurmlauncher.logintrace`. The separate preview keeps
   earlier app data/signatures intact and therefore needs its own imports.
4. In **0.10.10 → Server**, import the stopped working runtime ZIP and select
   Adventure. This consumes the same patched `server.jar`, `common.jar`,
   `poc-lib/`, `lib/`, worlds, database/map files and remaining runtime contents
   as before. The app creates independent original/working/checkpoint copies.
5. In **0.10.10 → Client → Import Client ZIP**, import the same complete client
   ZIP: `client.jar`, `common.jar`, all `lib/`, all `packs/` and remaining assets.
   No replacement game JARs or new native files are required.
6. Ensure the old app's server stays stopped. In **0.10.10 → Client**, choose
   **Start Local Game**. It starts this preview's imported server and connects
   to **127.0.0.1:3724**. The report should identify LOCAL_SERVER_SOURCE=this-app
   and the server should print LOG_CONFIG_READY. Keep only one server running.
7. If login stays waiting, leave it for about **60 seconds** so multiple server
   snapshots can be captured, take a screenshot and press **Stop Client Test**.
   If the server exits, the client should explain that automatically. If a
   world appears, capture it and briefly test movement/look/clicks before Stop.
8. Export **Client Report → wurm-client-report.txt**. In the **same 0.10.10 app**,
   go to Server and export **session report → wurm-server-report.txt**. Send both
   plus the screenshot. These are not Storage or World Report, and this time
   the server report must come from 0.10.10. Client Report also embeds that
   app's server history as a fallback.

Stop Client does not stop the server. After exporting, use the Server tab's
normal Stop if it remains running. Retain recovery files after an unexpected
exit; no automatic restore, database repair or server restart is added here.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.10 and attach/checksum the new guide. |
| `README.md` | Current paired-report finding and next test link. |
| `app/build.gradle.kts` | Version 0.10.10, code 24, separate logintrace package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Current version and instructions to run both sides in this preview. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientServerWatch.kt` | Per-attempt owner tracking and rate-limited diagnostic requests. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Include this app’s server report, identify server source, request wait snapshots and explain an owned server exit. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Identify the current version. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the current server/client login milestone. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedServerController.kt` | Serialized diagnostic/control writes and timestamped child start/readiness/exit. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedSession.kt` | Small state snapshots, diagnostic routing and actual version/package/export time in reports. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ClientServerWatchTest.kt` | Three owned/external, exit/reset and request-rate tests. |
| `docs/CLIENT_INTEGRATION.md` | Current Gate 5 architecture and remaining blocker. |
| `docs/CLIENT_LOGIN_TEST.md` | Evidence, architecture, limitations, tests and exact same-app Thor instructions. |
| `docs/CLIENT_THOR_TEST.md` | Current test link; mark previous milestone as historical. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test link; mark previous milestone as historical. |
| `docs/IMPLEMENTATION_PLAN.md` | Current Gate 5 architecture and remaining blocker. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | 0.10.10 release notes and import/export steps. |
| `graphics-compat/README.md` | Current evidence and next test link; graphics code unchanged. |
| `runtime-probe/src/server/ManagedServerMain.java` | Install logging before Wurm, handle DIAGNOSE and mark requested shutdown. |
| `runtime-probe/src/server/ServerDiagnostics.java` | Per-session JUL configuration, bounded logger/thread snapshots and shutdown evidence. |
| `runtime-probe/src/server/ServerLogHandler.java` | Timestamped, bounded console logging with sensitive-message omission. |
| `scripts/verify-managed-apk.py` | Require the new server diagnostic classes in the published helper. |
| `tests/test_managed_bootstrap.py` | Per-fixture temp directory and DIAGNOSE/INSPECT followed by STOP regression. |
| `tests/test_server_diagnostics.py` | Four real-JVM logging, reload, output-bound and exit-caller fixture tests. |
