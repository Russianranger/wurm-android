# Client integration architecture and qualification

## 0.8.0: direct launch, local compatibility and automatic receiver

This preview fixes the inaccessible separate receiver-start workflow and implements
the observed client launch/Steam contracts. It is not yet playable. The server's
0.6.0 physical pass, POC artifact, SQLite patches and managed runtime remain intact.

The user supplied the exact client JAR identified by the Thor report: SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
Private inspection verified these APIs; only handwritten adapters and API signatures
are committed. The supplied JAR, its disassembly and game implementation stay out
of Git and release assets. Prior source/history and upstream research are retained
in the sections below and [THOR_CLIENT_FINDINGS.md](THOR_CLIENT_FINDINGS.md).

**Latest physical evidence:** [0.8.0 controller transport, imported Steam
handler/ticket construction, resource JAR checks and loopback TCP passed](THOR_CLIENT_080_PASS.md).
The client fails before login while Profile loads native LWJGL. The next
source milestone compiles/audits the pinned Pojav Java API and restores the four
missing member signatures and two class names with handwritten adapters. See
[graphics-compat](../graphics-compat/README.md) for 317/317 scoped member coverage,
reproduction, test boundaries and source/license requirements. These developer
artifacts are not yet APK assets; the native render host is the next executable gate.

| Gate | Implemented/evidence | Still required |
| --- | --- | --- |
| 1 — Import | Thor import passed; class providers/hashes and sound/pmk/graphics resource JAR checks passed | Real resource completeness during gameplay |
| 2 — JVM/bootstrap | Thor JVM reaches real Profile initialization; exact native failure exported | LWJGL loads during Profile setup, before launch can be invoked |
| 3 — Local Steam | Actual supplied SteamHandler initialization and SteamAuthTicket creation passed on Thor (compat exit 0) | Server acceptance of the synthetic local ticket |
| 4 — Graphics/input | Thor INPUT_READY/INPUT_RECEIVED; Android GLES probe; pinned public Java API with adapters passes scoped static audit | Native LWJGL/window/OpenGL/audio bridge, display queries and gameplay input |
| 5 — Local connection | Thor TCP loopback probe passed; engine-facing accessors provide loopback target | Protocol authentication, login, rendered world |

A successful `STEAM_COMPAT_OK` means the actual imported Java handler/ticket ABI
worked against our local shim. It does not mean Steam authenticated a user or the
server accepted the ticket. Likewise `INPUT_RECEIVED` is a diagnostic JVM delivery,
not Wurm input. No code sets a successful login/world state without evidence.

### Packaging and process boundaries

`runtime-probe/src/client` remains an ordinary Java 17 asset, separate from D8.
`client-compat/src` builds against handwritten `client-compat/stubs` API signatures.
Only five authored class files are packaged into `client-compat.jar`; the APK
verification script checks the exact class set. The real console, SteamHandler,
SteamAuthTicket, Profile and Resources must come from the user's client.
See [client-compat/README.md](../client-compat/README.md).

The unchanged ARM64 runtime/runner owns separate bounded exec children:
`inventory`, `compat`, `entry`, `graphics`. Each allows 1 GiB and two minutes;
input allows ten minutes. Each failure is exported without suppressing the later
independent graphics probe. Client Stop affects only its child and lock. The
server keeps its own service, process, world, POC and SQL compatibility.

Inventory/graphics classpath: helper, discovered engine, client/common, explicit
imported root/lib JARs. Compat/entry prepend `client-compat.jar`. Server processes
never add that asset. Android JRE/APK libraries are the native search path;
imported desktop native libraries and desktop Java executables are not launched.
The client helper now uses `java.awt.headless=true`, matching the packaged JRE.
This removes the forced X11 AWT mode; it does not supply LWJGL native rendering.

### Actual launch and resource contract

The known method is `launch(Profile.PlayerProfile, Resources, boolean)`.
The desktop path passes `false`; that parameter is not read inside this method.
The adapter uses the real Profile singleton/factory, loads the saved Android
player name (default Thor), associates/stores its config, checks option version,
then obtains `launchProfile()`. Passwords are blank in this preview; passworded
accounts/servers need a later credential flow after native startup is qualified.

The adapter checks top-level non-test JARs in the actual pack directory, using
sound.jar, pmk.jar, graphics.jar first if present, then other names alphabetically.
It validates ZIP structure/nonempty entries and logs names/size/count, without
fetching the desktop update service. These are resource archives, not Java
classpath entries. At least one is required; the engine establishes whether their
contents are sufficient. It constructs `Resources(File,List)` with that list.

The engine still calls WurmMain utility methods even when the JavaFX UI is bypassed.
Our replacement provides imported client flags, its actual console stream, bounded
console-file/recent-log output, and local endpoint getters. The real game remains
unchanged. The adapter sets the observed username/password/window APIs, invokes
launch and waits on its `gameThread` field. Thread failure is logged with stack
and exit 42. If the engine catches a failure itself, its log remains evidence;
a normal thread return alone never means login succeeded.

On the host, the actual supplied JAR's Steam handler and ticket pass. Its Profile
setup then loads Options → DisplayOption → DisplayDevice → LWJGL Sys/Display and
hits missing native lwjgl. This occurs before the real launch invocation. Thor
must supply its own next native result; Android GLES success is not desktop
LWJGL/OpenGL success. Later LwjglClient paths also query AWT screen devices and
Swing window sizing, which the render adapter must address.

### Local Steam and connection

`SteamJni.Steam_api` uses the exact instance constructor and ticket return type.
It does not load SteamClientJni. It stores a local synthetic numeric identity in
Java user home, returns a marked synthetic byte payload in the real imported
SteamAuthTicket, and rejects activation unless explicit offline mode and
127.0.0.1:3724 are set. No real Steam tickets, browsing or cloud stats are supplied.
Typed ClientHooks calls avoid broad reflection resolving SteamHandler's unused
JavaFX browser parameter classes. This failure was reproduced and fixed on host.

The game itself reads WurmMain endpoint getters during performConnection, requests
a ticket from SteamHandler, sends its normal ticket packet, waits for auth then
logs in. No custom wire-protocol imitation is introduced. Ticket acceptance by
the personal server remains untested because native graphics currently blocks
startup. Start Local Game uses an existing listener (including the old 0.6.0 app)
or starts this package's imported server and waits up to three minutes.

### Storage and controller test

The existing transactional managed-client generation and import limits remain.
The user's ZIP must contain client.jar, common.jar, lib and all resource folders.
A failed import preserves the old pointer. PlayerFiles written under the import
root survive app reopening; replacing the import does not migrate those files.
Java user home, synthetic identity and Android player/controller preferences live
outside the import generation. No server database is shared with client storage.

Client tab → **Start Controller Test** opens the diagnostic and requests receiver
startup once. A running client operation is not replaced: wait or stop it and use
Retry Receiver. Touch controls are at the top in a horizontal scrolling row.
READY plus INPUT_RECEIVED is required; queued=true only means the app accepted an
event for transport. Focus loss and controller removal still release held input.
Controller Settings retain per-button actions, sticks/dead zone/invert-Y, mouse
bindings and restore defaults. Physical gameplay integration awaits the renderer.

## Reusable graphics components: investigation and integration decision

The following are inspected upstream candidates, **not included libraries in
0.8.0**. No opaque Pojav APK binaries were copied. Their Android compatibility
work is the intended starting point rather than writing a desktop GL stack.

| Component/source examined | Reusable functionality | Integration limits |
| --- | --- | --- |
| [Pojav LWJGL fork, 39272d4d](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237) | Modified LWJGL3 with LWJGLX already under org.lwjgl; public Java source now compiled and audited, with our four missing signature adapters | 317/317 scoped member signatures covered; matching native build, behavior and unscanned dependencies remain. Stock LWJGL3 alone is not a LWJGL2 replacement. Root license is BSD 3-clause; preserve dependency notices too. |
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

1. **Java API step implemented:** compile the pinned public fork and compare
   supplied Wurm class/member references. Four missing members and two class
   names now have adapters; seven fixture tests cover audit/wrapper contracts.
   This is static coverage only. Native procedures, dynamic calls and dependencies
   outside the scanned Wurm classes remain. Do not add the candidate to the APK
   until matching native/window support is available. Preserve imported JAR bytes.
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
   qualify the implemented direct Wurm launch adapter and minimal local Steam shim.
   Log shim mode without exposing ticket data. Test login,
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

## Verification and every file changed in 0.8.0

Local verification: 14 managed Kotlin/JUnit tests and 41 Python tests, including
seven new compatibility contracts. The managed Android sources compile. The
private real-JAR host check passes local SteamHandler/ticket compatibility;
it does not establish a physical gameplay pass. CI builds/tests/lints all three
variants, verifies the original runtime/POC pins and the exact five-class adapter
asset, and verifies APK signing before publishing. The public tests use only
handwritten fixtures and existing public JDBC test dependencies.

Paths below are relative to the repository. `managed/` abbreviates
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.

| File changed | Purpose |
| --- | --- |
| `managed/ClientActivity.kt` | Direct Start Controller Test action, version heading and saved local player name |
| `managed/ControllerTestActivity.kt` | Automatic receiver request, visible touch Exit/Stop/Retry row |
| `managed/ClientSession.kt` | Compat asset/stage classpath isolation, player/offline arguments, versioned report and stage results |
| `runtime-probe/src/client/ClientBootstrap.java` | Dispatch verified parameterized adapter and independent compat test; retain unknown-ABI failure |
| `runtime-probe/src/client/DirectClientLaunch.java` | Real profile/resources factories, local pack validation, entry call and game-thread failure/lifetime |
| `client-compat/src/SteamJni/Steam_api.java` | Local-only observed Steam JNI ABI replacement, typed ticket creation, no browsing |
| `client-compat/src/com/wurmonline/client/launcherfx/WurmMain.java` | Engine-facing launcher utilities without JavaFX UI; console/log and endpoint support |
| `client-compat/src/wurm/android/compat/LocalSession.java` | Persistent synthetic identity, marked local ticket and explicit loopback opt-in |
| `client-compat/src/wurm/android/compat/ClientHooks.java` | Typed calls into real SteamHandler without broad JavaFX-signature reflection |
| `client-compat/stubs/com/wurmonline/client/console/ConsoleListenerClass.java` | Compile-only listener signature, never packaged |
| `client-compat/stubs/com/wurmonline/client/console/WurmConsoleOutputStream.java` | Compile-only console signature, never packaged |
| `client-compat/stubs/com/wurmonline/client/steam/SteamHandler.java` | Compile-only handler/result signatures, never packaged |
| `client-compat/stubs/com/wurmonline/client/steam/SteamAuthTicket.java` | Compile-only ticket signatures, never packaged |
| `client-compat/README.md` | Source/packaging/ABI boundaries and local compatibility limitations |
| `tests/test_client_compat.py` | Seven executable compatibility, async crash, resource, identity and packaging contracts |
| `app/build.gradle.kts` | Compile/package adapter separately; version 0.8.0/code 11 and install-alongside .clientlaunch package |
| `scripts/verify-managed-apk.py` | Exact packaged adapter class allowlist, no stubs/server contamination and new helper check |
| `.github/workflows/android.yml` | Publish new immutable v0.8.0-client-launch after existing required gates |
| `docs/IMPLEMENTATION_PLAN.md` | Verified client architecture, implemented scope and next native/device gates |
| `docs/CLIENT_INTEGRATION.md` | Actual launch/Steam contracts, preserved Pojav investigation and this file accounting |
| `docs/CLIENT_THOR_TEST.md` | Exact no-PC install, automatic receiver, owned client import, local run and Client Report steps |
| `docs/RELEASE_CLIENT_PREVIEW.md` | Release scope, side-by-side install and remaining graphics/login limits |
| `docs/THOR_CLIENT_FINDINGS.md` | Private matching-JAR findings and superseding receiver test instructions |
| `README.md` | Current APK, milestone and test links |
