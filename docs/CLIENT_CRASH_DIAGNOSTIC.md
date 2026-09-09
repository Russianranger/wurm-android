# 0.10.8: identify the Thor's abrupt client exit

## What your latest report proves

`wurm-client-report(5).txt` is from **0.10.7**. It confirms the capability fix on
the physical Thor: core and legacy GL2.1 are true, GL3+ is false, and Wurm selects
its existing legacy/basic-water renderer. The FBO and shader-query checks pass.
The client produces one splash frame, starts builtin material preload, and then
exits **134** without a Java failure message or captured native crash details.

Exit 134 is consistent with SIGABRT, but the code alone does not establish the
signal or its cause. The last five `_gl4es_NormalMatrix` warnings do not prove
that uniform handling caused the abort. The working host probe does not reproduce
this device failure. TCP 127.0.0.1:3724 is reachable; Wurm login/world entry are
still unverified, and OpenAL still falls back to silent mode.

## What this milestone adds

This is a diagnostic release, not a claimed fix for the unexplained native exit.

- The verified source adapter wraps the actual core GL20 source upload,
  compile/link, attachment, status, uniform and attribute delegates with flushed
  BEGIN/END/THREW breadcrumbs. The native call, arguments, output buffers, return
  value and exception are retained. Breadcrumbs identify sequence, operation,
  shader/program object and relevant index. Source upload logs SHA-256 and size,
  never shader text. The cap is 4,096 calls per JVM, with a TRACE_LIMIT marker.
  This covers the current core path, not all OpenGL or ARB JNI calls.
- Each child attempt tracks native UID/PID and pending graphics operations.
  If Java cannot report an exception, the UI and exported log retain the exit
  code and an unfinished call or last graphics event. An unfinished call is a
  lead, not proof that it caused a crash on another thread.
- An unexpected exit in the signal-like range triggers bounded own-UID,
  current-attempt-time logcat capture. It may include other processes of this
  same app; native log lines identify PIDs. Android is then queried for an exact
  PID/UID/time-matched ApplicationExitInfo record. If a native tombstone is
  available, only signal, abort/cause and up to 32 crashing-thread frames are
  summarized. Raw memory, registers, maps, logs and descriptors are skipped.
  No binary tombstone is saved/exported. Logcat is limited to 64 KiB/240 lines
  and a three-second wait; tombstone input is limited to 1 MiB.
- Android may not track the exec child or expose its crash trace. Explicit
  LOGCAT_UNAVAILABLE, EXIT_INFO_UNAVAILABLE or TOMBSTONE_UNAVAILABLE lines record
  this. There is no retry without the UID filter, root command, READ_LOGS request
  or signal interception. User Stop and the diagnostic timeout do not trigger
  crash collection. Existing JVM hs_err capture remains available.

The capture follows Android's [native crash guidance](https://source.android.com/docs/core/tests/debug/native-crash)
and [ApplicationExitInfo API](https://developer.android.com/reference/android/app/ApplicationExitInfo).
The parser follows the [Android 13 AOSP tombstone schema](https://github.com/aosp-mirror/platform_system_core/blob/android13-release/debuggerd/proto/tombstone.proto).

Graphics native sources/pins, the private five-entry overlay, offline shim,
controller mappings, server runtime, POC and SQLite compatibility remain intact.
The APK contains no proprietary Wurm files. Real client startup is still attempted
through the existing window/input bridge, with its two-minute limit.

## Verification and remaining gates

- **84 Python/Java/native automated tests pass**, including five new trace tests
  covering delegation/results/exceptions, opt-in silence, source-hash privacy,
  bounds, source-drift rejection and abrupt-exit breadcrumb retention. The
  abrupt-exit fixture uses Runtime.halt(134); it tests flushed evidence, not an
  Android SIGABRT diagnosis.
- **Five new Kotlin tests pass** for attempt identity, pending-call pairing and
  bounds, selected tombstone fields/thread, malformed data, wrong identity,
  size/text/frame limits and ignored memory/log data.
- The native Mesa/GL4ES window test passes frame pixels, controller keyboard/mouse/
  wheel delivery, RESET, teardown, capabilities, FBO and shader queries with
  tracing enabled. The rebuilt API satisfies **all 317 required client members**.
- The private real-client material probe passes builtin preload, original legacy
  renderer selection/Volume checks, and the adapted real blur draw at
  **RGBA 64,128,192,255**, tolerance 1, with tracing enabled.
- Release CI builds, unit-tests and lints all three Android variants and verifies
  signing, runtime/graphics hashes, packaged trace helpers/delegates and exact POC
  bytes. Publication is gated on those checks.

Gate 4 has physically verified window/controller/splash and renderer selection.
Full scene rendering remains blocked by the unexplained device exit. Gate 5
still needs local ticket acceptance, actual login and a player in the world.
The Android crash collector needs this next Thor test; host results do not
qualify the Adreno driver or Android trace availability.

Developer checks: `python3 -m unittest discover -s tests -v` with the documented
SQLite fixture; `:app:testManagedPreviewUnitTest` for Kotlin; and
`WURM_HOST_GRAPHICS=<host-build> python3 scripts/test-window-host.py` for the native
regression. The existing private `ProbeClientMaterials` probe requires the user's
client JAR and private overlay; neither belongs in this repository.

## Exact AYN Thor test

1. Keep the working **0.6.0 server app** installed. Install **Wurm-Server.apk** from
   release **v0.10.8-client-crash**. Check the app displays **0.10.8**. Its version
   is **0.10.8-managed-preview**, code **22**, package
   `io.github.russianranger.wurmlauncher.clientcrash`.
2. In **0.10.8 → Client → Import Client ZIP**, import the **same complete client
   ZIP**: `client.jar`, `common.jar`, full `lib/`, full `packs/` including
   `graphics.jar`, `pmk.jar`, `sound.jar`, and remaining original assets. No new
   Wurm files are required. The separate preview needs its own import because
   CI debug signing currently changes between releases.
3. Start **Adventure** in the working server app and wait for its listening game
   port. Return to **0.10.8 → Client → Start Local Game**, **127.0.0.1:3724**.
4. Let the attempt fail or reach its two-minute limit. If it displays
   **Collecting crash details**, wait until that finishes before exporting. If a
   login/game screen appears, capture it and try the controls, then Stop Client.
   No repeat triangle test, PC, root, Termux command or manual setting edit is
   needed.
5. Select **Back to Client / Export → Export Client Report**. Send
   **wurm-client-report.txt**, plus a screenshot of any new screen. The needed
   report is **Client Report**, not Server Session, Storage or World Report.

We need the last graphics BEGIN/END pair, source hash, exit summary and any
`[client-crash]` evidence. Send the report even if Android says crash details are
unavailable; the flushed graphics breadcrumbs are independently useful.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.8; attach/checksum this guide. |
| `README.md` | Current evidence and diagnostic test link. |
| `app/build.gradle.kts` | Version 0.10.8, code 22, separate clientcrash package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Current version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientCrashCapture.kt` | Own-app logcat/exit record and bounded tombstone collection. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientCrashEvidence.kt` | Per-attempt identity and unfinished-call summary. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Enable traces, capture unexpected exits, retain failure reasons and report current gates. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientTombstone.kt` | Bounded selected-field Android tombstone parser. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Current version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the client crash diagnostic. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ClientCrashTest.kt` | Five identity, parsing and bounds tests. |
| `docs/CLIENT_CRASH_DIAGNOSTIC.md` | Evidence, scope, verification, every file and Thor steps. |
| `docs/CLIENT_INTEGRATION.md` | Current diagnostic architecture and confirmed/remaining gates. |
| `docs/CLIENT_THOR_TEST.md` | Current test link. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test link. |
| `docs/IMPLEMENTATION_PLAN.md` | Current diagnostic architecture and gate evidence. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and exact physical test. |
| `graphics-compat/README.md` | Current evidence and diagnostic test link. |
| `graphics-compat/src/wurm/graphics/GraphicsTrace.java` | Bounded flushed breadcrumbs and source hashes. |
| `scripts/build-lwjgl-api.py` | Wrap selected verified core GL20 delegates. |
| `scripts/test-window-host.py` | Exercise traces during real native window/input regression. |
| `scripts/verify-managed-apk.py` | Require trace class and GL20 references in packaged API. |
| `tests/test_graphics_trace.py` | Five delegate, privacy, bounds, drift and abrupt-exit tests. |
