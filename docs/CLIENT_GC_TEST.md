# 0.10.12: isolate the client GC abort

## Physical evidence from 0.10.11

The paired server reports (`wurm-server-report (2)(1).txt` and `(3)(1).txt`)
refer to the same session. The verified position overlay is active; the first
export observes Running and TCP 3724 after the client abort. The second records
a requested shutdown, creature saves, closed databases and exit zero at
2026-09-09T15:42:17Z, after 59.4 seconds. Neither report contains the earlier
`near "DUPLICATE"` error. This qualifies that fix for this run and demonstrates
server survival; it does not prove all gameplay saves or a successful login.
The retained OFF-level banner is Wurm's logging-start message, not a fatal error.

The client starts its real entry stage, displays the splash and aborts at
15:41:31Z. Android supplies a matching PID/UID tombstone: **SIGABRT**, Scudo
**corrupted chunk header**, crashing thread **GC Thread#1**, with the last
native frame `BasicHashtable<(MEMFLAGS)5>::free_entry`. This time the server is
still alive and the app has not hit its startup timeout or requested client Stop.

The pinned OpenJDK source maps memory flag 5 to `mtGC`, instantiates this
hashtable for GC bookkeeping, and uses it for G1 compiled-code roots:
[allocation types](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/hotspot/share/memory/allocation.hpp),
[hashtable allocation/free](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/hotspot/share/utilities/hashtable.cpp),
[G1 code roots](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/hotspot/share/gc/g1/g1CodeCacheRemSet.cpp).
This locates the **detection/free path**, not the earlier write or race responsible
for corruption. The native stack ends there, so a precise upstream bug is not
identified. The last unfinished shader query (program 45, uniform 28) was on a
different thread and is not proof that this query caused the abort.

The previous heap-tagging opt-out is active. The supplied address has unusual
upper bits, but neither masking pointers nor bypassing Scudo is justified.
[Android's pointer-tagging guidance](https://source.android.com/docs/security/test/tagged-pointers)
describes invalid-pointer/tag misuse; it does not establish the origin here.
Inspection of the pinned GL4ES/LWJGL query path is not a reproduction of a native
write corrupting G1 metadata. Graphics and buffer-cleanup changes are deferred
until the collector comparison or subsequent native evidence supports them.

## Implemented experiment

1. **Only actual client entry** selects `-XX:+UseSerialGC`. Its 32 MiB initial
   and 1,024 MiB maximum heap are retained. The server and existing graphics,
   window/input, inventory and compatibility tests keep their previous collector
   selection. This removes the G1-specific code-root path from this client
   attempt, but is an **experimental compatibility workaround**, not a proven
   repair for the underlying memory corruption. Serial collection can cause
   longer pauses; responsiveness needs a physical test. Both collectors are
   [supported HotSpot options](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html).
2. The child logs and checks **actual collector MXBean names** before Wurm
   initializes. Unexpected selection fails explicitly. Standard GC/init/safepoint
   logs include UTC time, PID and native thread ID in the existing client report.
   No arbitrary command field, GC disabling, heap-check suppression, allocator
   replacement, runtime rebuild or server flag change is introduced.
3. **Client tab → JVM Memory Test** runs two separately owned children, G1 then
   Serial, each at 32–256 MiB heap. It needs no imports and loads only the authored
   helper JAR. It does not load Wurm, LWJGL, EGL, GL4ES or Steam, and does not
   connect to a server. Accidental game/graphics classpath entries are rejected.
4. Each child requests 256 MiB of bounded heap churn and 32 MiB of direct-buffer
   churn over sixteen rounds, checks data sentinels and arithmetic, warms methods
   from 64 disposable class loaders, and requests reclamation. It reports observed
   GC counts, unloaded classes, compiler time and elapsed time. No Unsafe or
   manual native-memory freeing is used. The internal budget is 75 seconds;
   existing parent ownership, Stop, two-minute stage timeout and crash capture
   apply. If G1 fails, the comparison still attempts Serial unless the user stops.
5. Exit zero alone cannot pass the memory gate: the supervisor also requires
   its matching completion marker. Both results appear in the Client tab and
   **Export Client Report**. This bounded test can reveal a runtime-only failure;
   passing cannot exclude a runtime bug under a different workload, graphics
   corruption or an intermittent race. Export it before the actual client test.

The current POC, position/item SQLite patches, world controls, OpenJDK/native
pins, all graphics API/window/native assets, Steam shim, controller mappings and
login flow are retained. No proprietary files are committed or bundled.
Gate 4/5 full rendering, login/world entry and audio remain unqualified.

## Exact Thor test: keep the working 0.10.11 server

1. Install **Wurm-Server.apk** from **v0.10.12-client-gc**. Confirm **0.10.12**
   is displayed. Code 26 uses separate package
   `io.github.russianranger.wurmlauncher.clientgc`; keep 0.10.11 and its files.
2. In 0.10.12 open **Client tab → JVM Memory Test**. No game ZIP or running server
   is needed for this step. Stay on this screen until both G1 and Serial results
   appear. Each child has a two-minute outer limit; Stop Client cancels the test.
3. **Export Client Report** immediately. Save it as `wurm-memory-0.10.12.txt`.
   If **Serial fails**, send this report and stop here. If G1 fails but Serial
   passes, keep that report and proceed with the Serial client attempt.
4. Import your **same complete client ZIP** into 0.10.12. No new client files,
   manually patched JAR, server ZIP, PC, root or Termux are required for this
   test. All proprietary JARs/assets still come from your legally owned import.
5. In **0.10.11**, start the existing Adventure server and wait for **Running**.
   Leave that server running. In **0.10.12**, choose **Start Local Game**; it
   consumes the existing listener at **127.0.0.1:3724**. The log should identify
   the server as **external**. Do not also start another server.
6. If the world appears, screenshot it and try movement for 60 seconds. If it
   remains Connecting for 60 seconds, screenshot and export. If it fails earlier,
   export immediately without Retry. Before another attempt, export **Client
   Report from 0.10.12** as `wurm-client-0.10.12.txt`.
7. Export **Server Session Report from 0.10.11** separately. The new app cannot
   read another app's server log; its embedded server report may be empty.
   Send the memory report, client report, server session report and screenshot.
8. Stop the client in 0.10.12 and the server separately in 0.10.11 if still
   running. Export the server session report again after Stop if it reports a
   failure. Keep the checkpoint and original import.

Expected memory markers: `COLLECTOR expected=g1 actual=[G1 Young Generation, G1 Old Generation]`,
`MEMORY_PROBE_PASS collector=g1`, the corresponding Serial collector names
`[Copy, MarkSweepCompact]`, and `MEMORY_PROBE_PASS collector=serial`.
For the actual Wurm entry, expect `COLLECTOR expected=serial` and `Using Serial`.
These are runtime facts, not login-success indicators.

## Validation and limitations

**101 host tests and six targeted Kotlin tests pass.** Two host checks use the
private server JAR to recheck the retained position patch; CI skips those two.
The pinned ARM64 runtime image contains the required management APIs and libraries.

The host tests execute both collectors with the production logging flags and
real memory/JIT/class-loader workload, reject mismatched collectors before work,
and reject accidental game or LWJGL inputs before class initialization. Kotlin
policy tests keep the client-only change scoped and retain existing stages.
The existing managed-server launch/control tests remain in the full suite.
These tests cannot reproduce or qualify Android/Adreno behavior; the next Thor
report is required to assess the experimental collector change.

All Android variants must build, unit-test and lint before release. APK checks
require the new JVM helpers, pinned management/native libraries, source-backed
POC and no proprietary game classes. Release verification also compares graphics
assets and server helper bytes with 0.10.11.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish immutable 0.10.12; attach/checksum this guide. |
| `README.md` | Point to the current experiment and physical server result. |
| `app/build.gradle.kts` | Version 26 / 0.10.12; separate clientgc package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Expose JVM Memory Test; explain the current client experiment. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientJvmPolicy.kt` | Scope collector flags/logging and define explicit comparison stages. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientService.kt` | Allow the memory test through existing foreground ownership. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Run isolated collector comparison; select entry policy; require probe markers; export current evidence. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Display 0.10.12; rendering controls retained. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Display 0.10.12; server control implementation retained. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ClientJvmPolicyTest.kt` | Check entry-only flags, comparison stages and retained diagnostic policies. |
| `docs/CLIENT_GC_TEST.md` | Evidence, experiment limits, exact Thor steps and every changed file. |
| `docs/CLIENT_INTEGRATION.md` | Record GC detection evidence and the current bounded client gate. |
| `docs/IMPLEMENTATION_PLAN.md` | Record the collector experiment and outstanding rendering/login qualification. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release scope and device instructions. |
| `runtime-probe/src/client/ClientBootstrap.java` | Verify actual collector before entry/comparison and dispatch memory probe. |
| `runtime-probe/src/client/ClientJvmDiagnostics.java` | Report/check actual collector names, JVM identity, heap and GC counts. |
| `runtime-probe/src/client/ClientMemoryProbe.java` | Authored allocation/direct-buffer/JIT/class-loader/reclamation workload. |
| `scripts/verify-managed-apk.py` | Require new Java 17 helper/kernel/loader classes. |
| `tests/test_client_memory.py` | Execute G1/Serial and reject collector/classpath mismatches. |
