# From the working POC to Wurm Server

## Current milestone: 0.10.14 local login credential

The 0.10.13 Thor server successfully used the Java 17 encoder and remained alive
until the requested stop. Its exact login message says an authenticated user
supplied incorrect credentials. The offline ticket step passed; the later login
credential comparison failed. The direct launcher supplied an empty password.
Inspection of the owner's server shows that it hashes the supplied credential
and compares it with the hash of the authenticated identity, using the same
player-name salt. An empty value cannot meet that contract.

DirectClientLaunch now supplies the existing LocalSession.identity() to the real
engine's setPassword(String), so the login credential, Steam identity and local
ticket agree. The separate server password remains empty for this unprotected
local preview. LocalSession still requires offline opt-in and 127.0.0.1:3724,
persists identity across launches and refuses corrupt identity files. No server
authentication bypass, replacement hash algorithm or new Steam feature is added.
Diagnostics identify the credential source without logging its value or ticket.

The same report records a subsequent SIGABRT in libEGL FileBlobCache destruction
after Wurm reported login rejection and closed its window. This is an unresolved
native teardown/corruption issue, not evidence that the ticket step failed.
Preserve the existing crash capture, Serial GC, graphics/input bridge, server
encoder overlay, SQLite overlays and POC. Do not declare rendering/gameplay stable.

**Next gate:** install 0.10.14, import the same two ZIPs, select Adventure/Thor,
and run this app's Start Local Game with older servers stopped. Export Client
Report and Server Session Report after the result. The credential adapter and
regression tests are implemented; Gate 5 login acceptance/world entry still need
the Thor. See [CLIENT_LOGIN_IDENTITY_FIX.md](CLIENT_LOGIN_IDENTITY_FIX.md) for
exact files, steps, evidence, validation limits and every changed file.

## Previous milestone: 0.10.13 Java 17 server login compatibility

The Thor's 0.10.12 report passes both isolated collectors, verifies Serial for
client entry, and retains 196 frames through a minute of LOGIN_WAIT without the
previous native abort. That is a successful bounded test, not proof that the
old heap corruption is permanently repaired. The paired 0.10.11 server report
identifies the current failure: NoClassDefFoundError for sun.misc.BASE64Encoder
inside LoginHandler.encrypt, followed by unrequested exit zero. Authentication
was accepted through the local shim; login and world entry were not completed.

The supplied server class uses SHA-1 over UTF-8 and the legacy encode(byte[])
ABI. A separate session-private, hash-guarded login overlay redirects one owner
to an authored Java 17 Base64 adapter. Preflight prepares it without loading
Wurm; bootstrap checks reverse integrity and classpath selection before world
initialization. Hashing, authentication, login checks and all other method bytes
remain unchanged. Preserve the existing position overlay, item fixes, POC,
client Serial GC and graphics/controller/Steam path.

The next Thor test must use **0.10.13 for both server and client**, so the encoder
fix is active and server failures are observed by the owning app. Import the same
server and full client ZIPs; no new proprietary files or manual patching is
needed. The bounded Gate 5 dependency fix is implemented; actual login/world
entry and audio still need device qualification. See
[SERVER_LOGIN_BASE64_FIX.md](SERVER_LOGIN_BASE64_FIX.md) for exact steps,
validation limits and every changed file.

## Previous milestone: 0.10.12 client GC compatibility experiment

The 0.10.11 Thor server stayed alive after the client crash and stopped on request
without the earlier position SQL errors. The client now has a PID-matched native
SIGABRT: Scudo detects a corrupted header while GC Thread#1 frees G1-related
bookkeeping. The concurrent shader query is not evidence of the original write.

The next bounded milestone selects Serial GC only for actual client entry and
verifies the real collector in the child. A no-import JVM Memory Test compares
G1 and Serial with allocation, direct buffers, JIT warmup, disposable class loaders
and reclamation. Both test results and timestamped GC logs use Client Report.
Server, runtime/native pins and the working graphics/controller path are retained.
This is an experimental workaround and runtime-isolation gate; heap corruption's
origin and actual login/world entry remain unverified. Keep the working 0.10.11
server for the next client test. See [CLIENT_GC_TEST.md](CLIENT_GC_TEST.md) for
exact Thor steps, source evidence, limitations and every changed file.

## 0.10.11 position-save SQLite fix

The paired 0.10.10 Thor reports prove the owned server exits before the client.
Its shutdown logs contain repeated MySQL position-upsert errors in SQLite; the
first fatal Throwable was displaced by console rotation. The newly supplied
server.jar confirms this remaining SQL defect while retaining the earlier item
fixes. A narrowly guarded, session-private overlay changes that one statement,
with original-hash/reverse/classpath verification before Wurm initialization.
A separate persistent first-error capture keeps early severe records available
in both Client Report and Server Session Report even after console rotation.

This is a targeted Gate 5 dependency fix, not completed login/world entry. The
initial fatal cause still needs evidence. Graphics, controller and Steam paths
are retained. Use the 0.10.10 before-start checkpoint and the same client ZIP in
the separate 0.10.11 app; keep earlier apps and data. See
[SERVER_POSITION_SQLITE_FIX.md](SERVER_POSITION_SQLITE_FIX.md) for exact no-PC
Thor steps, verified scope, limitations and every changed file.

## 0.10.10 server login trace

The paired Thor reports establish local authentication acceptance, followed by
LOGIN_WAIT with no further bytes received. The server's last session records an
unrequested exit with no timestamp. Its missing detailed logging prevents a
causal diagnosis; historical NPC errors are not attributed to this latest attempt.

The next implementation supplies a standard per-session JUL configuration before
Wurm initializes, a bounded console log handler, observational server thread/logger
snapshots on the existing control pipe, and timestamped shutdown/exit evidence.
The client requests snapshots during an owned server's login wait, reports an
owned server exit promptly, and includes this app's server session in Client Report.
The POC/Steam/SQLite/world lifecycle and client graphics/protocol paths are retained.
No login or world result is fabricated; Gate 5 login itself remains incomplete.

For this test, import a stopped server working ZIP and the same complete client ZIP
into 0.10.10, keep the older server stopped, and use Start Local Game. The exact
steps, limitations, tests and every changed file are in
[CLIENT_LOGIN_TEST.md](CLIENT_LOGIN_TEST.md). No PC, root or Termux is needed.

## 0.10.9: observe the actual local connection and remove the premature cutoff

The Thor's 0.10.8 report completes builtin material preload, GUI and terrain setup,
then reaches Connecting and produces at least 375 window frames. STAGE_TIMEOUT
precedes exit 134; this run was stopped by the app's two-minute diagnostic limit.
It does not reproduce the unexplained 0.10.7 material-preload exit, nor prove that
older issue permanently fixed. Keep the physically tested graphics path intact.

The supplied client's real performConnection path sends its Steam ticket, waits
for authentication, then sends login. It keeps some rejection/retry text only in
StartupRenderer.startupMessage; the parent console never received those messages.
A new read-only observer follows the inspected engine/connection fields, exports
that text, authentication/login flags, approximate queued/read/pending byte counts,
and a bounded game-thread stack every 15 seconds during startup. The observer
never writes game state, sends packets, consumes buffers or fabricates acceptance.
ABI mismatch is explicit and does not prevent the real client from launching.

Android now displays separate authentication, login, rejection, retry and game-
loop states. The entry startup budget is five minutes. A real authenticated,
logged-in, connected client with its startup renderer closed removes that budget;
Stop Client remains available. Other diagnostic budgets stay unchanged. An
observed game loop still requires a device screenshot/control test to establish
visible world rendering. The server's lifetime remains independent.

The actual supplied JAR sends its original 78-byte synthetic-ticket payload
(80 framed bytes) to a private loopback test socket. Its original auth parser
accepts authored denial/success fixtures, and its original login method reaches
LOGIN_WAIT. This validates observer ABI and client dispatch, not acceptance by
the real Adventure server. The next Thor test returns both Client Report and the
working server's Session Report from the same attempt. Do not change the POC or
server callback behavior before that evidence identifies the actual wait.

Gate 4 progresses through terrain preparation to the connection screen; full
scene rendering/audio remain unqualified. Gate 5 now has explicit state and
usable startup time, but real server authentication/login/world entry are pending.
See [every change, test results and exact Thor steps](CLIENT_CONNECTION_TEST.md).

## 0.10.8: capture the Thor's abrupt material-preload exit

The 0.10.7 report physically confirms truthful GL2.1/core-and-legacy capabilities,
no GL3+ claims, and Wurm's existing legacy/basic-water renderer selection. FBO and
shader-query checks pass. After the first splash frame, builtin material preload
ends with child exit **134**, without a Java exception or crash detail. A possible
SIGABRT needs native evidence; the matrix-uniform warnings alone do not prove its
cause. Full rendering and local login remain blocked in gates 4 and 5.

The next smallest milestone adds opt-in, bounded BEGIN/END/THREW breadcrumbs to
the real core GL20 delegates used for source upload, compile/link, attribute and
uniform reflection. Shader sources are identified by SHA-256, never printed.
Arguments, native functions, results and exceptions are retained; ARB JNI methods
and native source pins stay unchanged. This is targeted coverage, not tracing of
every OpenGL call. A limit marker makes the 4,096-call cap explicit.

Each Android child attempt tracks its native PID and unfinished calls. Unexpected
signal-like exits trigger a best-effort own-UID/current-time logcat capture and an
exact PID/UID/time-matched ApplicationExitInfo lookup. If Android provides a native
tombstone, a bounded parser retains only signal, abort/cause and crashing-thread
frames, skipping memory, register, map, log and file-descriptor dumps. No root,
READ_LOGS permission, signal-handler interception or other-app log fallback is
introduced. Exec children may have no OS record; that limitation is reported.
Intentional Stop and diagnostic timeout do not trigger crash collection.

The failure status now retains the exit/unfinished call when Java reports no
exception. All evidence uses the existing Export Client Report flow. The real
server, imports, offline shim, controller mappings, five-entry private overlay
and two-minute startup limit stay in place. Host probes pass with tracing, but
cannot establish the Adreno crash cause. See [exact Thor steps, verification and
every changed file](CLIENT_CRASH_DIAGNOSTIC.md).

## 0.10.7: accurate capabilities and Wurm's existing legacy renderer

The Thor's 0.10.6 report passes blur startup, native shader queries, buffer cleanup
and FBO checks. It then fails loading `material.simple` in the water-LOD Volume
constructor. Inspection traced this to a deeper mismatch: the pinned LWJGL fork's
ANGLE workaround returns true for failed function lookups and missing capability
reports. Thus `OpenGL33` is true on GL4ES's reported OpenGL 2.1 context, selecting
Wurm's modern/deferred renderer and its GLSL 3.30 materials.

Restore the computed availability in both function-table checks and false in
`reportMissing`, in generated build copies only. Every function lookup and cache
write is retained. Core and legacy capabilities are checked against the GL4ES
backend; the real initialized `GLHelper` must report deferred/instancing false.
Wurm's own `WorldRender.useAdvancedWater()` then selects basic water. No Wurm
renderer or user setting is patched, and no new shader resources are adapted.
The previous engine/buffer/blur overlay remains exactly five entries.

The private host probe reproduces the erroneous selection with the old API and
passes with the fix. It initializes GLHelper in the same order as real startup,
checks the real Volume constructor, and retains the real blur material/pixel test.
Function-cache, native window/controller/FBO/shader regressions and the 317-member
API audit cover the changed adapter. Diagnostic reports add CAPABILITY_CHECK_PASS
and WURM_RENDERER_SELECTION_PASS; both are still awaiting Thor confirmation.

Gate 4 remains in progress: Wurm splash is physically confirmed, full legacy
terrain rendering and audio are not. Gate 5 still requires actual local ticket,
login and world-entry evidence. TCP 3724 reachability is not Wurm login. Working
server/POC/SQLite behavior, import storage and controller mappings remain intact.
See [diagnosis, every changed file and exact Thor test](CLIENT_CAPABILITIES_FIX.md).

## 0.10.6: material startup after the first Wurm splash frames

The Thor's 0.10.5 report confirms the buffer preflight, FBO checks and three game
frames; the screenshot shows the actual Wurm splash. Startup then reaches
AdvancedWaterRenderer/WaterTexture, where `material.gaussblur` is null because
both GLSL 3.30 blur shaders fail compilation through the GL4ES/GLES2 path.

A separate pinned LWJGLX bug explains uniform types of zero: the GL20 legacy
query overwrites the size output with type and advances the caller's buffer.
Build copies now correct the core uniform and attribute convenience methods,
matching the already-correct ARB wrapper. An owned-context check compiles/links
an authored shader and verifies native size/type outputs, nonzero positions,
canaries and Position=0 before real material loading. No native source changes.

The existing private overlay additionally carries two SHA-verified resource
adaptations. Only GLSL syntax changes: version 330 to 120, the position input to
an attribute, fragment output to gl_FragColor, and texture to texture2D. Wurm
already binds Position to location zero. Blur weights, math and uniforms remain
unchanged. Resources must reverse byte-for-byte to their pinned originals and
be selected by the classpath; the raw import stays unchanged. This is not a
general GLSL 3.30 translator or a claim of full desktop OpenGL 3 support.

The host reproduces the original material failure. The real Wurm material and
program now load/link and draw an authored constant input to the expected pixel
through the actual imported blur shader. Core/ARB reflection and the native
window/controller regression pass. Gate 4 now has physical splash rendering;
full terrain rendering, audio, local authentication/login and world entry still
need testing. GL4ES internal matrix-uniform warnings remain visible and are not
claimed resolved. The server and POC paths are unchanged.

See [evidence, changed files and exact Thor test](CLIENT_MATERIALS_FIX.md).

## 0.10.5: Java 17 buffer cleanup after confirmed FBO support

The Thor's 0.10.4 report confirms `OFFSCREEN_FBO_PASS` and
`OFFSCREEN_REQUIREMENT_PASS`. Wurm proceeds to splash texture creation, then
`BufferUtil.deallocate` fails accessing `sun.nio.ch.DirectBuffer`. There are still
zero game frames and no login evidence. OpenAL continues to fall back to silence.

Inspection/reproduction finds two distinct issues: the non-exported package and
the old `cleaner(): sun.misc.Cleaner` ABI. Exports alone reproduce a
`NoSuchMethodError`. The two imported buffer classes require the Java 17
`jdk.internal.ref.Cleaner` owner and return descriptor.

The existing private session overlay now contains three SHA-verified classes:
the unchanged offscreen engine adapter and the two buffer classes. Only the two
exact Cleaner constants in each buffer class change. Original allocation,
accounting, attachment traversal and cleanup instructions stay intact. Every
class reverses to its pinned original hash and must be selected by the entry
classpath. Imports remain unchanged; unknown versions fail before entry.

Only the client entry JVM exports `java.base/sun.nio.ch` and
`java.base/jdk.internal.ref` to classpath code. No blanket opens, JVM downgrade,
GC-only workaround or server flags are added. A preflight exercises the real
imported byte/float/int/double allocation and cleanup methods before game launch,
with module/ABI checks and exportable report markers.

The host reproduces both original errors and passes the fix. Sixteen repeated
real-client preflights restore native direct-buffer count/bytes exactly. Authored
CI fixtures verify relocation, native memory release, missing exports and hash
rejection without proprietary inputs. Gate 4's FBO test is now physically passed;
this buffer startup correction is host-tested. Full rendering, audio, local
login/ticket acceptance and world entry remain unqualified.

See [evidence, every changed file and exact Thor test](CLIENT_BUFFERS_FIX.md).

## 0.10.4: measured FBO support for the legacy startup requirement

0.10.3 physically passed font/profile initialization and created Wurm's 960x540
window on Adreno 740. It entered firstRender but failed checkSupportLevels at
`MISSING SUPPORT: Pbuffers`. Audio initialization failed separately; Wurm chose
its existing silent sound engine and continued. No game frame/login was observed.

Inspection of the supplied client finds only one runtime Pbuffer reference: the
capability check. Its actual Offscreen class uses FBOs or a back-buffer fallback.
The pinned Pojav Pbuffer constructor/shared-context path is unsupported. Reporting
that API as supported globally would be inaccurate.

The new prepare-graphics stage generates a temporary, one-class overlay from the
user import only if the entire engine class matches the inspected SHA-256. It
redirects that single Pbuffer capability reference to an authored FBO test. Every
method body/branch and the raw import remain unchanged; reversing the relocation
must recover the exact original hash before entry. Unknown engines, invalid
overlays and wrong classpath order fail visibly. The overlay is session-private,
never packaged/committed/exported, and removed with the existing session cleanup.

The adapter requires Wurm's FBO option, live EXT framebuffer support, successful
RGBA8/depth16 allocation/completeness, a checked magenta pixel and state/resource
restoration. Other Wurm capability tests and public LWJGL Pbuffer capabilities stay
unchanged. The real client support method and its FBO allocation/readback with and
without a depth texture pass on the host. A regression exposed GL4ES saved shader
program failures after the FBO test; the documented LIBGL_NOPSA=1 option fixes
normal drawing/input afterward. No upstream native source is changed.

**Next gate:** run Start Local Game on the Thor and return Client Report. This
completes the host-tested legacy offscreen-check correction within Gate 4, not
full game rendering/audio or Gate 5 authentication/world entry. Texture-size proxy
probes, actual shaders/terrain and subsequent runtime dependencies still need
physical evidence. The working server/runtime/SQLite path is preserved.
See [diagnosis, verification, all changed files and exact test](CLIENT_OFFSCREEN_FIX.md).
Earlier entries below retain their historical qualification.

## 0.10.3: real client window initialization without desktop UI

The Thor's 0.10.2 report confirms all 20 logical font/style raster checks, real
profile/resources and entry into Wurm's main thread. Window initialization then
failed; cleanup called GLFW callback release with no window and hid the original
exception. No Wurm frame or login was observed on the Thor.

Private testing of the actual client reproduces the hidden JavaFX Stage dependency
in `LwjglClient.loadIcons()` through `WurmStage.getIconNames()`. An authored helper
returns icon resource names from the imported client without constructing JavaFX.
The next host failure is the default maximized window querying headless AWT screen
size. After profile loading, the bootstrap uses the verified real DisplayOption
setter for a fixed 960x540 non-maximized, non-fullscreen viewport within the existing
pbuffer bounds. Android's viewer scales the resulting framebuffer.

The generated Pojav Display adapter now handles destruction before creation and
repeated destruction. An authored headless error reporter replaces Wurm's desktop
crash dialog, retains the original Throwable and makes the bootstrap return 42
when the engine reports a failure internally. It does not convert crashes to success.

**Host evidence:** the actual imported LwjglClient.initWindow now decodes its icons,
creates the GL4ES window, initializes keyboard/mouse and exports a checked sample
clear. Its real error routing preserves the test exception. The existing GL4ES
triangle/controller/reset/teardown regression also passes. These are window tests,
not a rendered Wurm scene or local login. No new native backend is introduced.

**Next physical gate:** retry Start Local Game on the Thor. Wurm scene rendering,
audio, protocol login, local ticket acceptance and world entry remain unqualified.
The working managed server and SQLite fix are intact. See [diagnosis, validation,
every changed file and exact device steps](CLIENT_WINDOW_START_FIX.md).
Earlier sections retain the state of previous milestones.

## 0.10.2: configure Android fonts before Wurm HUD startup

The Thor's 0.10.1 report confirms all 78 keybindings, Profile/PlayerProfile and
three real resource packs initialized. WurmClientBase.launch was invoked; HUD
construction then failed in FontTexture at Java 2D FontMetrics with
`Fontconfig head is null`. The client had not reached login or a rendered frame.

The next narrow Gate 4 substep supplies an explicit, per-session OpenJDK logical
font mapping to readable Android `/system/fonts` files before client initialization.
It checks real glyph rasterization for all 20 logical font/style combinations,
logs file identities and root causes, and continues into the existing real client.
The actual private client FontTexture reproduces the old failure with desktop
fontconfig unavailable, then passes metrics and glyph drawing for 12 styles with
the fix. No game text class, server code, native graphics or Steam shim is replaced.

See [evidence, source references, every changed file and exact Thor test](CLIENT_FONTS_FIX.md).
Physical Android font initialization and subsequent GL/audio/login remain pending.
Earlier sections record prior milestones and their then-current qualification.

## 0.10.1: remove the remaining JavaFX profile dependency

The Thor passed 0.10.0's window/controller test with 366 frames, real LWJGL key and
mouse delivery and clean exit. Its local-client attempt reached the server and
passed inventory/Steam checks, then failed in Profile.loadSettings while loading
WurmSettingsFX → WurmStage → JavaFX Stage. The new headless settings/keybind helper
removes this observed dependency and preserves the real Profile/Options path.

Using the actual supplied client JAR, the old helper reproduced the exception and
the replacement passed profile/player construction twice with all 78 default keys
and an unchanged binding file. The next physical test is **local client startup**,
not another required triangle test. Full Wurm rendering/login remain unqualified.
See [diagnosis, implementation, changed files and exact Thor steps](CLIENT_SETTINGS_FIX.md).
The existing server, SQLite fix, Steam shim and native window/input backend remain
unchanged. Earlier milestone entries below are historical.


## 0.10.0: client window and real LWJGL input queues

The Thor's 0.9.1 report (`wurm-client-report(1).txt`, September 9, 2026) records
**two successful JVM graphics runs**, each with three verified/displayed frames,
two same-context resizes, clean teardown and exit 0. The owner saw the orange
triangle on blue. This qualifies the pbuffer graphics diagnostic, not Wurm login.

The next bounded Gate 4 implementation is now the **LWJGL Window / Input Test**
and an actual Wurm entry attempt using that same window backend. See
[exact Thor steps, limitations and changed files](GRAPHICS_THOR_TEST.md).

- Build `wurm-window.jar` from the pinned Pojav Java GLFW source plus this
  repository's platform adapter. Its desktop monitor/window queries feed the
  existing Pojav LWJGL2 `Display` implementation. The generated GLFW copy replaces
  Pojav's dual-VM `pojavexec` hooks with our already tested, single-context EGL
  pbuffer. No Android native-window pointer crosses the process boundary.
- Put this window JAR, the source-built LWJGL API and graphics helper ahead of
  the imported client's desktop bindings **only for window and entry stages**.
  Keep the raw inventory stage and existing Steam compatibility test. Start Client
  invokes the existing verified `WurmClientBase.launch(PlayerProfile,Resources,false)`
  path; it does not run JavaFX or silently skip rendering.
- Feed the existing controller protocol/parser into `GLFWInputImplementation` on
  the owning game thread. `Keyboard.next/isKeyDown` and
  `Mouse.next/isButtonDown/getDWheel` consume the real queues. Adapt the upstream
  mouse buffer to its advertised eight buttons and retain wheel poll deltas.
  Queue overflow, focus loss and device removal release held input. The same
  persistent editable profiles work in the frame viewer.
- Display actual JVM framebuffer readback in Android, at at most five captures
  per second. The test uses a 640x360 window, bounded 16..1024 dimensions and one
  context. The window test ends after 90 seconds or Finish Window Test; Wurm entry
  remains a two-minute diagnostic. This file transport is intentionally temporary,
  not a production surface or frame-rate claim. Unsupported sizes, shared contexts,
  detachment/thread transfer and graphics failures are explicit errors.
- Preserve the working managed server, POC, SQLite compatibility and offline
  Steam shim. Start Local Game still waits for TCP 127.0.0.1:3724. TCP success is
  not authentication or world entry. A separate `.clientwindow` APK package keeps
  the working server and its Adventure data installed.

**Completed in code/host tests:** Pojav-backed Display/window creation, real
fixed-function GL4ES drawing/readback, key/mouse/wheel queues, reset and clean
teardown. **Physical acceptance pending:** the new window/input layer and Wurm's
next startup stage. Headless AWT usage, OpenAL ARM64 natives, unsupported desktop
OpenGL features, display sizing, actual login dispatch and local ticket acceptance
remain specific risks to identify from the next report. Gates 1/2/3 retain their
previous import/bootstrap/Java-shim evidence; full Gate 4 Wurm rendering and Gate 5
login/world entry are not complete.

Reusable components were inspected, not rebuilt from scratch: the pinned
[Pojav Java GLFW implementation](https://github.com/PojavLauncherTeam/PojavLauncher/tree/b12ad048157b3aa255d078c235dd4571e1900309/jre_lwjgl3glfw),
[Pojav LWJGL2 Display and input](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237/modules/lwjgl/lwjglx),
and [GL4ES](https://github.com/ptitSeb/gl4es/tree/81547d986798e876de8b434193920b606a72363f).
GLFW's [null platform](https://www.glfw.org/docs/latest/intro_guide.html) was also
considered; it would introduce another native backend/context integration before
reusing the working EGL path. Full Pojav native Surface hosting remains a later
performance path; its ART/OpenJDK input/window lifecycle is not drop-in compatible
with this app's exec child. Upstream trees remain unchanged; build adapters produce
modified copies. The release includes source pins, notices, GPL/LGPL/Apache license
text and corresponding source, including the Pojav archive and authored changes.

The earlier milestone records below are historical.


## 0.9.1: isolate and correct the graphics probe failure

Report (3) from 0.9.0 confirms owned JVM/native loading, Adreno EGL, GL4ES OpenGL
2.1 and linked GLSL 1.20 shaders. The aggregate draw/readback check failed without
an error code. [Diagnosis and exact next test](GRAPHICS_THOR_TEST.md) document the
confirmed empty-shader-log request bug, its correction, strict per-operation
GL4ES/GLES checks and first-failure UI status. Host llvmpipe reproduces that log
bug and passes the corrected three-frame test; the original Thor failure's exact
cause and physical rendering acceptance still need the next device report.

0.9.1 installs separately as `.graphicsfix1` and needs no imports. This is a targeted
Gate 4 correction, not a new Wurm window/input/login milestone. Keep the working
server runtime, POC, SQLite compatibility, import and controller layers unchanged.
After a physical pass, continue with the GLFW/window host and real game input,
then direct local connection. Do not infer world entry from a diagnostic triangle.


## 0.9.0 implemented: executable native graphics gate

The [JVM Graphics Test](GRAPHICS_THOR_TEST.md) packages the pinned Pojav Java API,
its actual ARM64 core/OpenGL JNI bindings, GL4ES and a small public-NDK EGL bridge.
It uses the verified separate JVM process, an off-screen pbuffer and bounded frame
readback into Android. It exercises desktop GLSL, VBO drawing, known-pixel checks,
surface resize/swap and teardown; reports exact stages and native identities.
This isolates driver/native compatibility before taking on ART/OpenJDK hosting
and direct Android Surface ownership. It is a smaller executable gate than the
previous proposed dual-VM host, not a claim that a pbuffer supplies Wurm's window.

Only the new `render` operation receives these assets/settings. Separate library
names prevent overlap with imported LWJGL2 native lookup. Existing client import,
bootstrap, offline shim, controller test and server process/POC/SQL behavior remain
intact. Version 0.9.0 installs separately and needs no user files for this gate.

Next: qualify the rendered triangle and clean repeated runs on the Thor, then
integrate a GLFW/window/surface host and its real keyboard/mouse queues before
retrying Profile, the game thread and local authentication. Full Gate 4 rendering/
input and Gate 5 login/world entry remain incomplete. No physical graphics pass
is inferred from compilation or the earlier Android GLES diagnostic.

The earlier plan and report milestones follow for context.

## Current priority: native client graphics after the 0.8.0 Thor test

[The two new Thor reports](THOR_CLIENT_080_PASS.md) qualify controller delivery
to the diagnostic JVM, actual Java Steam handler/ticket compatibility, resource
JAR validation and TCP reachability. The client fails during Profile display
initialization with `no lwjgl in java.library.path`, before login. No repeated
import or server change is needed to explain this failure.

**Next smallest source milestone implemented:** [graphics-compat](../graphics-compat/README.md)
builds the pinned Pojav Java candidate, audits the supplied client's symbolic
references and adds the four missing method signatures/two missing class names.
All 38 scoped classes and 317 members are covered. This is not native linkage or
rendering; the candidate is not packaged and the APK version is unchanged.

The user's matching `client.jar` has now been inspected privately. It is not an
opaque dependency: the actual launch/profile/resource, connection and client Steam
contracts are documented in [CLIENT_INTEGRATION.md](CLIENT_INTEGRATION.md) and
[THOR_CLIENT_FINDINGS.md](THOR_CLIENT_FINDINGS.md). No proprietary implementation
is added to the repository. The target remains **server → Wurm client → local
login → rendered world → controller gameplay**, entirely on the AYN Thor.

1. **Packaging/import.** Keep the exact source-pinned server POC and runtime.
   Continue transactional client ZIP import, class-provider validation and JAR
   hashes. Compile the direct bootstrap into `runtime-probe.jar`. Compile the
   handwritten client utility/Steam replacements into a separate `client-compat.jar`
   using API-only signature stubs; exclude those stubs from all packaged JARs.
   Only compat/entry children get this asset ahead of the imported client JAR.
   It must never shadow the working server shim.
2. **Direct launch.** Use the verified `Profile.getProfile()`, `loadPlayer`,
   `associateConfig`, `storeConfig`, `launchProfile` and
   `Resources(File,List<String>)` calls. Select and log local resource packs.
   Call `WurmClientBase.launch(PlayerProfile,Resources,false)` after setting
   username/password/target through the observed APIs. `false` matches the desktop
   call and is unused in this method; it is not an offline switch. Wait for the
   spawned game thread instead of exiting when launch returns. Unknown signatures
   still fail explicitly. Profile setup itself currently reaches native LWJGL.
3. **Local Steam.** Replace `SteamJni.Steam_api`, retain the user's actual
   `SteamHandler` and `SteamAuthTicket`, and replace only the JavaFX launcher's
   engine-facing utility surface. Use typed calls to avoid resolving unrelated
   JavaFX signatures. Persist a synthetic local identity; make bounded synthetic
   ticket data explicitly scoped to `127.0.0.1:3724`. No browsing/full Steamworks.
   Host and Thor compatibility with the real JAR pass; personal-server ticket acceptance
   requires the real connection path and remains unverified.
4. **Graphics/input.** Keep the Pojav LWJGLX/GLFW + native Surface/dual-VM + GL4ES
   investigation and license/source obligations. Next build a source-pinned
   isolated Android render host; an exec child cannot consume another process's
   raw `ANativeWindow*`. Qualify native context/swap and Wurm's desktop display
   queries before claiming rendering. Preserve the reusable mapper. **Start
   Controller Test** starts its JVM diagnostic receiver automatically. The Thor
   report now contains `INPUT_READY` and `INPUT_RECEIVED`; diagnostic transport
   passed. Gameplay input still needs the renderer's keyboard/mouse queue adapter.
5. **UI and local connection.** Client tab has import/start/local-start/stop,
   editable persisted player name, controller settings/test and Client Report
   export. Default player is Thor with blank passwords in this preview. Start
   Local Game reuses a local listener or starts this package's managed server,
   waits for TCP 3724, then attempts the client. Engine-facing WurmMain accessors
   provide the loopback target; no Steam browser is started. The original server,
   SQL compatibility, world selection/configuration and stop controls are intact.
6. **Completed code milestone.** Gate 1 core import and resource JAR checks;
   Gate 2 verified parameterized launch adapter and detailed failures; Gate 3
   local shim with real-JAR Thor compatibility; Gate 4 diagnostic receiver delivery
   passed on Thor, and a compiled Pojav Java API candidate passes static coverage;
   Gate 5 local orchestration and endpoint handoff. These are implementation
   milestones, not a full Gate 2 game launch, Gate 3 server-auth or Gate 4/5 gameplay pass.
7. **Next physical test.** The 0.8.0 procedure is complete; retain its import and
   the working 0.6.0 server. First implement the isolated native render host,
   matching ARM64 LWJGL/GLFW libraries and pinned GL4ES backend. The next APK must
   prove a JVM-created context, clear/swap/resize and surface recreation before
   the Wurm retry. Native desktop window sizing, OpenAL/JInput, dual-VM lifecycle,
   complete resources, memory with server, ticket acceptance and world entry
   remain. No new copy/install/run is needed for the current reports. The next
   APK's device procedure must need no PC, Termux, root or separate Java installation.

[CLIENT_THOR_TEST.md](CLIENT_THOR_TEST.md) has the exact procedure and markers.

The following sections retain the server milestones and evidence.

**Latest device result: 0.6.0 observation passed.** The user confirmed the report
survived closing/reopening. The child observed five Adventure maps, eight open
databases with WAL/SHM under `localhost/sqlite`, and owned TCP listeners on 3724
and 48020; all nine databases were logged at startup. Proc inspection succeeded
on this Thor. Normal Stop exited 0. See [THOR_WORLD_PASS.md](THOR_WORLD_PASS.md).
The additional port's role and which identical INI candidate was loaded remain
unknown. The current next step is the in-app client integration above, followed
by a specific gameplay/item change verified across restart.

**Current implemented milestone: 0.6.0 world/configuration observation.**
The existing controller now records candidate configuration identities, POC
GameFolder and JDBC log evidence, loopback readiness and a best-effort snapshot
of the Java child's open/mapped game files and owned TCP listeners. View/export
persists across Stop and app reopening. Unknown/denied observations stay explicit;
configuration writes and world/database relocation are not introduced. See
[WORLD_CONFIGURATION_TEST.md](WORLD_CONFIGURATION_TEST.md) for implementation,
every changed file, report limitations and the reproducible physical test.
The Thor report now records wildcard IPv6 listeners and a successful IPv4
loopback connection; LAN reachability and effective INI selection remain open.

**Next small milestone implemented:** 0.5.0 adds stopped-runtime baseline/check
controls, persistent file comparisons and SQLite-copy checks using the existing
foreground service/native child/lock. See [STORAGE_VERIFICATION.md](STORAGE_VERIFICATION.md)
for every changed file, migration from the stopped 0.4.1 working export and the
physical acceptance procedure. No POC, runtime pin, SQL patch or client rewrite.

**Latest device result:** [0.5.0 changed-file persistence passed on the Thor](THOR_STORAGE_PASS.md).
The post-stop and post-reopen checks match all 12,007 files, pass all 36 databases
and report `SOURCE_UNCHANGED`. Four Adventure map files and three databases under
`localhost/sqlite` changed from baseline and retained those bytes after reopening.
The corresponding server session reached TCP 3724 and exited 0 after normal Stop.
The 0.6.0 read-only report above now collects world/map/database observations and
configuration candidates for device review before editable settings/world switching.
A specific gameplay change still needs verification through a supported external
client/admin route across a server restart.

**Earlier device result:** [0.4.1 managed startup, same-copy reopen, Restart and
requested shutdown passed on the Thor](THOR_SERVER_PASS.md). The source-built Java 17.0.20 runtime passed
Java/SQLite/network preflight; Wurm reached TCP 3724 under UID 10197 and exited 0
after normal Stop. The cumulative follow-up records three successful cycles,
including controller-managed Restart. The user confirmed five minutes of app
switching and two minutes of screen lock, returning to Running after both.
Changed-file persistence is now covered by the 0.5.0 result above. Gameplay-level
save verification and extended memory/background behavior remain open.

**Implemented next milestone:** the 0.4.0 `managedPreview` variant now connects
imports to a protected working copy, source-built Android Java 17.0.20, preflight,
foreground server controls, checkpoints/export/restore and live logs. See
[the concrete implementation and Thor acceptance procedure](MANAGED_SERVER_TEST.md)
for the current behavior and every changed file. The sections below retain the
original import-preview plan and history; statements about disabled controls
apply to the old `debug`/0.2.0 preview. Managed Wurm startup, same-copy reopen,
Restart, requested normal exit and changed-file persistence now passed. Real SQL
item paths, gameplay-save semantics and extended background lifetime remain device
acceptance gates.

**Device progress, 2026-09-08:** the Thor import and Adventure selection
persistence passed. All four source server/common/SQLite hashes match the import
report; the successful Termux JVM is OpenJDK 17.0.20. The separate
[embedded JVM diagnostic 0.3.2 passed on the Thor](THOR_JVM_PASS.md) at 12:02 UTC:
the APK-owned Java 17.0.10-internal process ran as ordinary UID 10193 and SQLite
completed create/insert/update/commit/close/reopen, with child exit 0. The earlier
re-exec and boot-class-path failures are resolved. The `jvmProbe`
build leaves the existing import and rooted launch paths intact; a maintained
runtime is required before promoting this backend to Wurm server use.

## Inspection and boundary

The existing platform-widget Kotlin app, foreground `ServerService`, root
controller, Bash supervisor, tests and native Termux build remain the regression
path. The supervisor still starts **Adventure** with the exact proven classpath
and heap arguments. This milestone does not change that launch or shutdown path.

`poc/README.md` was read before inspecting the two Java sources and decoding the
artifact. The decoded JAR contains only a manifest and these two Java 17 classes:

- `poc.AndroidServerMain`: world/GameFolder selection, personal-server mode,
  `runServer(false, true)`, and the persistent keep-alive loop.
- `SteamJni.SteamServerApi`: the Java offline Steam shim and handler callbacks.

The JAR's SHA-256 is
`0fe4039a1a06afae93099b6eaf140e04fe7e0b1f1145323116468f8f78a884fe`.
It is available, source-backed application code. It is not a missing dependency.
The source has no SQLite item SQL patch implementation. The README explicitly
locates that work outside this JAR; no exact patch/diff is in this repository.
The previously working, patched user runtime is therefore the first import input.

## 1. Package the POC

Immediately: Gradle decodes the tracked base64 into a generated APK **asset**,
checks its SHA-256 and class allowlist, and checks the associated source hashes.
The importer installs that exact JAR. It is not placed on Android's compile/D8
classpath: these JVM classes use Wurm server APIs unavailable to Android ART.
CI needs no proprietary JARs. Source/artifact drift fails the build.

When changing the POC: compile both sources with `javac --release 17` against
the owner's `server.jar` and `common.jar`, then regenerate the base64 and pinned
hashes after review. Those dependency JARs never enter git or CI artifacts.
Do not implement SQL substitutions by guessing from the phrase
`ON DUPLICATE KEY UPDATE`; obtain the exact earlier patch and supported server
version before implementing repeatable, version-checked patch generation.

## 2. Import user files

Immediately: Android's document picker accepts a ZIP containing a **stopped,
prepared POC runtime**, at ZIP root or inside a single wrapper directory. Copy
into app-private storage, without root, broad storage permissions, downloads or
uploads. Require the known server/common/SQLite JARs, `lib/`, and at least one
world candidate. Include every runtime resource and existing SQLite patch/overlay.
Reject a conflicting imported POC JAR rather than silently replacing it.

Extraction validates paths and bounds entries/uncompressed bytes, stages the
import, and atomically commits an active-generation pointer only after validation.
A failed/interrupted replacement leaves the prior import usable. Source ZIP and
Termux runtime remain untouched. Byte-for-byte extraction preserves patched game
files and database contents; the report records file hashes but does not certify
that unknown proprietary bytecode contains the correct patch.

Later: support a stock-server installation through an explicit supported-version
manifest and the recovered SQL patch recipe. Add world backup/export before
allowing a managed JVM to mutate imported worlds. Do not bundle Wurm files.

## 3. Launch and manage Java on ARM64

The demonstrated Termux JVM launch is preserved as a separately labelled rooted
screen. Copying Termux's `java` executable is insufficient for a standalone APK:
its library paths and Android execution rules differ.

Completed diagnostic: the pinned Android/Bionic ARM64 OpenJDK 17.0.10 candidate
runs through the APK-installed native JLI launcher in a separate child process,
with private JRE data, a verified JVM image alias and disposable working directory.
Basic Java startup, SQLite native loading and normal process exit passed on the
Thor. Before server integration, build/package a maintained Java 17 runtime with
explicit source/patch provenance and repeat the diagnostic. Android's
[writable-home execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10)
rules out simply extracting an executable into `filesDir` and invoking it.
The APK-packaged executable approach is now validated for this short diagnostic.

Then launch the unchanged POC entry point with the proven classpath ordering,
including the Android SQLite natives JAR and the existing SQL patch overlays.
Keep native JVM state outside the UI process so `System.exit`, JVM crashes and
native state cannot take down the UI. A foreground service owns the process,
bounded stdout/stderr, a world/runtime lock and readiness probes. Acquire the
wake lock only during a running server. No root, Termux prefix or shell is part
of this managed backend. The present import preview does **not** contain a JVM.

## 4. Controls, status and logs

Immediately: add a home screen with Server and Client tabs. Server offers import,
world selection, a report and an explicit unavailable Start/Stop/Restart state
for managed execution. It links to the unchanged rooted Start/Stop/status/live
log screen for regression tests. Import progress/error and runtime readiness
are distinct from a running process; imported does not mean running.

With the JVM probe passing, connect these controls to a managed controller behind
the existing service pattern. States: Stopped, Starting, Running, Stopping, Error.
Distinguish process alive from TCP 3724 ready. Restart means request shutdown,
wait for confirmed exit and lock release, then start; never overlap processes.
Identify/test Wurm's save-and-shutdown API before claiming graceful world saving.
Retain bounded logs and export diagnostics; reconcile service death explicitly,
without an automatic restart loop. Verify newer foreground-service requirements
before increasing the current Android 13 target SDK.

Client groundwork: persist a user-selected ZIP reference and connection settings
in a separate tab. Start stays disabled. No extraction, renderer, authentication
or desktop client execution is claimed; these need a separate compatibility plan.

## 5. Worlds and configuration

Immediately: discover top-level directory candidates containing `wurm.ini` or
`sqlite/`, list them and persist one selected world per active import. These are
candidates, not a reimplementation of Wurm's `GameFolder.fromPath` validation.
Do not rewrite `wurm.ini`, databases, names, credentials or network settings.
The report identifies the selected world and notes that the legacy rooted screen
still runs Adventure in its separately configured Termux directory.

Later: model a launch profile as runtime generation, world directory, initial/max
heap (initial defaults 512 MiB / 4 GiB), and confirmed server ports. Use validated
fields and argument arrays, not shell fragments. Represent Wurm configuration
through version-aware parsing that preserves unknown keys, comments and backups.
World switching/config changes require a stopped process. Expose only settings
whose effect is verified; do not guess the Wurm INI schema.

## 6. Smallest working milestone implemented now

- Verified, reproducible decoding and APK packaging of the current POC artifact.
- Transactional no-root prepared-runtime ZIP import and validation.
- World discovery/selection and exportable identity/diagnostic report.
- Wurm Server home screen, retained root regression screen, client tab groundwork.
- Host tests for import failures, preservation, paths, limits and artifact identity.
- Build/test/lint in CI and a clearly labelled installable import-preview APK.

This is a useful on-device storage/import test, not a standalone server release.
It deliberately adds to the current small project instead of replacing its
controller or proven POC. See [THOR_IMPORT_TEST.md](THOR_IMPORT_TEST.md) for inputs,
steps and expected results.

## 7. Remaining physical-device gates

1. Basic prepared-ZIP import, world discovery, hash identity and Adventure
   selection persistence passed. Interrupted/large imports, backup/export and
   recovery still need their own device tests.
2. Embedded ARM64 Java startup, boot modules, ordinary-UID execution and normal
   exit passed with both the 17.0.10 diagnostic and maintained 17.0.20 managed
   runtime. The latter reached Wurm TCP readiness with a 4096 MiB maximum heap;
   extended memory/lifecycle behavior remains open.
3. SQLite 3.53.2.1 Android native loading and disposable database persistence
   passed. This does not establish Wurm-specific item SQL compatibility.
4. Hash-identical patched runtime reached GameFolder/database loading, Steam shim,
   personal/offline startup and TCP 3724 under the app UID in the 0.4.1 report.
   Exercise item insert/update paths to prove the earlier SQL fix still works.
5. Same-copy restart, short app-switch/screen-lock operation and changed-file
   persistence after normal Stop/app reopening passed. Verification of a specific
   gameplay change, process death, lock contention, extended OEM power management,
   notification behavior and peak memory at the selected heap remain open.
6. LAN reachability and Wurm-level behavior beyond a listening socket. No current
   test or a TCP probe establishes a complete playable server/client session.

The maintained embedded JRE build and first managed startup/requested-stop result
are complete. The remaining lifecycle and persistence gates above need further
device evidence. The exact SQL patch recipe is also needed before supporting
unpatched stock imports; retain the already working patched runtime. The POC
Java source and current JAR are present.
See [the post-PASS milestone](THOR_JVM_PASS.md#next-implementation-milestone) for
the ordered implementation and device acceptance criteria.

The import picker follows Android's
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
Users must export from Termux's private directory to Downloads first; another
ordinary app cannot directly read `/data/data/com.termux/...`.
