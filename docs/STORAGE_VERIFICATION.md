# Wurm Server 0.5.0: stopped storage verification

The 0.4.1 Thor result now includes startup, same-working-copy reopen, Restart and
requested exit 0. The user also confirmed **five minutes in another app** and
**two minutes with the screen locked**, returning to Running after both. Those
are observed short background results, not an endurance or memory-pressure test.

The next smallest milestone adds evidence about the stored runtime: a persistent
baseline, comparisons after stopping/reopening, and SQLite checks on disposable
copies. Existing POC bytes, SQLite-patched Wurm inputs, Java 17.0.20, child-only
heap compatibility, foreground lifecycle and Client groundwork are retained.

## New controls and how they work

- **Capture storage baseline** fingerprints every regular file in the stopped
  working runtime and checks candidate SQLite databases before accepting the
  snapshot. Replacing a baseline requires confirmation and clears the previous
  check reference. It changes no imported game file.
- **Check stored data** compares with that baseline and, when available, the last
  successful check. It repeats database checks and accepts a new last-check
  snapshot only on completion. Expected file changes are reported, not silently
  interpreted as a save failure.
- **View storage report / Export storage report** show or save file-change counts,
  up to 100 changed paths per comparison, database names, aggregate table/row
  counts and hashes. No table rows or configuration contents are included.
  Session reports still contain raw Wurm output and native-launch diagnostics.

The same foreground service, wake lock, single-operation owner and native workspace
lock protect the audit. Start/Restart/import/restore cannot overlap an audit.
Normal Stop cancels the audit; it does not need Wurm shutdown because Wurm is not
started. Each audit has a 15-minute limit. Pending reports/snapshots are published
with atomic moves, so an incomplete file is not presented as a completed result.
The UI/report states when the latest audit did not complete.

The fingerprint scope is the **complete runtime**, including JARs, configuration,
maps, other imported worlds and `localhost/sqlite`. Wurm's earlier log used that
database path, so checking only `Adventure/sqlite` would miss relevant files.
Comparison scope includes the working-copy identity and selected world; a restore
or world switch requires an explicit new baseline. The baseline lives outside
the game runtime and persists across app closure. It is not exported inside the
working-runtime ZIP or imported from another installation.

The handwritten Java 17 audit entry point uses only the helper JAR and the pinned
SQLite JARs. Server/common/POC classes are not on its classpath. It discovers
database candidates by SQLite header or `.db`/`.sqlite`/`.sqlite3` extension.
For each database it copies the main file and any WAL, SHM and rollback-journal
sidecars into disposable audit storage and verifies the copied bytes. SQLite
opens **only those copies**, using [URI `mode=ro`](https://www.sqlite.org/uri.html),
and enables query-only mode. It runs
[`PRAGMA quick_check`](https://www.sqlite.org/pragma.html#pragma_quick_check) and
counts ordinary schema table rows. quick_check is a partial structural check; it
does not establish foreign-key validity, full index consistency or game semantics.

A second complete source fingerprint must match the first before accepting any
snapshot. Corruption, required recovery of a copied journal, unsupported schema,
low storage, symlinks, timeout or changed source bytes cause a failed/incomplete
audit. The report does not equate every SQLite exception with corruption. Game
files stay untouched; no repair or SQL patch is attempted. Disposable DB copies
are removed after each check; leftovers from a killed audit are removed by the
next audit. Limits are 100,000 regular files, 512 database candidates and 2,048
user tables per database. One database plus its sidecars is copied at a time.

## Exactly what to copy/install/run on the AYN Thor

1. In the working **0.4.1** app, use normal **Stop Server** and wait for Stopped.
   Use **Export working runtime ZIP** to save `wurm-working-runtime.zip` in
   Downloads. Keep 0.4.1 installed with its data and retain this ZIP as a backup.
   Use the current working export for continuity, rather than the initial Termux
   ZIP if you want to carry forward changes from the tested runtime.
2. Install **Wurm-Server.apk** from
   [v0.5.0-storage-preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.5.0-storage-preview).
   It installs alongside 0.4.1 as package
   `io.github.russianranger.wurmlauncher.storagepreview`. Open the screen showing
   **0.5.0**, import the working ZIP, and select **Adventure**, **4096 MiB**, TCP
   **3724**. Keep at least **4 GiB free internal storage** for this runtime; larger
   worlds/databases need additional space. The audit needs one database/sidecar
   copy plus 128 MiB reserve in addition to existing import/checkpoint storage.
3. While stopped, tap **Capture storage baseline**, confirm, and wait for Stopped
   with the baseline-captured message. View/export the storage report. Expect
   `BASELINE_CAPTURED`, `DB_OK` entries, `FAILED=0` and `SOURCE_UNCHANGED`.
4. Start Server, wait for Running/TCP_READY, then use normal Stop Server and wait
   for Stopped. Tap **Check stored data** and wait for completion. Export
   **wurm-storage-report.txt** and **wurm-server-report.txt**. `BASELINE_DIFF` may
   be normal: logs and server databases can change during a run.
5. Close and reopen **0.5.0 while the server remains stopped**. Without starting,
   restoring, importing or capturing a new baseline, tap **Check stored data**
   again. Expect `LAST_CHECK_MATCH`: all recorded file bytes survived the app
   close/reopen unchanged. Export the storage report again and send that report,
   the previous post-stop storage report and the session report for review.

No root, new Java installation or Termux command is needed. If an audit fails,
export both reports and stop the audit test sequence; do not restore/replace the
baseline merely to make it pass. Existing server controls remain separate from
the audit result. The old 0.4.1 installation remains available.

Development signing is still an ephemeral CI debug key. The separate package
and user-driven working-ZIP import preserve the older installation and move a
stopped copy deliberately. Durable signing and seamless updates are subsequent
work; this milestone does not request or publish a private signing key.

## Interpreting the report

| Marker | Meaning |
| --- | --- |
| `BASELINE_CAPTURED` | A complete fingerprint and successful database-copy checks were accepted as the reference. |
| `BASELINE_MATCH` / `BASELINE_DIFF` | File bytes match or differ from the captured baseline; counts distinguish additions, changes and removals. |
| `LAST_CHECK_NONE` | First successful check after baseline capture. |
| `LAST_CHECK_MATCH` / `LAST_CHECK_DIFF` | Comparison with the last accepted check, including checks from an earlier app launch. |
| `DB_OK ... tables=... rows=... wal=...` | The copied database passed quick_check and aggregate row counting; WAL presence is reported. |
| `SOURCE_UNCHANGED` | All source file bytes matched before/after the database-copy checks. |
| `STORAGE_CHECK_COMPLETE` | The audit completed and published a new last-check snapshot. It is not a gameplay-save certification. |
| `AUDIT_FAILED` / `DB_CHECK_FAILED` | The audit could not qualify a snapshot. Prior accepted snapshots remain available; inspect the current failure report. |

To prove a particular Wurm change was saved, an existing supported external
client/admin route must make an identifiable change and verify it after Wurm
restarts. The Android client remains groundwork. File hashes and aggregate row
counts provide evidence for that test but cannot replace it. An unchanged hash
can be legitimate when no persistent game data changed.

## Build and verification

Use the existing Linux x86_64 Gradle/SDK/NDK workflow described in
[MANAGED_SERVER_TEST.md](MANAGED_SERVER_TEST.md#build-and-verification). CI also
downloads the public Xerial SQLite JDBC 3.53.2.1 fixture and checks its SHA-256
against the known Thor pin before running real database tests. To run those tests
locally, point `WURM_TEST_SQLITE_JAR` at that JAR, then run:

```bash
python3 -m unittest discover -s tests -v
bash ./gradlew --no-daemon :app:assembleManagedPreview \
  :app:testManagedPreviewUnitTest :app:lintManagedPreview
python3 scripts/verify-managed-apk.py app/build/outputs/apk/managedPreview/app-managedPreview.apk
```

Tests cover separate-JVM baseline/check persistence, added/removed/changed files,
real committed SQLite changes, WAL-backed row counts, source-byte preservation,
corruption without baseline replacement, changed-world/working-copy rejection,
symlinks and missing databases. Kotlin tests check audit arguments and classpath
separation alongside the existing recovery tests. CI compiles/tests/lints all
variants, checks the ARM64 runtime/POC/helper assets and verifies APK signing.
The audit's Android SQLite behavior, duration and storage cost still need this
physical Thor test.

## Every file changed

Kotlin names below are relative to
`app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.

| File | Change |
| --- | --- |
| `runtime-probe/src/persistence/StorageAudit.java` | Persistent file snapshots, comparisons, disposable SQLite-copy checks and atomic reports. |
| `ManagedActivity.kt` | Baseline/check/view/export controls and 0.5.0 identity. |
| `ManagedLaunch.kt` | Separate audit entry point and arguments using diagnostic-only classpath. |
| `ManagedServerController.kt` | Owned audit child, pinned inputs, cancellation/deadline and completion/failure handling. |
| `ManagedServerService.kt` | Foreground baseline/check actions sharing the existing service. |
| `ManagedSession.kt` | Serialize audits with server/storage operations and report operation failures accurately. |
| `ManagedWorkspace.kt` | Audit directory/report outside the game runtime. |
| `app/build.gradle.kts` | Version 0.5.0/code 8 and `.storagepreview` install-alongside package. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedWorkspaceTest.kt` | Audit argument/world/classpath boundaries. |
| `tests/test_storage_audit.py` | Six focused tests using real SQLite and handwritten data. |
| `scripts/verify-managed-apk.py` | Require the Java 17 storage audit class in the APK helper. |
| `.github/workflows/android.yml` | Pinned public JDBC test fixture and immutable 0.5.0 release with guide/checksums. |
| `README.md` | Current milestone, release and device-test links. |
| `docs/THOR_SERVER_PASS.md` | Record the user's five-minute app-switch and two-minute screen-lock results. |
| `docs/IMPLEMENTATION_PLAN.md` | Mark short background checks passed and describe the implemented audit milestone. |
| `docs/MANAGED_SERVER_TEST.md` | Route the current storage milestone to its migration/test guide. |
| `docs/RELEASE_MANAGED_PREVIEW.md` | 0.5.0 download, migration and test notes. |
| `docs/STORAGE_VERIFICATION.md` | This implementation, test, limitations and file inventory. |

No handwritten POC, runtime source/binary pin, native heap compatibility code,
proprietary file or SQLite SQL patch changed.
