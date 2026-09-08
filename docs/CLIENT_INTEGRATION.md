# Client integration architecture and qualification

This is the 0.7.0 **import/bootstrap/controller diagnostic milestone**. It does
not deliver a playable Wurm client. The server's 0.6.0 physical pass remains the
baseline; no proprietary files or server SQL/POC changes are part of this work.

## What was verified before implementation

Read `poc/README.md`, the complete `AndroidServerMain.java` and `SteamServerApi.java`,
and the base64 artifact; inspected the Android variants, runtime installer,
import/working-copy stores, native runner, services, UI, reports, tests and history.
The POC is source-backed and remains packaged exactly as before. Its server Steam
shim is not a client API, and the client's classpath must not include that POC JAR.

The checked-out history contains the client document/settings scaffold, not the
proprietary client implementation or the precise `WurmClientBase.launch` ABI.
Public mod-loader source at
[`ago1024/WurmClientModLauncher`, commit 82d9bd8d](https://github.com/ago1024/WurmClientModLauncher/tree/82d9bd8d927d48ab8c1552a3f7f89c80b17a3fb4)
references `launcherfx.WurmMain`, hooks `WurmClientBase.runGameLoop()V`, and lists
LWJGL 2.9.1, its utilities and `SteamClientJni.jar`. This corroborates the launcher
and engine classes and desktop dependencies. It does **not** establish the
parameter contract of the user's client version. The imported JAR report is the
source of truth for that ABI. Class metadata inspection runs without class
initialization, so missing JavaFX/native dependencies cannot hide the signatures.

## Gate accounting

| Gate | Implemented in this APK | Still required for acceptance |
| --- | --- | --- |
| 1 — Import | Transactional ZIP import, required layout/class providers, JAR hashes, asset candidates, persisted inventory | Thor import pass; complete assets verified by real startup |
| 2 — JVM/bootstrap | APK JRE child, explicit classpath/cwd, ABI inventory, actual engine class initialization; invoke `public static launch()` if present; independent LWJGL attempt; timeout/exit/crash capture | Exact adapter for the observed parameterized launch API; game launch/login UI |
| 3 — Offline Steam | Client JNI ABI/field inventory and explicit `STEAM_SHIM not-installed`; tested server shim retained separately | Implement only observed client local init/identity/ticket path; prove ticket acceptance |
| 4 — Graphics/input | Android GLES2 surface/GPU report; reusable persisted mapper; physical events → LWJGL2-style numeric keyboard/mouse IPC → real JVM diagnostic receiver | LWJGLX/native window/GL backend integration, audio/JInput coverage, Wurm frames and gameplay input |
| 5 — Local connection | Start Local Game waits for local TCP 3724, starting this package's managed server if necessary | Pass endpoint through the actual Wurm launch API; Wurm login and world entry |

Gate 1's basic importer is complete in code; other gates are partial or blocked
as listed. Host tests do not establish any Thor client/graphics/Steam pass.
`ENTRY_INITIALIZED`, `DISPLAY_CREATED`, `ANDROID_SURFACE_CREATED`, `INPUT_RECEIVED`
and `TCP_PROBE reachable=true` each describe different observations. None means
the player reached the world. A final successful test needs actual game frames,
local login/world evidence and visible controller-driven gameplay.

## Process, storage and bootstrap contract

The managed variant includes the existing source-built Android ARM64 Java
17.0.20 runtime. `runtime-probe/src/client/*.java` builds into the existing Java
17 helper asset; these classes are never sent to D8 and require no Wurm build
dependency. `ClientService` owns the client operation independently of
`ManagedServerService`. It uses a foreground notification, bounded wake lock,
client operation lock and a separate native process lock. The unchanged runner
kills its child on parent death. Stop affects only the owned client child;
client failure or timeout leaves a running server under its own controls.

`managed-client/<generation>/payload` contains the working client import;
`current` atomically points at the accepted generation. Path traversal, duplicate
entries, excessive entry counts/expanded size and missing/duplicate core class
providers reject the import. A failed import leaves the previous pointer/data.
Replacement can temporarily require both old and new expanded copies. Keep the
original ZIP as your source copy; client settings written in its working directory
are not promised to survive replacing that import. A separate `managed-client/user`
is Java's user home. No world database is shared with the client importer.

The classpath contains the handwritten helper first, the discovered engine JAR,
client/common JARs, and explicit imported root/lib JAR paths. Java native search
paths contain APK-installed Android JRE libraries only. Imported Windows/Linux
native libraries and desktop Java executables are not launched. All JAR hashes
are checked again before startup. Server POC/shim JARs are not added.

Start runs three independent subprocesses: `inventory`, `entry`, `graphics`.
Each has a 1 GiB maximum heap and a two-minute limit. Exceptions exit 42 with the
actual type, stage and stack; native crashes record exit code and available
`hs_err` header. Stop can terminate incomplete client initialization. This is a
bounded startup experiment, not yet a long-lived playable client session.

The entry probe bypasses `WurmMain`, initializes `WurmClientBase` and resolves its
launch methods. It invokes a verified public static zero-argument launch method
if one exists. Otherwise it reports `ENTRY_ABI_REQUIRED`; it does not populate an
unknown method with nulls, invented booleans or credentials. Inventory has already
printed JVM method descriptors before this linkage attempt. The graphics child
independently calls the imported `org.lwjgl.opengl.Display.create()`, so an engine
failure does not conceal the native/graphics blocker.

`wurm.client.host`/`port` JVM properties identify the intended endpoint for the
future adapter. **They are not claimed to be properties Wurm consumes.** TCP
readiness is an actual loopback socket test, not Steam browsing or Wurm login.
Start Local Game can use the separately installed 0.6.0 server on the same Thor.
If no listener exists, this package needs its own server import and normal server
settings with port 3724. It waits up to three minutes and reports server startup
failure. It never changes imported INIs or starts a second server over a listener.

## Reusable graphics components: investigation and integration decision

The following are inspected upstream candidates, **not included libraries in
0.7.0**. No opaque Pojav APK binaries were copied. Their Android compatibility
work is the intended starting point rather than writing a desktop GL stack.

| Component/source examined | Reusable functionality | Integration limits |
| --- | --- | --- |
| [Pojav LWJGL fork, 39272d4d](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237) | Modified LWJGL3 with LWJGLX exposing legacy LWJGL2 APIs; documented ARM64/NDK build | Compare the imported Wurm API requirements, utilities and natives with this adapter. Stock LWJGL3 alone is not a LWJGL2 replacement. Root license is BSD 3-clause; preserve dependency notices too. |
| [Pojav native host, b12ad048](https://github.com/PojavLauncherTeam/PojavLauncher/tree/b12ad048157b3aa255d078c235dd4571e1900309) | `egl_bridge.c` obtains `ANativeWindow` from Android Surface; `input_bridge_v3.c` and Java `CallbackBridge` connect window/input callbacks to the runtime VM; existing GL4ES/Zink backend plumbing | Depends on Pojav native environment, thread/JNI attachment and GLFW callbacks. Root LICENSE is LGPLv3. Package corresponding source/notices and satisfy applicable linking requirements when incorporating it. |
| [GL4ES](https://github.com/ptitSeb/gl4es) | Desktop GL 1.5/2.x translation to GLES, including Android support | Candidate first backend, not proof of Wurm shader/extension compatibility. Upstream documents feature limitations. Pin a tested source revision/backend, preserve MIT notices and qualify Wurm frames on Thor. |

The Pojav fork README explicitly integrates LWJGLX for older LWJGL2-based games.
It therefore offers a practical candidate API layer. Its successful use with
Minecraft does not prove Wurm's usage is covered. Pojav's native surface path uses
process-local handles and runtime JavaVM pointers. **An `ANativeWindow*` cannot be
passed as a numeric pointer from this Android app into its exec child.**

The preferred next experiment, inferred from those interfaces, is a separate
Android `:client` process hosting the Surface, an ART/OpenJDK JNI bridge and the
Pojav-derived window/input layer. Keep the working server exec model. Verify the
dual-VM/JRE library loading/Android namespace behavior before moving the Wurm
engine into it. An alternative is real Surface/Binder IPC to an exec render child,
which needs a different host adapter; a raw pointer or stdin event stream does
not solve graphics ownership. Stock LWJGL2 X11/GLX Linux natives cannot run against
Android's Bionic/EGL as-is. Software rendering still needs window/API adaptation.

Concrete follow-up after the Thor report:

1. Compare exact Wurm class/method/native requirements against pinned LWJGLX and
   its GLFW implementation. Resolve missing methods before shadowing imported
   LWJGL JARs. Preserve original client JAR bytes and record adapter classpath order.
2. Build a minimal source-pinned ARM64 render-host/native bridge and compatible
   LWJGLX/GLFW artifacts in CI, with full source/notices. Prove clear/swap/resize,
   surface destruction/recreation and isolated client exit with the server running.
3. Qualify GL4ES first; test required GL versions, extensions, shaders and buffers
   on the Thor GPU. Evaluate the Pojav Zink path only against a demonstrated GL4ES
   gap and working Vulkan driver, not as a blanket assumption.
4. Connect the mapper's desktop events to LWJGLX keyboard/mouse queues or the
   GLFW callbacks. Translate LWJGL2 key numbers to GLFW numbers at that boundary;
   they are different encodings. Implement cursor grab, absolute positioning,
   resize scaling, text/character input and focus release for actual Wurm UI.
5. Address observed OpenAL/JInput calls with Android-compatible backends, then
   implement the exact direct Wurm launch adapter and minimal local Steam shim.
   Log synthetic identity/shim mode without exposing auth tickets. Test login,
   world frames and input before claiming any of those gates passed.

## Controller contract

The Android adapter uses standard gamepad key events and joystick motion ranges,
including Z/RZ versus RX/RY right-stick layouts, hat D-pad events and trigger
LTRIGGER/RTRIGGER or BRAKE/GAS values. It logs detected names/IDs and axis ranges.
See [Android controller input documentation](https://developer.android.com/games/sdk/game-controller/controller-input).

`ControllerMapping` has no Android dependency. A persistent profile maps named
physical inputs to LWJGL2 key codes, mouse buttons, wheel or unbound. Stick
sensitivity/threshold, right-stick pixels/second, dead zone and invert-Y are
editable. A+RT can share left-click without either release cancelling the other.
Axis/key duplicate sources share held state; movement has release hysteresis.
Right-stick motion is ticked continuously even while Android axes remain steady.
Focus loss/pause resets held state; hot unplug releases that device's inputs.
Settings take effect when reopening the input test. Android-reserved system
buttons may never be delivered to the app.

The version-1 transport is newline-delimited `KEY code down`, `BUTTON code down`,
`MOVE dx dy` (desktop positive Y up), `WHEEL amount` and `RESET`. The bounded queue
resets on overflow. Java validates ranges and finite deltas and releases on EOF.
The diagnostic is capped at ten minutes. It reports `sink=diagnostic`; the screen's
cursor and native GLES background are test visuals, not Wurm frames. Capture is
limited to the owned test screen; no system-wide input injection or root is used.

The default profile is WASD left stick, mouse right stick, A/RT left click, LT
right click, B/Start Escape, X E, Y I, LB Shift, RB Ctrl, L3 Space, R3 F, D-pad
1–4, Select Tab. Aux 1–16 and Mode start unbound. All these bindings are editable.

## Verification and changed files

Host verification: managed Kotlin compilation/JUnit, new Java fixture tests and
the existing server/native/storage suite. CI builds, tests and lints debug,
JVM-probe and managed variants; release verification checks exact POC/runtime
pins, Java 17 client helper classes and APK signing. No proprietary fixture is
used. Physical acceptance is the separate [Thor procedure](CLIENT_THOR_TEST.md).

Paths below are relative to the repository; `managed/` means
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/` and `testsK/`
means `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/`.

| File changed | Purpose |
| --- | --- |
| `managed/ClientStore.kt` | Client ZIP validation, transactional publication, hashes and inventory |
| `managed/ClientSession.kt` | Own client process, bootstrap/local orchestration, input IPC, persistent logs/report |
| `managed/ClientService.kt` | Independent foreground lifecycle, notification and Stop Client |
| `managed/ClientActivity.kt` | Client import/start/local/stop/status/export and settings/test navigation |
| `managed/ControllerMapping.kt` | Pure reusable mapper and atomic persistent editable profile |
| `managed/ControllerSettingsActivity.kt` | Detected controller, mappings, sensitivities, dead zone, invert and defaults |
| `managed/ControllerTestActivity.kt` | Android physical input adapter, focus/unplug handling and diagnostic cursor |
| `managed/ClientSurfaceProbe.kt` | GLES context/surface/GPU lifecycle diagnostics |
| `managed/ManagedActivity.kt` | Route Client tab to the new managed client screen |
| `app/src/managedPreview/AndroidManifest.xml` | Register private client activities and service |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeRuntime.kt` | Serialize shared installation; retain already-correct native links while another JVM runs |
| `runtime-probe/src/client/ClientBootstrap.java` | API/entry/Steam/LWJGL probes and explicit failures |
| `runtime-probe/src/client/ClassInventory.java` | Bounded class-file API metadata parser without dependency resolution |
| `runtime-probe/src/client/DesktopInput.java` | Versioned desktop event validation and JVM diagnostic receiver |
| `testsK/ClientStoreTest.kt` | Valid import/reopen/hash and failed import preservation/path/duplicate/size cases |
| `testsK/ControllerMappingTest.kt` | Shared presses, sticks, repeated events, reset/unplug, settings persistence and invalid input |
| `tests/test_client_bootstrap.py` | Metadata without missing dependencies, direct call, native failure, corrupt class and input protocol fixtures |
| `app/build.gradle.kts` | Version 0.7.0/code 10 and separate `.clientpreview` package |
| `scripts/verify-managed-apk.py` | Verify client helper class files and absence of proprietary client asset |
| `.github/workflows/android.yml` | Publish immutable client preview APK and matching runtime sources/guides after passing CI |
| `docs/IMPLEMENTATION_PLAN.md` | Client-first architecture and gate plan |
| `docs/CLIENT_INTEGRATION.md` | This architecture, reuse investigation and file-by-file accounting |
| `docs/CLIENT_THOR_TEST.md` | Exact import/install/test/report instructions |
| `docs/RELEASE_CLIENT_PREVIEW.md` | Honest release scope and installation notes |
| `README.md` | Current milestone, APK and test-guide links |
