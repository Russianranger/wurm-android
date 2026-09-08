# Thor native heap abort: 0.4.1 compatibility test

## Evidence from 0.4.0

The supplied `wurm-server-report.txt` records the AYN Thor / Android 13 / ARM64 run
on 2026-09-08 at 13:36 UTC, UID/eUID 10195, Adventure, 4096 MiB heap and TCP 3724.
Report SHA-256: `a7c6c4d554fe86ffb3fc4f0e7d9c22ba1d52ca70f5d333b99de368dc89f6cc42`.

| Evidence | Conclusion |
| --- | --- |
| All five server/common/SQLite/POC hashes match | The prepared, SQLite-patched input identity was retained. |
| Java 17.0.20-internal, `JAVA_OK`, `SQLITE_OK`, `PROBE_OK`, `PREFLIGHT_PASS` | The source-built runtime passed the disposable Java/SQLite test under an ordinary app UID. |
| Completed before-start ZIP | Recovery checkpoint creation succeeded before Wurm opened. |
| `Server.shutDown()V` resolved | The shutdown adapter found the expected API; shutdown itself was not tested. |
| Adventure recognized/current; Dist loaded; Steam shim; personal/offline startup | The unchanged POC progressed through these initialization stages. |
| Flyway opened creatures/deities/economy/items/login/logs/players/templates/zones | Wurm reached SQLite loading. Imported URLs say `jdbc:sqlite:localhost/sqlite/...`; this correction does not rewrite their configuration. |
| `Loading servers`, then `Pointer tag ... was truncated`, exit 134 | Native allocator abort before TCP readiness. No successful managed startup or save is established. |

The report has no native backtrace identifying the offending library/function.
The last Wurm message makes native networking worth probing, but does not prove
networking caused the abort. Neither an out-of-memory condition nor a particular
bad-pointer operation is established. `DT_RPATH ... ignoring` warnings also occur
during the successful preflight; they are not this run's terminating condition.

## Narrow compatibility change

[Android's tagged-pointer documentation](https://source.android.com/docs/security/test/tagged-pointers)
describes this allocator check and compatibility opt-out. An invalid allocation
pointer or lost pointer tag can trigger it. Disabling checks is not a repair of
the underlying native bug and must not be reported as such.

The managed controller sets `WURM_HEAP_TAGGING=off` for its preflight and Wurm
children. The APK-installed native runner configures its own allocator after the
ordinary-UID check and before loading Java or acquiring the world lock. It calls
the public Bionic API `mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL,
M_HEAP_TAGGING_LEVEL_NONE)` (available since API 31; this APK requires API 33).
See the [Android 13 Bionic header](https://android.googlesource.com/platform/bionic/+/refs/heads/android13-release/libc/include/malloc.h)
and [allocator implementation](https://android.googlesource.com/platform/bionic/+/refs/heads/android13-release/libc/bionic/heap_tagging.cpp).

Small allocations before/after record the top byte without modifying the pointer
passed to `free`. Success prints `HEAP_TAGGING_OFF: before=0x.. after=0x00`.
Unknown policy, unavailable opt-out, allocation failure or a still-tagged new
allocation exits 78 before Java. An absent environment setting leaves the shared
runner's allocator behavior unchanged, including the separate JVM diagnostic.

This disables heap-tag checks in the Java child only. It does not change Android
system settings, ART or manifest policy. It is a temporary compatibility test;
another invalid-memory operation may still abort, and future device/runtime
qualification needs to revisit this setting.

Managed preflight also enables `probe.NetworkProbe` via
`-Dwurm.probe.network=true`. It records interface-enumeration stages without
logging addresses, resolves localhost and exchanges bytes over an ephemeral
loopback TCP socket. Java permission/socket exceptions during enumeration are
reported as `ENUMERATE_UNAVAILABLE`, not an enumeration pass; localhost and the
TCP exchange must still succeed. Native aborts stop preflight. The final
`NETWORK_OK` marker joins `JAVA_OK`, `SQLITE_OK` and `PROBE_OK` in the gate before
Wurm/checkpoint creation. These disposable tests do not open game files or bind
the expected game port.

## Exactly what to install and run on the Thor

1. Keep 0.4.0 installed and retain its report, original import and before-start
   checkpoint. Export its working runtime/checkpoint before any restore or
   uninstall. The correction does not require retrying 0.4.0.
2. Download **Wurm-Server.apk** from [v0.4.1-managed-preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.4.1-managed-preview).
   Install alongside the old apps and open the screen showing **0.4.1**. Its
   package is `io.github.russianranger.wurmlauncher.managedfix1`.
3. Import the existing **wurm-runtime-20260908-052948.zip** from Downloads. No new
   archive, Java download, root access or Termux command is needed. Keep at least
   **4 GiB free internal storage** for this separate installation's copies.
4. Select **Adventure**, heap **4096 MiB**, expected TCP **3724**. Ensure any
   existing server is stopped. Start Server, allow notifications and leave the
   app open during the first startup attempt.
5. Look for `HEAP_TAGGING_OFF` with `after=0x00`, `NETWORK_OK`, `PREFLIGHT_PASS`,
   then progress beyond `Loading servers`. `TCP_READY` / Running is the startup
   target. It is still not proof of a playable Wurm client connection.
6. Export **wurm-server-report.txt after the first attempt**, including failures,
   and send it for review. If Running, use normal Stop Server, wait for exit, and
   export the report again. Test restart/save persistence only after normal stop;
   forced termination does not establish saving.
7. On failure, retain the report and export the working runtime and before-start
   checkpoint before using explicit restore. A preflight failure opens no world.

The new package is necessary because current CI development signing is ephemeral;
it leaves 0.4.0's private data intact. This is not automatic data migration or a
long-term upgrade/signing solution. Keep earlier apps until their data is exported.

## Verification and remaining device gates

Host fixtures exercise opt-out success, unknown policy, allocator refusal, failed
allocation, residual tags and the unchanged default. They assert failed policy
never proceeds to the simulated Java load. A real host-JVM test exercises the
loopback network exchange. Kotlin tests check that only preflight receives the
network diagnostic flag, alongside existing workspace/recovery/control tests.

CI compiles the actual ARM64 runner with NDK 26.1.10909125, compiles/tests/lints the
Android variants, and verifies APK runtime hashes, diagnostic classes, opt-out
markers, source-backed POC bytes and notices. These checks do not substitute for
the Thor: allocator behavior, progress past this abort, TCP readiness, safe save/
reopen, item SQL paths and background survival remain physical-device gates.

## Every file changed in the correction

Launcher names below are relative to
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.

| File | Change |
| --- | --- |
| `runtime-probe/native/heap_compat.h` | Public runner entry point and injectable allocator hooks for host verification. |
| `runtime-probe/native/heap_compat.c` | Explicit child-only Bionic opt-out, allocation-tag diagnostics and failure gate. |
| `runtime-probe/native/jvm_runner.c` | Apply requested policy before loading Java. |
| `scripts/prepare-jvm-probe.py` | Compile the shared native helper into the runner. |
| `runtime-probe/src/probe/NetworkProbe.java` | Interface diagnostics, localhost resolution and bounded TCP loopback exchange. |
| `runtime-probe/src/probe/RuntimeProbe.java` | Optional networking test before the final probe-success marker. |
| `ManagedLaunch.kt` | Enable the network test in managed preflight only. |
| `ManagedServerController.kt` | Supply child heap policy and require networking success before Wurm. |
| `ManagedActivity.kt` | Identify the 0.4.1 compatibility build on screen. |
| `ManagedSession.kt` | Identify the new version in exported reports. |
| `app/build.gradle.kts` | Version 0.4.1 / code 7 and separate `.managedfix1` package. |
| `scripts/verify-managed-apk.py` | Verify native compatibility markers and Java 17 network diagnostic class. |
| `tests/native/heap_compat_runner.c` | Injectable native allocator-policy test harness. |
| `tests/test_heap_compat.py` | Success/default/failure behavior and no-load-on-failure checks. |
| `tests/test_network_probe.py` | Run the actual Java localhost/loopback probe on the host. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedWorkspaceTest.kt` | Check diagnostic/server argument separation. |
| `.github/workflows/android.yml` | Publish immutable 0.4.1 APK, source companions and this guide with checksums. |
| `README.md` | Current report outcome and new download/test entry points. |
| `docs/RELEASE_MANAGED_PREVIEW.md` | Corrective release notes and next test. |
| `docs/MANAGED_SERVER_TEST.md` | Update current install/version/preflight instructions. |
| `docs/THOR_NATIVE_HEAP_FIX.md` | Report analysis, compatibility scope, complete file inventory and device procedure. |

No POC file, proprietary Wurm file, SQLite patch/input, JRE binary pin or runtime
source-build recipe changed. Existing 0.4.0 functionality and project structure
are retained; client execution remains future work.
