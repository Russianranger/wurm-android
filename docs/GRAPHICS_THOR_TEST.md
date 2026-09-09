# AYN Thor: 0.10.0 window, controller and Wurm startup test


**Current 0.10.5:** the Thor passed the real framebuffer check in 0.10.4, then hit Java 8 buffer-cleanup calls that are incompatible with Java 17. The next build verifies the two-class ABI adapter and actual cleanup before game launch. Follow the [buffer startup test](CLIENT_BUFFERS_FIX.md). Earlier milestone instructions below are historical.

The previous **0.9.1 graphics gate passed on your Thor twice**: orange triangle on
blue, six verified/displayed frames, same-context resizes and clean JVM exits.
No need to repeat that as the main test. The new test uses LWJGL2 `Display`,
fixed-function GL4ES drawing and the actual keyboard/mouse queues used by Wurm.

## Install and files

1. Keep the working **0.6.0 server** and its Adventure world installed. Keep 0.9.1
   if you want to compare the earlier graphics test.
2. Download [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.0-client-window/Wurm-Server.apk)
   on the Thor and install it. This release is a separate package:
   `io.github.russianranger.wurmlauncher.clientwindow`, version
   **0.10.0-managed-preview**, version code **14**. Its first screen and Client
   heading show 0.10.0. It does not upgrade or erase your existing server app.
3. **Nothing else is required for the window/controller test.** The APK includes
   the ARM64 JVM, native graphics backend and source-built Java window adapter.
   You do not need a PC, root, Termux, Steam or a separate Java/graphics download.
4. For the later Wurm startup attempt, have the **same complete client ZIP that
   previously passed import** available in Downloads. The new package needs its
   own import. Keep the original client layout: `client.jar`, `common.jar`, full
   `lib/`, full `packs/` (including its resource JARs), every other original asset
   directory/file, and `nativelibs/` if present. Flat ZIPs or one outer
   `WurmLauncher/` folder work. Do not import only client.jar or the server ZIP.
   The previous expanded installation was about 1.64 GB; allow that space again.
   No proprietary client/server files are provided by the APK or repository.

## Report 1: window and controller test

1. Open **0.10.0 → Client tab → LWJGL Window / Input Test → Run Window / Input Test**.
   Wait for `WINDOW_READY` / `INPUT_READY`. Swipe the top control row horizontally
   if necessary. This is a new button, separate from Start Controller Test and
   JVM Graphics Test; those older diagnostics remain available.
2. Expect a smaller orange triangle on blue and a cyan crosshair. Move the **left
   stick** to move the triangle with W/A/S/D; move the **right stick** to move the
   cyan crosshair. These pixels are drawn by the JVM through LWJGL and GL4ES.
3. Hold **A or RT** (left-click) and **LT** (right-click); the triangle changes
   color. Release them. Press the other face buttons, shoulders, D-pad, Start and
   Select; their key events should appear in the report. Start/B send Escape to
   LWJGL; use touch to leave this diagnostic.
4. While holding a movement direction, briefly switch apps and return with
   controls neutral. The triangle must stop moving. Controller removal/focus loss
   should release held keys/buttons, not leave them stuck.
5. Tap **Finish Window Test**. It also finishes automatically after 90 seconds.
   Expect **Window test passed**; this means drawing and clean shutdown, not that
   every mapping or Wurm login has passed. Stop Client Test forcibly cancels instead.
6. Tap **Back to Client / Export → Export Client Report**. Save
   `wurm-client-report.txt` and send it as **report 1: window/controller**.
7. Run the window test once more and finish it to check a fresh JVM can reopen.
   If desired, change one mapping/sensitivity in Controller Settings, save, and
   repeat that control. Mappings are persistent and editable. A controller key can
   also be mapped to mouse wheel for an optional wheel test.

Useful evidence: `WINDOW_READY`, `WINDOW_FRAME`, `FRAME_DISPLAYED`, `LWJGL_KEY`,
`LWJGL_MOUSE`, `LWJGL_MOUSE_MOVE`, optional `LWJGL_WHEEL_POLL`, `WINDOW_CLOSED`,
`WINDOW_PROBE_PASS` and child exit/accepted 0. `queued=true` alone only confirms
Android transport, not consumption by LWJGL. Report what you saw as well as logs.

## Report 2: actual Wurm client startup

Only proceed after the window test ends. If it fails, send report 1 first.

1. In **0.10.0 → Client tab**, import the complete client ZIP described above.
   Wait for successful import. Save your desired local player name (default Thor).
2. Open the existing **0.6.0 server app** and start Adventure on TCP **3724**.
   Wait until it is running. Return to **0.10.0 → Client tab → Start Local Game**.
   The local-start operation sees the existing loopback port, then runs the client
   inventory/Steam checks and actual direct game entry. If the server is instead
   imported into 0.10.0, the same button can start its managed server and wait for
   the port; no second server import is necessary when using working 0.6.0.
3. The window viewer opens. Watch for a Wurm image or the precise next startup
   exception. The app now attempts the source-built window backend ahead of the
   client's desktop LWJGL classes. It does not run the JavaFX launcher.
4. If a Wurm screen appears, try the controls. Record exactly what appears: window,
   menu/login, connection attempt, or world. A reachable TCP port alone is not a
   successful Wurm connection. This preview still has a **two-minute client stage
   limit**; any timeout is recorded and only the owned client child is terminated.
5. Return to the Client tab and export **Client Report** again. Send this as
   **report 2: Wurm startup**. If it crashes before a frame, the report is still the
   desired result: it includes the classpath, bootstrap stack trace, exit status
   and available HotSpot crash details. The server retains its separate controls.

These are **Client Reports**, not server Session, Storage or World reports.
No Termux commands are needed on the Thor for either test.

## What is and is not complete

This implements a bounded **Gate 4 window/input substep** and connects it to Gate
2's real Wurm launch attempt. Host Mesa testing creates the real LWJGL Display,
verifies GL4ES framebuffer pixels, receives keys/buttons/motion/wheel through
LWJGL queues, releases held input and tears down cleanly. Kotlin tests cover the
continuing frame reader; existing import/controller/server tests remain intact.
The Thor still must qualify this new layer. Full Wurm rendering/audio, actual
login dispatch, local auth-ticket acceptance and world entry are **not claimed**.

The backend uses one context on its owning game thread and a diagnostic pbuffer,
16..1024 pixels per dimension. Captures are limited to five per second and shown
by the Android frame viewer; this is not a final smooth Surface renderer. Shared
contexts/thread transfers, full desktop window management, text input and native
audio are not implemented here. The imported client's AWT/OpenAL/GL requirements
may expose the next blocker; startup is attempted and failures stay visible.
The Pojav Cacio mouse hook is optional and logged as absent; the GLFW input path
is active without it. Controller profiles generate keyboard/mouse actions, not
Steam browsing or Android gamepad emulation inside Wurm.

A host shutdown crash with pending fixed-function GL4ES work was reproduced and
resolved in our adapter by flushing/finishing GL work before closing EGL/GL4ES.
No GL4ES upstream source was modified. Host evidence is not a Thor acceptance.

## Build and source

The normal workflow still builds all three variants and runs tests/lint. For the
managed APK use the existing build environment and `bash ./gradlew
:app:assembleManagedPreview` (one command). `prepareClientGraphics` verifies pinned
LWJGL, GL4ES, libffi and Pojav archives and builds ARM64 libraries with NDK
26.1.10909125. It compiles only selected public Pojav Java sources; no bundled
upstream binary JARs or Wurm files are build inputs. The generated API uses Java
8 bytecode; the owned window/bootstrap helpers use Java 17.

Optional real host test: after building the assets and source-matched host
JNI/GL4ES libraries with Mesa, set `WURM_HOST_GRAPHICS` to that private host folder
and run `python3 scripts/test-window-host.py`. On x86_64 the LWJGL libffi JNI must
use the matching `X86_64` ABI macro; ARM64 builds use the ARM64 target headers.
The test asserts real pixels and keyboard/mouse/wheel/reset/teardown markers.
Standard asset/parser tests need no GPU or proprietary game files:
`python3 -m unittest discover -s tests -v`.

The APK's manifest records source/native/asset hashes. Releases carry the pinned
Pojav archive, build adapter, LWJGL/GL4ES/libffi sources, input parser and complete
license notices in `Graphics-corresponding-source.tar.gz`. Source changes can be
rebuilt and the APK re-signed; no upstream checkout is edited by the build.

## Files changed in 0.10.0

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish the 0.10.0 APK and matching Pojav/window source. |
| `README.md` | Link the current milestone and device instructions. |
| `app/build.gradle.kts` | Version/package 14 / 0.10.0 / clientwindow; track new build inputs. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Window-test button and frame viewer for real client launch. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientService.kt` | Allow the owned window-test operation. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Window/API classpath, input pipe, runtime properties, outcome/report diagnostics. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ControllerCapture.kt` | Reusable focus-scoped physical controller capture for JVM frames. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsFrame.kt` | Accept continuing positive frame sequences while retaining size/data bounds. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsRuntime.kt` | Verify/install the added window JAR. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Interactive window and Wurm viewer with controller input and finish/retry controls. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the new APK version; server logic unchanged. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/GraphicsFrameTest.kt` | Test continuing sequence acceptance and invalid frame rejection. |
| `docs/CLIENT_INTEGRATION.md` | Current architecture, reusable components, gate evidence and limits. |
| `docs/CLIENT_THOR_TEST.md` | Direct readers to current test instructions; retain previous evidence. |
| `docs/GRAPHICS_THOR_TEST.md` | Exact install/import/test/report steps and this file inventory. |
| `docs/IMPLEMENTATION_PLAN.md` | Record Thor graphics pass and next implemented window/input gate. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and two-report test checklist. |
| `graphics-compat/README.md` | Explain current packaged adapter and historical API audit. |
| `graphics-compat/licenses/Apache-2.0.txt` | Complete license for reused Android Java utility sources. |
| `graphics-compat/licenses/GPL-3.0.txt` | Complete GPL text incorporated by the upstream LGPL notice. |
| `graphics-compat/native-sources.json` | Pin the inspected Pojav Java GLFW archive by revision and SHA-256. |
| `graphics-compat/window/org/lwjgl/glfw/CallbackBridge.java` | Owned cursor-grab/local-clipboard hooks for adapted GLFW. |
| `graphics-compat/window/wurm/graphics/WindowBackend.java` | EGL window/frame transport and real LWJGL input queue adapter; reset/teardown. |
| `graphics-compat/window/wurm/graphics/WindowProbe.java` | Interactive fixed-function window and keyboard/mouse diagnostic. |
| `scripts/build-lwjgl-api.py` | Correct eight-button/wheel polling and label optional Cacio mouse absence. |
| `scripts/build-window-api.py` | Compile selected pinned Pojav sources with owned platform adaptations. |
| `scripts/prepare-client-graphics.py` | Build/package window JAR, source identity and complete notices. |
| `scripts/test-window-host.py` | Optional real Mesa pixel/input/reset/teardown regression test. |
| `scripts/verify-managed-apk.py` | Verify packaged window classes/license and absence of Wurm providers. |
