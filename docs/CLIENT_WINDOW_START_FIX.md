# 0.10.3: client window startup and original-error reporting

## Thor evidence and reproduced causes

The owner's `wurm-client-report(2).txt` from 0.10.2 passes inventory and Steam
compatibility, all **20** logical font/style raster checks, the real profile and
resource packs, and entry into **Wurm main thread**. The font fix is physically
confirmed. Entry exits 42 before any `WINDOW_CREATE`, frame or login evidence.

The reported NullPointerException is in `Display.destroy()`, freeing GLFW
callbacks for handle zero. Wurm calls cleanup before reporting its startup
exception, so this secondary exception hides the original error.

With the supplied private client JAR (SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`),
direct invocation of the real `LwjglClient.initWindow()` reproduces
`NoClassDefFoundError: javafx/stage/Stage` in `loadIcons()`. The engine asks
`WurmStage.getIconNames()`, loading a JavaFX Stage despite bypassing the launcher.
After fixing that dependency, its default maximized window reproduces
`HeadlessException` in `getWindowedFullscreenSizeAndPosition()`, querying AWT
screen size. Both originals were reproduced on the host; the Thor report only
exposes the cleanup failure.

## Bounded implementation

1. An independently authored `WurmStage` supplies the four icon resource names
   used by the engine. The real client loads/decodes its own icons from the imported
   JAR. No Stage, JavaFX images or proprietary resources are packaged. Desktop
   launcher subclasses remain unsupported.
2. After profile loading/saving and option-version checks, the bootstrap calls the
   verified real `DisplayOption.set(maximized,width,height,hz,fullscreen,resizable)`
   with `false,960,540,-1,false,false`. This avoids desktop monitor queries, fits
   the existing 1024-pixel pbuffer bound and retains Wurm's display validation.
   The bootstrap does not explicitly save this override. Android's existing viewer
   scales this logical viewport; it is not the native screen resolution.
3. The generated Pojav `Display.destroy()` skips callback release without a window,
   clears its handle/creation flags and tolerates repeated destruction. The build
   verifies the exact source block before patching; upstream checkouts and native
   code remain unchanged.
4. An authored headless `ErrorReporterPanel` implements the engine's five static
   error-reporting calls. It logs and retains the original Throwable without a
   Swing dialog. After the game thread exits, a reported error produces exit 42
   even when Wurm caught it internally; it cannot silently become launch success.

Helpers precede the imported client in the existing managed client classpath.
APK verification requires exactly nine authored compatibility classes and excludes
compile-only stubs. Server launch/runtime/import, POC, SQLite fix, Steam identity/
ticket and controller mappings retain their existing behavior.

The reused graphics components remain pinned
[Pojav LWJGL2](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237),
[Pojav Java GLFW](https://github.com/PojavLauncherTeam/PojavLauncher/tree/b12ad048157b3aa255d078c235dd4571e1900309/jre_lwjgl3glfw)
and [GL4ES](https://github.com/ptitSeb/gl4es/tree/81547d986798e876de8b434193920b606a72363f).
This changes the generated Java integration, not the EGL/backend architecture.
Corresponding source, patches and license notices accompany the APK.

## Verification and limits

- **71 automated tests pass**, including explicit viewport configuration,
  asynchronous crashes and a failure reported internally by the game thread.
- The rebuilt Pojav candidate covers all **38 classes / 317 members** in the
  supplied client's scoped LWJGL references. This is static API coverage only.
- Actual private `LwjglClient.initWindow()` passes on host GL4ES/Mesa: imported icon
  decoding, 960x540 window and keyboard/mouse initialization. A sample clear reaches
  the frame file with checked dimensions and pixel color. Cleanup before creation
  and twice after creation passes. Wurm's real `processError` routes a deliberate
  test exception into the reporter with the original identity preserved.
- The existing GL4ES triangle/input regression passes real rendered-pixel checks,
  keyboard, mouse buttons including button 7, wheel, reset and cleanup.
- GitHub Actions requires all Android variants to build, run unit tests and lint;
  it verifies runtime/graphics identities, helpers, exact POC bytes and APK signing
  before publishing.

Optional private regression: compile `scripts/ProbeClientWindow.java` with built
`runtime-probe.jar`, `wurm-window.jar`, `graphics-probe.jar`, `pojav-wurm-api.jar`
and the user's `client.jar`. Run in a **disposable working directory**, with
`client-compat.jar` before the client in the classpath, and host native-library/
environment settings from `scripts/test-window-host.py`. Set
`java.awt.headless=true`, `wurm.client.offline=true`, `wurm.client.host=127.0.0.1`,
`wurm.client.port=3724`, a disposable `user.home`, and `wurm.graphics.frame` to a
file in that directory. Expect `WURM_WINDOW_INIT_PASS` and
`WURM_WINDOW_PROBE_PASS`; `PRIVATE_PROBE_ERROR_ROUTE` is deliberate. No supplied
client, extracted assets or disassembly is committed.

**Gate status:** a host-tested window-startup correction within Gate 4 is complete.
Full Gate 4 Wurm scene rendering/audio and Gate 5 authentication/world entry remain
open. The host probe calls the real window method and draws a sample clear, not
the full game. Subsequent GL features, context/thread handling, audio natives,
login dispatch and ticket acceptance need the next Thor report. The two-minute
limit and bounded frame readback remain in place.

## Exact next AYN Thor test — no PC

1. Keep the working **0.6.0 server app** installed with Adventure and its data.
   Download [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.3-client-window-start/Wurm-Server.apk)
   from [release 0.10.3](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.3-client-window-start).
   Install alongside it. Check **0.10.3-managed-preview**, code **17**, package
   `io.github.russianranger.wurmlauncher.clientwindowfix`. The separate package
   accommodates CI debug signing and preserves existing app data.
2. Open **0.10.3 → Client → Import Client ZIP**. Import the same complete ZIP that
   passed: `client.jar`, `common.jar`, full `lib/`, full `packs/` (including
   `graphics.jar`, `pmk.jar`, `sound.jar`) and remaining original assets together.
   This package needs its own import. No additional game files, JavaFX, fonts,
   Java install, root or Termux commands are needed.
3. Start Adventure in the working **0.6.0 server app** and wait for its listening
   port. Return to **0.10.3 → Client → Start Local Game**. The target remains
   **127.0.0.1:3724**; the frame viewer opens automatically. You do not need to
   repeat the successful triangle/controller diagnostic.
4. Watch for a Wurm menu, login screen or scene. If one appears, capture a
   screenshot and try controls briefly. A blank/sample frame or open TCP port
   alone does not establish a game connection.
5. After failure, timeout or **Stop Client**, return to Client and tap
   **Export Client Report**. Send **wurm-client-report.txt** and any screenshot.
   Choose **Client Report**, not server Session, Storage or World reports.

New markers: `WINDOW_HELPER`, `WINDOW_OPTIONS_IMPORTED`, `WINDOW_OPTIONS_ANDROID`
and `ICON_RESOURCES`. Look next for `WINDOW_CREATE`, `WINDOW_READY`, `WINDOW_FRAME`
or the original exception under `CLIENT_REPORTED_FAILURE` / `BOOTSTRAP_FAILED`.
`WINDOW_DESTROY_SKIPPED` means cleanup had no live window; it is not a rendering pass.

## Every changed file

| File | Change |
| --- | --- |
| `client-compat/src/com/wurmonline/client/launcherfx/WurmStage.java` | Headless icon-resource helper. |
| `client-compat/src/com/wurmonline/client/ErrorReporterPanel.java` | Original failure logging and retention without Swing. |
| `client-compat/README.md` | Exact helper packaging and scope. |
| `runtime-probe/src/client/DirectClientLaunch.java` | Helper identity checks, explicit viewport and reported-error handling. |
| `scripts/build-lwjgl-api.py` | Verified generated-source patch for Display cleanup. |
| `graphics-compat/window/wurm/graphics/WindowProbe.java` | Zero-window/repeated cleanup and dead-handle assertions. |
| `scripts/test-window-host.py` | Require cleanup regression marker. |
| `scripts/ProbeClientWindow.java` | Optional real client window/frame/error-routing regression. |
| `tests/test_client_compat.py` | Viewport, helper packaging and internally reported failure regression. |
| `scripts/verify-managed-apk.py` | Exact nine compatibility classes. |
| `app/build.gradle.kts` | New helpers; 0.10.3/code 17/clientwindowfix package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Report version and confirmed font milestone. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the window fix. |
| `.github/workflows/android.yml` | Publish 0.10.3; attach guide/checksum. |
| `docs/CLIENT_WINDOW_START_FIX.md` | Diagnosis, verification, test steps and file inventory. |
| `docs/IMPLEMENTATION_PLAN.md` | Architecture and remaining gates. |
| `docs/CLIENT_INTEGRATION.md` | Window integration and qualification. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Current APK notes and steps. |
| `docs/CLIENT_THOR_TEST.md` | Current test link. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test link. |
| `README.md` | Current release/test link. |
