# Client integration architecture and qualification

## Current milestone: 0.10.15 personal-server character creation

The 0.10.14 Thor passes local authentication but fails with `No such player - Thor`.
The POC sets personal mode true before calling `runServer(false, true)`. Inspection
of the matching original ServerLauncher proves its first argument overwrites that
mode before startup. The missing-player branch then rejects registration because
personal mode is false. Wurm already contains the creation path and the in-game
"Define your character" prompt.

The POC now uses `runServer(true, true)` and verifies personal mode after startup.
The authored artifact, source pins and importer pin are updated together. Existing
client credentials, game JARs, SQLite/encoder overlays, Steam shim source, Serial GC,
rendering and controller implementation are retained. No player database is edited
manually and no extra client creation packet is introduced.

**Next gate:** install 0.10.15, import the same server/client ZIPs, select Adventure
and Thor, then use this version's Start Local Game. Complete in-game creation if
it appears; export matched Client and Server Session Reports plus a screenshot.
If creation and clean shutdown succeed, restart the same character without
reimporting to check persistence. Native EGL/Scudo stability, audio and gameplay
remain unqualified. See [SERVER_PERSONAL_MODE_FIX.md](SERVER_PERSONAL_MODE_FIX.md).

## Previous milestone: 0.10.14 local login credential

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


## 0.9.0: native LWJGL/GL4ES graphics diagnostic

The next executable Gate 4 step is implemented in
[GRAPHICS_THOR_TEST.md](GRAPHICS_THOR_TEST.md): source-built ARM64 native core/OpenGL
bindings from the pinned Pojav fork, pinned GL4ES, an authored EGL pbuffer bridge,
desktop GLSL triangle/pixel verification, resize/swap/teardown and Android frame
viewing. It runs inside the existing separate OpenJDK child; no ART/OpenJDK
cohosting or Android native-window pointers are required for this bounded test.
This does not yet connect the candidate to Wurm's Display/window or input queues.

The render child alone receives `graphics-probe.jar`, `pojav-wurm-api.jar`, the
GL4ES GLES2/OpenGL2.1 settings and LWJGL's supported library-name mapper. Renamed
LWJGL3 JNI libraries do not alter the original client's `liblwjgl.so` lookup.
All four libraries and Java assets are verified against packaged SHA-256 metadata.
The existing Client Report includes source pins, outcomes and retained frame
identity. Pixel transport is a bounded diagnostic file, not a production frame-rate
claim; no synthetic Android drawing is substituted if the JVM fails.

This source-built native test is ready for physical acceptance, which remains
pending. Next use its result to resolve graphics loading/driver failures or advance
to GLFW/window integration, display sizing, audio and real gameplay input. Local
Steam Java compatibility has passed; server ticket acceptance still needs a Wurm
login. The working managed server and prior device evidence remain unchanged.

The following 0.8.0 architecture is retained as the preceding milestone. Its
candidate-packaging and next-device-test statements are superseded above.

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
