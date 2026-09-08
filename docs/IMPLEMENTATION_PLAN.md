# From the working POC to Wurm Server

## Current priority: in-app client integration (0.7.0)

The user has no separate PC. The next acceptance target is **Android server →
Android Wurm client → local login → rendered world → controller input**.
Earlier references below to external-PC/LAN testing describe historical plans;
they are not prerequisites for this milestone. Keep the tested server, POC,
source-backed artifact, SQL compatibility work and managed world unchanged.

1. **Files and packaging.** Keep compiling all handwritten JVM helper sources
   as `runtime-probe.jar`, separate from Android/D8. Continue packaging the exact
   source-pinned POC JAR for the server only. Import the user's complete client
   ZIP into an independent transactional `managed-client` generation. Require
   `client.jar`, `common.jar`, `lib/`, one engine provider and one LWJGL2 Display
   provider, hash the classpath and list asset candidates. Proprietary game
   files and desktop native libraries never become repository dependencies.
2. **Bootstrap.** Reuse the APK-installed Android ARM64 Java 17.0.20 executable,
   environment, heap-tagging workaround and safe process ownership. An independent
   client foreground service holds a client-only lock. Run API metadata discovery,
   actual `WurmClientBase` initialization/direct-launch attempt, and actual imported
   LWJGL2 `Display.create()` in separately bounded children. A prior stage's failure
   must not suppress later diagnostics. Obtain exact `launch(...)` and Steam ABI
   from the imported files; do not invent parameter values for unknown signatures.
3. **Local Steam adapter.** The existing server shim stays untouched and off the
   client classpath. After ABI discovery, implement only the observed client local
   init/identity/ticket call path as a separate source-built client adapter. Use a
   persistent synthetic identity scoped to this local/offline mode, with explicit
   shim-state logs. Ticket format and whether this personal server accepts it are
   device gates. No Steam browsing, full Steamworks or license bypass is planned.
4. **Graphics/input.** Qualify the Android GLES surface and gamepad events now.
   Reuse the investigated Pojav LWJGLX/GLFW + native surface/input + GL4ES path
   after API coverage and native host integration are verified. A plain exec child
   cannot use a raw Android `ANativeWindow*` from another process. The likely next
   isolated render host is an Android `:client` process with its own Surface and
   ART/OpenJDK JNI bridge; this does not replace the server process architecture.
   Preserve the tested mapper/protocol when replacing the diagnostic receiver
   with the renderer's LWJGL keyboard/mouse adapter. See the source pins, licensing,
   alternatives and concrete integration checklist in [CLIENT_INTEGRATION.md](CLIENT_INTEGRATION.md).
5. **UI and local orchestration.** Client tab exposes Import, Start Client, Start
   Local Game, Stop, controller settings/test and persistent client report export.
   Start Local Game uses an existing local listener or starts this package's
   managed server and waits for TCP 3724 before the client bootstrap. It does not
   stop the server when the client stops. A TCP probe is not a Wurm connection.
   Complete direct login only after mapping the actual launch API. World selection
   and server configuration remain owned by the existing Server tab; client target
   is fixed to `127.0.0.1:3724` for this gate, with no speculative INI edits.
6. **Implemented now.** Gate 1 basic import validation; Gate 2 JVM/ABI/linkage
   attempts; Gate 4 Android surface qualification and editable controller → JVM
   diagnostic transport; Gate 5 TCP readiness orchestration. These are host-tested
   implementations awaiting physical acceptance, not successful gameplay claims.
7. **Remaining physical blockers.** Actual launch signature/argument semantics,
   client-side Steam ABI and ticket acceptance, complete assets, LWJGLX coverage,
   OpenGL feature requirements/GL4ES on Thor, OpenAL/JInput replacements, Android
   surface/dual-VM lifecycle, working-set memory with server, login and world entry.
   [CLIENT_THOR_TEST.md](CLIENT_THOR_TEST.md) specifies the exact no-PC test and
   `wurm-client-report.txt` evidence needed for the next adapter implementation.

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
