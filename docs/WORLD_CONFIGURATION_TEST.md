# Wurm Server 0.6.0: world and configuration observation

**Physical acceptance passed, 2026-09-08.** The user confirmed the world report
survived app reopening. The reports identify open Adventure maps, active SQLite
files under `localhost/sqlite`, owned TCP listeners and normal exit 0.
See [THOR_WORLD_PASS.md](THOR_WORLD_PASS.md) for the exact evidence and remaining
questions. The procedure below remains reproducible; this accepted run need not
be repeated.

The next milestone adds **View world/configuration report** and **Export
world/configuration report**. Start automatically records a new report for that
launch; Restart creates a new launch identity after the previous child exits.
View/export work while running or stopped, and the saved report survives app
closure. These controls do not edit settings or start an extra Wurm process.

The [0.5.0 storage test passed](THOR_STORAGE_PASS.md): changes to four Adventure
maps and three databases under `localhost/sqlite` persisted after normal Stop
and app reopening. This milestone identifies the active layout more precisely
before configuration editing, world switching or a gameplay-save test.

## Evidence collected

| Report evidence | Meaning and limits |
| --- | --- |
| Runtime, working-copy ID, world, launch ID and timestamps | Identifies the specific launch. The report is a saved observation, not live status. |
| Requested heap / expected readiness TCP | Launcher settings; expected TCP does not configure Wurm's bind port. |
| `CONFIG_FILE`, `CONFIG_LITERAL` | Before-start file identities and limited literals from root, selected-world and `localhost` `wurm.ini`. Presence does not prove Wurm loaded that candidate. |
| `GAME_FOLDER`, `GAME_FOLDER_SET` | Recognized path and selection confirmation from the unchanged POC. |
| `JDBC_OPEN` | Wurm/Flyway's SQLite URL resolved relative to the working runtime. No database is opened by the reporter. |
| `OPEN_FILE`, `MAPPED_FILE` | Map/database paths observed in this child's file descriptors or memory mappings. A snapshot; files and mappings can change. |
| `SHIM_PORTS` | Values passed to the synthetic Steam shim. A query value is not proof a query socket exists. |
| `LOOPBACK_READY` | Existing controller's TCP connection after POC initialization; not LAN reachability or Wurm login. |
| `TCP_LISTEN` | Optional proc TCP snapshot matched to this child's socket inodes, excluding unrelated processes. |
| `TCP_UNAVAILABLE`, `FD_UNAVAILABLE`, `MAPS_UNAVAILABLE` | Observation unavailable; never interpreted as no listener/data or a default configuration. |
| `SNAPSHOT_BEGIN` / `SNAPSHOT_END` | Child observation began/completed. Completion can include unavailable sections. |
| Last recorded lifecycle | Preparing, observed running, stop or exit with timestamp. Historical after process death. |

Configuration inspection covers three known candidate files, at most 64 KiB each,
and exact single-line assignments for `DB_HOST`, `DB_PATH`, `DB_PORT`, `USE_SQLITE`,
`INTERNAL_IP`, `INTERNAL_PORT`, `EXTERNAL_IP`, `EXTERNAL_PORT` and `QUERY_PORT`.
Only restricted literal characters are shown. No general INI schema is inferred;
escapes/continuations are not interpreted. Passwords, accounts, raw configuration
and database rows are omitted. Absent keys remain unknown. Paths and local
listener addresses are included for diagnosis. Session reports retain raw Wurm
logs and should be reviewed separately before sharing publicly.

The child reads only its own proc files. TCP tables cover the network namespace,
so a listener is accepted only when its inode was observed in this child's FDs.
See the kernel documentation for [proc](https://docs.kernel.org/filesystems/proc.html)
and [TCP fields](https://docs.kernel.org/networking/proc_net_tcp.html).
Android 10 and later [restrict proc network access](https://developer.android.com/about/versions/10/privacy/changes#proc-net-filesystem),
so the probe handles `TCP_UNAVAILABLE` explicitly. **The actual 0.6.0 Thor run
successfully read these tables**, observing owned listeners on `[::]:3724` and
`[::]:48020`. Other devices may deny access; missing listener evidence then stays
unknown. A bound listener alone does not establish LAN reachability. No root or
permission bypass is attempted.

Observation runs on a daemon thread separate from the STOP reader, after readiness.
Inspection failure does not replace server status or prevent normal Stop. Scans
have file/line/output bounds; the Java probe never opens a game file. Kotlin reads
configuration before startup and writes an atomic bounded report outside the
runtime. Evidence is replaced per launch. Restoring/different working copies or
changing the selected world labels the saved report historical.

## Exactly what to copy, install and run on the AYN Thor

1. In **0.5.0**, use **Stop Server** and wait for Stopped. **Export working runtime
   ZIP** to Downloads as `wurm-working-runtime.zip`. Keep 0.5.0 installed with
   its data, and retain the ZIP.
2. Install **Wurm-Server.apk** from
   [v0.6.0-world-preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.6.0-world-preview).
   It installs alongside 0.5.0 as `io.github.russianranger.wurmlauncher.worldpreview`.
   Open **0.6.0**, import that stopped working ZIP, select **Adventure**, heap
   **4096 MiB**, expected TCP **3724**. Keep at least **4 GiB free internal
   storage** for this tested runtime's import, working copy and checkpoint.
3. **Start Server** and wait for Running. **View world/configuration report**.
   Expect `GAME_FOLDER Adventure`, `JDBC_OPEN localhost/sqlite/...`,
   `SHIM_PORTS game=3724 query=27016`, `LOOPBACK_READY` and `SNAPSHOT_END`.
   Open/mapped files should help identify Adventure maps and active databases.
   Denied proc evidence is useful diagnostic output; do not change permissions.
   If pending, close/reopen the report after a few seconds. The dialog is a
   snapshot, not an automatically refreshing view.
4. **Stop Server**, wait for Stopped, then close/reopen 0.6.0.
   **View world/configuration report** should retain the launch ID and observations
   with final child exit recorded. Don't Start again before exporting: a new
   launch replaces the report.
5. Export **world/configuration report** as **`wurm-world-report.txt`** and
   **session report** as **`wurm-server-report.txt`**. Send those **two files**.
   **A Storage report is not needed for this milestone.**

If startup fails, export those same reports with whatever evidence is available.
If `INSPECT_REQUESTED` appears but no `SNAPSHOT_END` arrives, retain the Session
report for diagnosis; normal Stop remains available. Don't restore a checkpoint
just to make a diagnostic marker appear.

No root, Termux command, separate Java installation, new storage baseline or
proprietary-file upload is needed. Signing remains the existing ephemeral CI
debug key, so the separate package and stopped ZIP transfer preserve the tested
installation. Seamless signed updates remain later work.

## Implementation and checks

POC source/base64/classes, JRE 17.0.20 pin, native heap compatibility, SQL-patched
input hashes, native lock, Start/Stop/Restart and client groundwork are preserved.
Only the control helper adds INSPECT; it still invokes the exact packaged POC.

The 28 host tests pass, including real JVM open-file/listening-socket observation,
synthetic IPv4/IPv6 and foreign-socket filtering, unavailable proc handling,
INSPECT followed by normal Stop, and existing SQLite/WAL and lifecycle tests.
Kotlin compilation and seven focused workspace/report tests pass locally. Report
tests cover source-byte preservation, credential exclusion, path boundaries,
reopening, historical scope and output bounds. The release workflow builds,
unit-tests and lints all three variants and verifies packaged runtime/POC/helper
identities and APK signing before publication.

Use the existing [managed build instructions](MANAGED_SERVER_TEST.md#build-and-verification).
The steps above now passed on the Thor, including proc access and the observed
map/database layout. Which identical INI candidate Wurm loaded remains unknown;
host fixtures cannot qualify other devices. No gameplay or item-SQL test is
claimed. The next step is a supported external client/admin connection and a
specific change verified across restart.

## Every file changed in 0.6.0

Kotlin filenames are relative to
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/` unless noted.

| File | Change |
| --- | --- |
| `ManagedWorldReport.kt` | New per-launch evidence, candidate configuration inspection, atomic persistence and historical labels. |
| `ManagedActivity.kt` | View/export controls, 0.6.0 identity, precise import/report labels. |
| `ManagedServerController.kt` | Capture before launch, observe startup, request child snapshot after readiness, record lifecycle. |
| `ManagedSession.kt` | 0.6.0 report identity and updated persistence wording. |
| `ManagedWorkspace.kt` | World report path outside game runtime. |
| `runtime-probe/src/server/WorldProbe.java` | Bounded child-only proc file/map/socket observation; explicit unavailable results. |
| `runtime-probe/src/server/ManagedServerMain.java` | Asynchronous INSPECT alongside STOP. |
| `runtime-probe/src/persistence/StorageAudit.java` | Report version label only. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedWorldReportTest.kt` | Report privacy, persistence, scope and bounds tests. |
| `tests/test_world_probe.py` | Synthetic and real process observation tests. |
| `tests/test_managed_bootstrap.py` | Compile both helpers; test INSPECT followed by Stop. |
| `scripts/verify-managed-apk.py` | Require Java 17 WorldProbe class. |
| `app/build.gradle.kts` | Version 0.6.0/code 9 and `.worldpreview` package. |
| `.github/workflows/android.yml` | Publish immutable 0.6.0 APK, guide and checksums after verification. |
| `README.md` | Current milestone, download and test links. |
| `docs/IMPLEMENTATION_PLAN.md` | Implemented observation and remaining device questions. |
| `docs/MANAGED_SERVER_TEST.md` | Route current testing to 0.6.0; retain lifecycle history. |
| `docs/RELEASE_MANAGED_PREVIEW.md` | Release description, migration and exact reports to export. |
| `docs/WORLD_CONFIGURATION_TEST.md` | This guide, scope and complete file inventory. |
