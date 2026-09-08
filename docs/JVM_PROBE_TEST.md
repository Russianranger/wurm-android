# AYN Thor: embedded JVM diagnostic

## Verified device baseline, 2026-09-08

Your import report and Termux screenshot establish Android 13 / API 33 ARM64,
OpenJDK **17.0.20+0**, 12,002 imported files (692,919,114 bytes), and Adventure
selection persisting after reopening Wurm Server. The four source JAR hashes
match the app's import report:

| File | SHA-256 |
| --- | --- |
| `server.jar` | `9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06` |
| `common.jar` | `066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633` |
| `poc-lib/sqlite-jdbc-3.53.2.1.jar` | `f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1` |
| `poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar` | `011d4edb8d06012ced78d6aa675ffc85bf339d3cd640845684b80873ec5a6e97` |

This validates import identity/persistence, not Wurm database saving or the
behavior of the earlier item SQL patch under the app UID.

## Latest device report and 0.3.2 correction

The 0.3.1 Thor report from 2026-09-08 11:42 UTC confirms `mustsetenv: FALSE`
and successful loading of `libjvm.so`, at ordinary UID/eUID 10183. The earlier
re-exec problem is resolved on the device. Initialization then stops with
`Failed setting boot class path.`, exit 1. No Java/SQLite success markers appear.

In the pinned Android HotSpot, `os::jvm_path` uses the loaded library name from
`dl_iterate_phdr` (with `dladdr` as fallback). `init_system_properties_values`
removes three path components to derive the initial Java home and locates
`lib/modules`. This happens before the command-line `-Djava.home` is applied.
Android's loader reports the canonical APK path, `.../lib/arm64/libjvm.so`, even
when the file was opened through the private `.../lib/server/libjvm.so` symlink.
That flat layout derives the APK directory instead of the private JRE image.

Version 0.3.2 adds a small adapter to the existing native runner. Its two library
query exports forward to the system linker and report the **selected JVM only**
through its existing JRE alias. The adapter checks that alias resolves to the
same APK-installed native file and that boot modules exist. It preserves library
addresses, symbols, program headers and callback return values. Every other
library keeps its original name. No upstream native binary is patched or copied
to writable storage, and no device-wide setting changes.

The app now verifies the installed 81,720,691-byte `lib/modules` image against
SHA-256 `2cd3abc75196790da2ad94fffbf93c43b70415d8172a824e96619b401c408139`.
The native build verifies both adapter exports. Three host tests use synthetic
shared libraries to check the original flat-path failure, both adapted queries,
an unrelated same-named library, callback behavior, and invalid image rejection.
These are loader-contract tests; they do not constitute an Android JVM PASS.

Source references:
[HotSpot image-directory derivation](https://github.com/openjdk/jdk17u/blob/ca760c86642aa2e0d9b571aaabac054c0239fbdc/src/hotspot/os/linux/os_linux.cpp),
[boot module lookup](https://github.com/openjdk/jdk17u/blob/ca760c86642aa2e0d9b571aaabac054c0239fbdc/src/hotspot/share/runtime/os.cpp),
[Android HotSpot path patch](https://github.com/FCL-Team/Android-OpenJDK-Build/blob/ce21ce33b4f495e678c2cfdecb6abe893bf561ee/patches/jdk17u_android.diff),
[Bionic canonical paths](https://github.com/aosp-mirror/platform_bionic/blob/android13-release/linker/linker.cpp),
[Android global symbol lookup](https://github.com/aosp-mirror/platform_bionic/blob/android13-release/android-changes-for-ndk-developers.md).

## First JVM device report and 0.3.1 correction

The Thor report from 2026-09-08 11:18 UTC confirms both SQLite input hashes,
the JRE data/native checks, and the APK-installed child running as ordinary
UID/eUID 10182. It then fails with `trying to exec .../bin/java`, exit 1.
Java initialization and SQLite execution have **not** passed on the device.

The 0.3.0 environment put the APK native directory first in `LD_LIBRARY_PATH`.
OpenJDK's [RequiresSetenv and CreateExecutionEnvironment](https://github.com/openjdk/jdk17u/blob/ca760c86642aa2e0d9b571aaabac054c0239fbdc/src/java.base/unix/native/libjli/java_md.c)
detect the later `lib/server/libjvm.so` and request a re-exec. The Android
[SetExecname patch](https://github.com/FCL-Team/Android-OpenJDK-Build/blob/ce21ce33b4f495e678c2cfdecb6abe893bf561ee/patches/jdk17u_android.diff)
derives that executable as `JAVA_HOME/bin/java`. This image deliberately keeps
executables in the APK install location; that writable-home executable is absent.

Version 0.3.1 puts `JAVA_HOME/lib/server` first, followed by `JAVA_HOME/lib` and
the APK native directory. The environment is set before starting the child.
A regression test exercises a real Linux JDK 17 launcher with both orders:
the old order requests re-exec; the corrected order does not. Android-specific
loading still needs the repeat Thor test. Launcher tracing and the app version
are now included in the exported report. Look for `mustsetenv: FALSE` before
the Java/SQLite success markers.

## What to install and run

1. Keep your existing **Wurm Server** app and its imported Adventure world.
2. Download **Wurm-Server-JVM-Test.apk** from
   [v0.3.2-jvm-probe](https://github.com/Russianranger/wurm-android/releases/tag/v0.3.2-jvm-probe).
   It installs as **Wurm Server JVM Test**, package
   `io.github.russianranger.wurmlauncher.jvmprobe`. No root, Termux or additional
   Java installation is needed to run this APK.
   If Android rejects the update because its development signing key changed,
   uninstall **Wurm Server JVM Test only**, then install the new APK. Keep the
   original **Wurm Server** app installed; its imported Adventure is separate.
3. Have at least 400 MiB of free internal storage and your existing prepared
   runtime ZIP in Downloads. The ZIP seen in your screenshot was about 219 MiB.
4. Open the test app and tap **Select Runtime ZIP and Run Test**. Select that
   same ZIP. The app temporarily copies the ZIP for indexed access, extracts only
   the two checksum-pinned SQLite JARs, and deletes the temporary ZIP. It never
   extracts or opens Adventure, `server.jar`, `common.jar`, or any Wurm database.
5. Keep the app open. It installs the bundled JRE data, verifies the native
   libraries, and starts the Java/SQLite test in a child process under the test
   app UID. Rotation retains progress. Java execution has a 90-second timeout;
   ZIP copying/JRE setup happens before that timer. Cancel stops this disposable
   test only. Force-stopping the app ends the owned native child as well.
6. Tap **Export Report**, save `wurm-jvm-probe-report.txt` to Downloads, and send
   it back whether the result is PASS or FAIL. The report persists on reopening.

Expected success markers:

```text
[runtime] Boot modules verified: 81720691 bytes, SHA-256 ...
[native] uid=... euid=... pid=...
mustsetenv: FALSE
[native] JVM_IMAGE_PATH_OK: .../lib/server/libjvm.so
[probe] java.version=17.0.10
[probe] JAVA_OK
[probe] SQLITE_OK: create/insert/update/commit/close/reopen
[probe] PROBE_OK
[app] Child exit=0
[app] RESULT: PASS
```

If the report stops at `dlopen`, JLI or VM initialization, investigate native
loading/image layout next. In 0.3.2, `JVM_IMAGE_PATH_OK` establishes that a query
was translated; it alone does not establish Java initialization. If it reaches
`JAVA_OK` but SQLite fails, investigate
the exact imported driver, Android native selection and its shared-library
dependencies. Export includes the beginning of a JVM fatal-error report when
one is available. A missing final result after reopening means the prior run
was interrupted; run the diagnostic again.

## Scope and runtime choice

The candidate is the **Android OpenJDK 17.0.10** ARM64 build distributed with Fold
Craft Launcher. It is **not** the same build as your working Termux 17.0.20.
This older build is used only for a bounded local compatibility probe. It is not
approved here as the final runtime for a running/network-accessible Wurm server.
A maintained runtime build, including a repeat of these tests, is a gate before
promoting this backend to server use.

We pin both upstream archive hashes and the source repository revision; preserve
the upstream JRE legal notices in the APK; and provide the base OpenJDK source
and Android build/patch sources alongside the release. See
[RUNTIME_PROVENANCE.md](RUNTIME_PROVENANCE.md) for exact references and limits.

The runner is a small native ELF executable packaged as
`lib/arm64-v8a/libwurmjvm_runner.so`. Android installs it with the APK's native
libraries. The app launches it using an argument array, without a shell or `su`.
The runner calls OpenJDK's JLI launcher, which initializes the JVM in that fresh
process. No second JVM is loaded into the Android UI's ART process. JRE data is
private; native files there are symlinks to APK-installed libraries. This respects
Android's [writable-home execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10).

The test uses a 32 MiB initial / 256 MiB maximum heap and a new private scratch
database on each run. It checks basic Java threads/computation and SQLite
create/insert/update/commit/close/reopen. It does not connect to a server, open
TCP 3724, start the POC, test Wurm item SQL, or verify world shutdown hooks.

The separate package is intentional: the 0.2.0 preview used a temporary CI debug
signing key. Installing this diagnostic alongside it preserves your verified
import. The test APK also uses development signing; future test APKs may require
uninstalling **the test app only**. A durable signing key remains required for
the main app before managed worlds become live data.

## Build and next gate

Normal `assembleDebug` and the native Termux build remain the import/root app.
The extra `jvmProbe` build type needs Linux x86_64, Python 3, curl, JDK 17 and NDK
26.1.10909125 in addition to the existing SDK setup:

```bash
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.1.10909125"
bash ./gradlew --no-daemon :app:assembleJvmProbe :app:testJvmProbeUnitTest :app:lintJvmProbe
```

Output: `app/build/outputs/apk/jvmProbe/app-jvmProbe.apk`. Gradle downloads and
verifies the pinned open-source JRE, builds the native runner, compiles the
handwritten `runtime-probe/src/probe/RuntimeProbe.java` as Java 17 bytecode, and
packages its JAR as an asset. No proprietary dependency is needed for building.

After a physical PASS, integrate a maintained embedded runtime with the managed
import and the unchanged POC classpath. Add world backup/export and then test
GameFolder initialization, the Steam shim, item SQLite updates, TCP 3724, and
save/stop/restart. The currently working source-backed POC stays unchanged.

## Files changed for this milestone

### 0.3.2 image-layout correction

| File | Change |
| --- | --- |
| `runtime-probe/native/jvm_layout.c` | Forward system library queries with a verified image alias for the selected JVM |
| `runtime-probe/native/jvm_layout.h` | Adapter initialization interface |
| `runtime-probe/native/jvm_runner.c` | Initialize image layout before loading JLI |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeEnvironment.kt` | Supply the installed JVM's exact path |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeRuntime.kt` | Verify the installed Java module image and report its identity |
| `scripts/prepare-jvm-probe.py` | Pin modules, compile the adapter and check its dynamic exports |
| `tests/native/jvm_layout_library.c` | Synthetic shared-library fixture for both query APIs |
| `tests/native/jvm_layout_runner.c` | Exercise the exported adapter from dynamically loaded libraries |
| `tests/test_jvm_layout.py` | Three host regression tests with an unadapted negative control |
| `app/build.gradle.kts` | Native source task inputs and version 0.3.2, code 5 |
| `.github/workflows/android.yml` | Run native tests, verify packaged modules and publish 0.3.2 |
| `docs/IMPLEMENTATION_PLAN.md` | Record passed launcher fix and the next initialization gate |
| `docs/JVM_PROBE_TEST.md` | Explain device evidence, source diagnosis, correction and retest steps |
| `docs/RUNTIME_PROVENANCE.md` | Document unchanged upstream binaries, module hash and adapter source |
| `docs/RELEASE_JVM_PROBE.md` | Updated release findings and installation instructions |

### 0.3.1 launcher-path correction

The 0.3.1 correction changes the following files; the original 0.3.0 inventory
is retained below.

| File | 0.3.1 change |
| --- | --- |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeEnvironment.kt` | New shared launch environment with the JVM directory first and JLI tracing |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/JvmProbeCoordinator.kt` | Apply that environment and include app version in reports |
| `app/src/testJvmProbe/java/io/github/russianranger/wurmlauncher/ProbeEnvironmentTest.kt` | Real host JLI regression test including the failing order as a negative control |
| `app/build.gradle.kts` | Version 0.3.1, code 4 |
| `.github/workflows/android.yml` | Publish a new immutable 0.3.1 diagnostic release |
| `docs/JVM_PROBE_TEST.md` | Device failure, cause, correction, install steps and file inventory |
| `docs/RELEASE_JVM_PROBE.md` | Launcher-fix release notes and test-app update instructions |
| `docs/IMPLEMENTATION_PLAN.md` | Record the native launch passing and JLI startup failure |

### Original diagnostic milestone

| File | Purpose |
| --- | --- |
| `app/build.gradle.kts` | Additional `jvmProbe` build, JVM-class JAR tasks and native assets |
| `app/src/jvmProbe/AndroidManifest.xml` | Separate package launcher/label and APK native extraction |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/JvmProbeActivity.kt` | ZIP picker, progress, cancel, log viewing and export |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/JvmProbeCoordinator.kt` | Test lifecycle, child ownership, timeout and saved report |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeInputs.kt` | Copy only the two verified SQLite inputs |
| `app/src/jvmProbe/java/io/github/russianranger/wurmlauncher/ProbeRuntime.kt` | Verify/install JRE data and native-library links |
| `app/src/testJvmProbe/java/io/github/russianranger/wurmlauncher/ProbeInputsTest.kt` | Five focused input validation/preservation tests |
| `runtime-probe/native/jvm_runner.c` | Ordinary-UID native child that calls OpenJDK JLI |
| `runtime-probe/src/probe/RuntimeProbe.java` | Java threads/computation and disposable SQLite persistence test |
| `scripts/prepare-jvm-probe.py` | Pin/download/package JRE and compile ARM64 runner |
| `.github/workflows/android.yml` | Both variant builds/tests/lint, APK verification, diagnostic release |
| `README.md` | Entry point to the next device test |
| `docs/BUILD_TERMUX.md` | Distinguish the existing Termux build from the NDK diagnostic build |
| `docs/IMPLEMENTATION_PLAN.md` | Record passed import checks and the next physical gate |
| `docs/JVM_PROBE_TEST.md` | Verified baseline, install/test steps, expected output and scope |
| `docs/RUNTIME_PROVENANCE.md` | Candidate version, hashes, source/notice references and limitations |
| `docs/RELEASE_JVM_PROBE.md` | Clear install-alongside diagnostic release notes |
