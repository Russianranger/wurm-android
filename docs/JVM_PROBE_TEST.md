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

## What to install and run

1. Keep your existing **Wurm Server** app and its imported Adventure world.
2. Download **Wurm-Server-JVM-Test.apk** from
   [v0.3.0-jvm-probe](https://github.com/Russianranger/wurm-android/releases/tag/v0.3.0-jvm-probe).
   It installs as **Wurm Server JVM Test**, package
   `io.github.russianranger.wurmlauncher.jvmprobe`. No root, Termux or additional
   Java installation is needed to run this APK.
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
[native] uid=... euid=... pid=...
[probe] java.version=17.0.10
[probe] JAVA_OK
[probe] SQLITE_OK: create/insert/update/commit/close/reopen
[probe] PROBE_OK
[app] Child exit=0
[app] RESULT: PASS
```

If the report stops at `dlopen`, JLI or VM initialization, investigate native
loading/image layout next. If it reaches `JAVA_OK` but SQLite fails, investigate
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
