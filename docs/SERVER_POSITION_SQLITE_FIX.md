# 0.10.11: SQLite position saving and retained first errors

## What the latest Thor reports prove

The paired 0.10.10 reports from 2026-09-09 show the server shutting down by
itself at 15:09:14Z, about 19.5 seconds after its child started. Its Timer-5
thread calls `Server.shutDown` from the Throwable catch in `Server.run`.
`requestedStop=false`. The client receives local authentication acceptance,
sends its login payload, then stops because its owned server has exited.
The client exit 134 follows that supervisor action; this pair does not establish
a spontaneous client graphics crash or a successful login.

There are 475 position-save failures with SQLite's `near "DUPLICATE": syntax
error`. These occur during shutdown. The initial fatal Throwable is missing:
the repeated errors rotated the console file and displaced the start of the
session. Fixing this confirmed SQL defect is necessary, but **we cannot yet say
it caused the initiating server failure**. Another failure may remain.

The supplied server.jar exactly matches the app's existing Thor pin:
`9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06`.
Its loaded `creatures/CreaturePos.class` contains one MySQL upsert constant used
by both synchronous player and creature position saves. The earlier item fixes
are present in the root item classes. Archival `target/classes/` duplicates in
the JAR are not the classes selected by the classloader and are not patched.

## Implementation

The existing managed preflight now calls `server.ServerPreflight`, which runs
the unchanged JVM/SQLite/network probe, then prepares `server-sqlite.jar` in the
disposable session. It contains one class derived privately from the user's
server.jar. The class must match SHA-256
`b188c29d94e83b8d5a9487267469695b9a52208c57a2f0eea04de789afde9a21`.
Exactly one UTF-8 SQL constant changes; executable methods, parameter binding,
stack maps, fields and all other bytes are retained. Unknown versions fail
before checkpoint/world startup. Imported and working server.jar bytes stay
unchanged, as do the POC, common.jar and item classes.

The statement keeps the same eight parameters in order: X, Y, Z, rotation,
zone, layer, bridge ID and Wurm ID. SQLite `ON CONFLICT DO UPDATE` replaces
MySQL `ON DUPLICATE KEY UPDATE`, using `excluded` values for the seven mutable
columns. Like the original, it responds to uniqueness conflicts. It preserves
existing row identity and omitted columns; it does not delete/reinsert rows.
See SQLite's [UPSERT semantics](https://www.sqlite.org/lang_upsert.html).
The existing personal-server UPDATE and scheduled updater paths remain intact.

The server classpath places this overlay before server.jar, retaining the POC
and pinned SQLite precedence. Before any Wurm initialization, the helper reverses
the constant substitution, verifies the exact original class hash and checks
that the classloader selected the overlay. The overlay is deleted with its
session. Missing, tampered or incorrectly ordered overlays fail explicitly.
Storage audits still invoke their original read-only helper and do not patch.

Separately, the JUL handler atomically stores the first four WARNING records and
first four SEVERE records, with independent budgets of 6,000 characters each,
in the app's `managed-first-errors.txt`. Both console and retained records use
the same credential omission and bounded cause/stack formatting. The file is
outside the world and disposable session, survives app reopening and console
rotation, and is included near the beginning of **Server Session Report**.
**Client Report** already embeds that report. A new server attempt resets the
capture; imports/exports and logging reloads do not erase it. Export before
retrying if a failure occurs. Capture is not guaranteed for native hard exits.

There are no client graphics, controller, login-packet, Steam, NPC or world-data
rewrites. Gate 5 remains incomplete until the Thor logs in and enters the world.

## Exact AYN Thor test

1. Leave the older apps installed. In **0.10.10**, wait until its server has
   stopped (the supplied report already shows it stopped). Export **before-start
   checkpoint ZIP**, saving `wurm-before-start.zip`. Keep the original app and
   its current files; this uses the checkpoint taken before the failed run,
   not the potentially partially saved result of that run. If no checkpoint is
   available, use your retained original prepared server runtime ZIP instead.
2. Download/install **Wurm-Server.apk** from release
   **v0.10.11-server-sqlite**. This is code 25, separate package
   `io.github.russianranger.wurmlauncher.positionsqlite`; it installs alongside
   0.10.10. Open it and confirm **0.10.11** is displayed.
3. Import the checkpoint ZIP on its Server tab and select **Adventure**.
   On its Client tab import the same complete, legally owned client ZIP used
   successfully to display the Wurm splash. Keep its full JAR/asset layout.
   No replacement server.jar, loose patched class, runtime download, PC, root
   or Termux command is needed. The attached server.jar was sufficient for
   engineering inspection; the APK still needs your full server runtime ZIP.
4. Keep every older app's server stopped so TCP 3724 is free. In **0.10.11**
   choose **Start Local Game**. This starts its own server and attempts the
   client connection to **127.0.0.1:3724**.
5. If it enters the world, take a screenshot and try movement for 60 seconds.
   If it remains at Connecting, wait about 60 seconds and take a screenshot.
   If it fails sooner, export immediately without Retry. Full rendering and
   gameplay are not claimed by the host tests.
6. Choose **Back to Client / Export → Export Client Report**. Also export
   **Server Session Report** from the Server tab. Send **both**, preferably
   named `wurm-client-0.10.11.txt` and `wurm-server-0.10.11.txt`, plus the screen.
   Do **not** substitute Storage Report or World Report. Export before closing
   the app or starting another attempt. The report headers must say 0.10.11.
7. Stop Client Test if still running; stop the server separately and wait for
   exit. Export Server Session Report again after Stop to capture any save
   failures. An exit code of zero alone is not proof of a save. Keep the original
   app/checkpoint if recovery is required; do not repeatedly run a failed world.

Expected server markers are `POSITION_PATCH_READY`, `SERVER_PREFLIGHT_OK` and
`POSITION_PATCH_ACTIVE`. The exported report begins with a separate first-error
section. Absence of the DUPLICATE error validates this specific fix; it does not
by itself validate login, database consistency or persistent gameplay saves.

## Verification

**97 host tests passed**, including both optional private-JAR checks; three
targeted Kotlin launch tests passed. CI discovers the same Python suite and
skips only the two private checks because proprietary inputs are not in git.

Authored fixtures verify byte-identical reverse patching, unrelated constant
preservation, invalid-hash/missing-rule rejection, unsupported production input,
and missing-overlay failure. Real SQLite tests exercise insert, update, all
eight bindings, rollback, preserved row ID/extra columns and no delete cascade.
A private optional test uses the actual supplied CreaturePos methods with authored
dependency fixtures and the pinned SQLite JDBC 3.53.2.1. Both original synchronous
save methods reproduce the DUPLICATE error; both patched methods insert/update
successfully, and the existing personal-server UPDATE path still passes.
The exact overlay/source hashes, wrong classpath order, tampering and the earlier
item SQL fixes are also checked. No proprietary code is included in the tests.

Logging tests exercise 1,000 warnings before the first severe error, a logging
reload, 1,000 later severe records, redaction, reset and a bounded retained file.
Kotlin tests verify the preflight, overlay/JDBC/POC classpath ordering and the
unchanged audit dispatch. CI runs the full tests, Android builds and lint before
publishing; APK checks require the new helpers and exclude bundled CreaturePos.

Optional private checks, from a development checkout with Java 17:

```sh
export WURM_TEST_SERVER_JAR=/private/path/server.jar
export WURM_TEST_SQLITE_JAR=/private/path/sqlite-jdbc-3.53.2.1.jar
python3 -m unittest discover -s tests -p 'test_server*.py' -v
```

Without the proprietary JAR, only those two private checks skip. Never add that
JAR, generated overlay, disassembly, reports or user data to git.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish immutable 0.10.11 APK; attach/checksum the new test guide. |
| `README.md` | Point to the current test and confirmed limitation. |
| `app/build.gradle.kts` | Version 25 / 0.10.11; separate positionsqlite package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Display 0.10.11. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Identify client export as 0.10.11. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Display 0.10.11. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the position fix preview. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedLaunch.kt` | Prepare private overlay; retain pinned classpath and audit dispatch. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedServerController.kt` | Require patch preflight marker and reset first-error capture per attempt. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedSession.kt` | Include retained first errors before rotating console history. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedLaunchTest.kt` | Test preflight, server classpath/property and audit dispatch contracts. |
| `docs/CLIENT_INTEGRATION.md` | Record the current Gate 5 dependency fix. |
| `docs/IMPLEMENTATION_PLAN.md` | Record bounded architecture and remaining physical gate. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Current release scope and user steps. |
| `docs/SERVER_POSITION_SQLITE_FIX.md` | Evidence, architecture, every change and exact Thor instructions. |
| `runtime-probe/src/server/ManagedServerMain.java` | Verify selected overlay before resolving Wurm classes. |
| `runtime-probe/src/server/ServerDiagnostics.java` | Initialize durable per-session first-error capture. |
| `runtime-probe/src/server/ServerLogHandler.java` | Retain bounded, sanitized first warnings/severe errors independently. |
| `runtime-probe/src/server/ServerPreflight.java` | Run existing probe, generate overlay, report completion before world start. |
| `runtime-probe/src/server/ServerSqlitePatch.java` | Guarded constant-only SQLite rewrite and reverse/classpath verification. |
| `scripts/verify-managed-apk.py` | Require new helpers and reject bundled proprietary position classes. |
| `tests/test_managed_bootstrap.py` | Compile new preflight dependencies in existing control fixtures. |
| `tests/test_server_diagnostics.py` | Test first-error retention under floods/reloads and redaction/reset. |
| `tests/test_server_sqlite_patch.py` | SQL/constant contract tests plus optional actual-class/JDBC probes. |

