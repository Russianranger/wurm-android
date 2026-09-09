# 0.10.5: Java 17 buffer cleanup

## What the Thor confirmed

The supplied `wurm-client-report (2)(2).txt` is from **0.10.4**. Both
`OFFSCREEN_FBO_PASS` and `OFFSCREEN_REQUIREMENT_PASS` appear on the Adreno 740.
Wurm proceeds through mouse initialization into splash texture loading. The
next fatal error is `IllegalAccessError` in `BufferUtil.deallocate`, called from
`TextureLoader.initTexture` and `StartupRenderer.assignSplash`.

The FBO correction is now physically confirmed. The report still has **zero
submitted game frames**, no login and no world entry. The OpenAL library failure
is caught by Wurm's existing silent-mode fallback and is not this fatal error.

## Diagnosis and smallest correction

The inspected client has two Java 8-era classes:

| Class | Original SHA-256 |
| --- | --- |
| `com/wurmonline/client/util/BufferUtil.class` | `037c786dd552cf0b3a6a22c1c9761296528564ebca80171605e2446bac563076` |
| `com/wurmonline/client/util/BufferUtil$Cleaner.class` | `bba71961402b766edddde5b164e8e170b3b2091af632532d83dd8605f6573d8d` |

They access `sun.nio.ch.DirectBuffer` and expect
`cleaner(): sun.misc.Cleaner`. On the bundled Java 17 runtime, that method returns
`jdk.internal.ref.Cleaner`. A host probe using the actual imported client
reproduces the Thor's access error; adding exports alone then reproduces
`NoSuchMethodError: sun.misc.Cleaner sun.nio.ch.DirectBuffer.cleaner()`.

[Oracle's Java 17 migration guide](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html)
describes the targeted `--add-exports` option for older libraries using internal
APIs. This client also needs the return-type relocation established by inspecting
its bytecode and the runtime interface. `--illegal-access=permit` cannot solve it.

The existing **prepare-graphics** stage now generates a three-class private
session overlay: the unchanged offscreen engine adapter plus these two classes.
In each buffer class, exactly one Cleaner owner constant and one return-type
constant are relocated. The method instructions, branches, allocation/accounting,
null handling and attachment cleanup remain unchanged. The nested Cleaner must
also be adapted because float/int/double views free their parent allocation there.

| Class | Adapted SHA-256 |
| --- | --- |
| `BufferUtil.class` | `10ad55e38c07f78e7df4756f982d772a12cae34e5ff7d5236134adf77dfecaa1` |
| `BufferUtil$Cleaner.class` | `6443c2e21b3510615cb71f2caeea17051953ffb908c24c388553bdca428b1b78` |

Only the **client entry JVM** receives:

```text
--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED
```

No blanket module opens, patched Java runtime or GC-only cleanup is introduced.
The two exports are checked before engine initialization. After the existing
profile/display setup, an in-process preflight runs the real imported allocation
and deallocation methods for byte, float, int and double buffers. It checks the
allocation counter before/after cleanup and logs each result before game launch.

All three overlay classes must reverse to their pinned original hashes and be
selected by the entry classpath. Unknown/tampered inputs fail visibly. The raw
import remains unchanged, the overlay stays inside the private session and is
removed by existing cleanup. No proprietary class/JAR, report or disassembly is
committed, APK-bundled or exported. Public builds require no game files.

The server runtime/import, POC, SQLite fix, graphics native sources, FBO check,
shader-cache setting, Steam shim and controller mappings retain their behavior.

## Verification and limits

- **76 automated tests pass.** New authored fixtures create an old Cleaner ABI,
  reproduce its linkage failure on Java 17, relocate it back, execute cleanup of
  real native allocations and verify byte-identical reversal. They check all four
  buffer kinds, heap/null handling, unrelated constants, missing exports and hash
  rejection without game files.
- With the actual supplied client, both original errors reproduce. The three-class
  production overlay passes integrity and classpath verification. **16 repeated
  preflights (64 allocations) restore native direct-buffer count and bytes exactly**,
  measured through the host runtime's `BufferPoolMXBean`. This also exercises the
  real nested Cleaner attachment path for typed views.
- CI builds, tests and lints all Android variants and verifies signing, runtime/
  graphics hashes, required helper classes and exact source-backed POC bytes.

**Gate status:** Gate 4's measured FBO check is physically passed. This Java 17
buffer correction is host-tested and awaits Thor acceptance. Full Wurm rendering,
audio, local authentication/ticket acceptance and world entry remain unqualified.
The preview still has a two-minute startup limit and bounded frame readback.

Optional developer reproduction uses `scripts/ProbeClientBuffers.java`, the built
runtime helper and graphics/compatibility JARs, and a private client import. Run
`client.ClientBootstrap prepare-graphics` with the original client and an absent
`-Dwurm.client.offscreenOverlay=/absolute/scratch/overlay.jar`; then prepend that
overlay to the client classpath. Run `ProbeClientBuffers` with the two exports
above and the same overlay property. Use the existing host native library mapping
and environment from `scripts/test-window-host.py`, including `LIBGL_NOPSA=1`,
headless mode, explicit local/offline properties and a disposable working/user
home directory. Expect `WURM_BUFFER_NATIVE_RELEASE_PASS rounds=16 kinds=4`.
The public `client.ClientBootstrap buffers` diagnostic exercises one preflight;
it does not start a game or connect to the server.

## Exact next AYN Thor test

1. Keep your working **0.6.0 server app** and Adventure installed. Download
   [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.5-client-buffers/Wurm-Server.apk)
   from [release 0.10.5](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.5-client-buffers)
   and install alongside it. Confirm **0.10.5-managed-preview**, code **19**,
   package `io.github.russianranger.wurmlauncher.clientbuffers`.
2. Open **0.10.5 → Client → Import Client ZIP**. Import the same complete ZIP:
   `client.jar`, `common.jar`, full `lib/`, full `packs/` including `graphics.jar`,
   `pmk.jar` and `sound.jar`, and the remaining original assets. This separate
   package needs its own import because preview CI signing currently changes.
3. Start **Adventure** in the working server app and wait for its listening game
   port. Return to **0.10.5 → Client → Start Local Game**, targeting
   **127.0.0.1:3724**. No PC, root, Termux or manual Java flags are needed.
4. Watch for a Wurm screen. If one appears, capture it and try controls briefly.
   You do not need to repeat the triangle/controller diagnostic. Audio may remain
   silent; a splash/window alone does not prove connection or world entry.
5. After failure, timeout or **Stop Client**, select **Export Client Report**.
   Send **wurm-client-report.txt** and any screenshot. Use **Client Report**,
   rather than the server Session, Storage or World reports.

New markers: `BUFFER_PATCH_VERIFIED`, `BUFFER_PATCH_READY`, `BUFFER_PATCH_ACTIVE`,
`BUFFER_MODULE`, `BUFFER_RUNTIME_ABI`, four `BUFFER_RELEASE_PASS` lines and
`BUFFER_PREFLIGHT_PASS`. These should precede `ENTRY_INVOKE` and the already
confirmed FBO markers. Report the next original exception after those, or any
visible game screen. An unsupported-class hash means a different client build;
send that report before further retries.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.5 and attach the new guide/checksum. |
| `README.md` | Point to the current test and confirmed FBO progress. |
| `app/build.gradle.kts` | 0.10.5, code 19, separate clientbuffers package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Client-entry module exports and updated version/gate report. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the buffer startup fix. |
| `docs/CLIENT_BUFFERS_FIX.md` | Diagnosis, hashes, tests, limits and file inventory. |
| `docs/CLIENT_INTEGRATION.md` | Current buffer integration and remaining gates. |
| `docs/CLIENT_THOR_TEST.md` | Current test pointer. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test pointer. |
| `docs/IMPLEMENTATION_PLAN.md` | Confirmed FBO gate and buffer architecture. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and exact device steps. |
| `graphics-compat/README.md` | Current gate status and scope. |
| `runtime-probe/src/client/ClientBootstrap.java` | Module precheck and standalone buffers diagnostic. |
| `runtime-probe/src/client/ClientBuffers.java` | Pinned Cleaner ABI relocation, module checks and four-kind preflight. |
| `runtime-probe/src/client/ClientGraphicsPatch.java` | Generate/verify all three private overlay classes. |
| `runtime-probe/src/client/DirectClientLaunch.java` | Run the real buffer preflight after profile/display preparation. |
| `scripts/ProbeClientBuffers.java` | Optional real-client native-memory regression. |
| `scripts/verify-managed-apk.py` | Require the authored ClientBuffers helper in the APK. |
| `tests/test_client_buffers.py` | Executable ABI/native-memory/hash/export fixtures. |
