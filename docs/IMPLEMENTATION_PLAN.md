# From the working POC to Wurm Server

**Device progress, 2026-09-08:** the Thor import and Adventure selection
persistence passed. All four source server/common/SQLite hashes match the import
report; the successful Termux JVM is OpenJDK 17.0.20. The next implemented gate is
the separate [embedded JVM diagnostic](JVM_PROBE_TEST.md), using a pinned Android
17.0.10 candidate. Physical Java/SQLite execution is still pending. The `jvmProbe`
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

Next executable milestone: select and validate an Android/Bionic ARM64 OpenJDK
17 distribution and its redistribution notices; package the required native
libraries in the APK. Prototype a small native launcher in a dedicated app
process (JNI Invocation API / `JNI_CreateJavaVM`), with the JRE data extracted to
private storage and `cwd` set to the imported runtime. Prove basic Java startup,
SQLite native loading and clean process exit before introducing Wurm. Android's
[writable-home execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10)
rules out simply extracting an executable into `filesDir` and invoking it.
An APK-packaged executable is an alternative only after device validation.

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

When the JVM probe works, connect these controls to a managed controller behind
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

1. Android document-provider handling of the real multi-GB ZIP, free space,
   progress, interrupted imports, persistence and world discovery on the Thor.
2. Embedded ARM64 JVM 17 startup, Bionic/linker namespaces, required native/JRE
   modules, memory overhead and app UID filesystem access on the Thor OS version.
3. SQLite 3.53.2.1 Android native loading in the app's process/library environment.
4. Hash-identical patched runtime reaches GameFolder/database loading, Steam shim,
   personal/offline startup, persistent threads and TCP 3724 under the app UID.
   Exercise item insert/update paths to prove the earlier SQL fix still works.
5. Save/stop/restart persistence, process death, lock behavior, screen-off and OEM
   power management, notification behavior and peak memory at the selected heap.
6. LAN reachability and Wurm-level behavior beyond a listening socket. No current
   test or a TCP probe establishes a complete playable server/client session.

The exact SQL patch recipe, the embedded JRE build and device results are the
remaining dependencies. The POC Java source and current JAR are already present.

The import picker follows Android's
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
Users must export from Termux's private directory to Downloads first; another
ordinary app cannot directly read `/data/data/com.termux/...`.
