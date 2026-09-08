# AYN Thor JVM/SQLite diagnostic: PASS

The owner's physical-device report dated **2026-09-08 12:02:40 UTC** confirms
that **Wurm Server JVM Test 0.3.2-jvm-probe** completed successfully on the Thor.
This is device evidence, separate from the host/CI regression tests.

| Check | Observed result |
| --- | --- |
| Device environment | Android 13, API 33, ARM64 |
| Native process identity | UID/eUID 10193; no root used |
| Launcher environment | `mustsetenv: FALSE` |
| JVM image layout | `JVM_IMAGE_PATH_OK` |
| Core module image | 81,720,691 bytes; expected SHA-256 verified |
| Java runtime | `17.0.10-internal`, OpenJDK 64-Bit Server VM, `aarch64` |
| Java threads/computation | `JAVA_OK` |
| SQLite engine | `sqlite.version=3.53.2` |
| SQLite persistence test | Create, insert, update, commit, close, reopen: `SQLITE_OK` |
| Overall probe | `PROBE_OK`, child exit 0, `RESULT: PASS` |

Both imported SQLite JAR hashes match the previously verified working Termux
inputs. See [the baseline and test procedure](JVM_PROBE_TEST.md) for their hashes.
The successful test runs through the APK's native child and embedded JRE; it
does not invoke Termux, a shell or `su`.

## Evidence identity

- [Tested release](https://github.com/Russianranger/wurm-android/releases/tag/v0.3.2-jvm-probe).
- Source commit: `f6b08fef118da60d290d9674f873b34d6b728d5c`.
- Published APK SHA-256: `37caac1bd8f92625b5d63ab301f90da11b49ae9c1663ad00fc690f94ddca0dbe`.
  This identifies the release asset; the device report itself does not calculate
  the installed APK's hash.
- Supplied report SHA-256: `689cb0a35726331afefe7e1f2947649145299417ff6d9b65bf37defe9a243c05`.
- The original report is not copied into this public repository. This record
  retains the test results without the generated app installation/run paths.

## What this closes, and what it does not

The earlier re-exec and boot-class-path failures are resolved on this device.
An APK-owned ARM64 Java process can execute Java code and use the owner's Android
SQLite JDBC/native JARs to persist a disposable database without root or Termux.

The test did not load `server.jar`, `common.jar`, the POC or any Wurm world.
It does not validate Wurm's item SQL patch, TCP 3724, client connectivity, server
shutdown hooks, world saving, a foreground service, screen-off behavior or the
4 GiB server heap. A normal exit of this short probe is not a Wurm save/stop test.
It also does not change the runtime provenance/maintenance requirements in
[RUNTIME_PROVENANCE.md](RUNTIME_PROVENANCE.md).

## Next implementation milestone

Build a protected managed-server preview using the existing importer, native
runner, source-backed POC and Android service/UI structure:

1. Add runtime/world backup export and use a recoverable working copy for the
   first server run. Keep imported proprietary files and SQL patches byte-identical.
2. Package a maintained Android ARM64 Java 17 build with explicit source/patch
   pins and notices, then repeat this Java/SQLite diagnostic on the Thor.
3. Launch the unchanged POC from the selected managed runtime/world through a
   foreground service. Add Start/Stop/Restart, bounded live logs and a runtime
   lock. Keep process-alive status distinct from TCP readiness. Restart must
   wait for the old process to exit before creating a replacement.
4. Verify the exact Wurm save/shutdown mechanism before claiming graceful saving;
   exercise GameFolder/database loading, the Steam shim, item insert/update SQL,
   TCP 3724 and a save/stop/restart cycle on the disposable world copy.
5. Establish durable APK signing before users accumulate live worlds that must
   survive app updates. The current diagnostic and import preview are separate
   development packages, not a migration mechanism for live world data.

The acceptance result for that milestone is a no-root Wurm server reaching its
game listener from imported files, with observable lifecycle control and verified
world persistence. Client execution remains outside this server milestone.

## Owner's next device action

No reinstall or repeat of the unchanged 0.3.2 diagnostic is required for this
successful result. Keep both existing apps, the passing report and
`wurm-runtime-20260908-052948.zip`. The original Wurm Server import preview still
has disabled managed Start/Stop/Restart controls; this diagnostic does not enable
them. A later managed-server APK will have its own copy/install/test instructions.
