# Wurm Server 0.5.0 — storage verification preview

Adds **Capture storage baseline**, **Check stored data**, and **View/Export storage
report**. While stopped, the app fingerprints the complete working runtime,
compares with its baseline/last check and checks disposable SQLite database copies.
The report records file changes, database check results and aggregate row counts
without including table contents. The audit never opens the source databases
through SQLite or applies game/SQL repairs.

The 0.4.1 foundation passed startup, working-copy reopen, Restart, TCP 3724 and
requested exit 0 on the Thor. The user also confirmed five minutes in another
app and two minutes with the screen locked, returning to Running after both.
POC bytes, Java 17.0.20, input pins, heap compatibility and client groundwork stay
the same. The new storage checks still require their own physical-device test.

1. In **0.4.1**, stop normally and **Export working runtime ZIP** to Downloads as
   `wurm-working-runtime.zip`. Keep that app and its data installed.
2. Install this **Wurm-Server.apk** alongside it. Open the screen showing **0.5.0**
   (package `io.github.russianranger.wurmlauncher.storagepreview`), import the
   working ZIP and select **Adventure**, heap **4096 MiB**, TCP **3724**. Keep at
   least **4 GiB free storage** for this runtime. Larger databases need more.
3. While stopped, **Capture storage baseline** and wait for completion. Expect
   `BASELINE_CAPTURED`, database checks with `FAILED=0`, and `SOURCE_UNCHANGED`.
4. Start, wait for Running, then Stop normally. **Check stored data** while stopped.
   Export both storage and session reports. `BASELINE_DIFF` can be expected after
   a server run; it is a report of changed file bytes, not a save-failure verdict.
5. Close/reopen 0.5.0, leaving the server stopped. **Check stored data again**
   without starting, restoring or recapturing the baseline. Expect
   `LAST_CHECK_MATCH`. Export this storage report and send both checks plus the
   session report for review. No root, Termux command or separate Java install.

If a check fails, export both reports before changing anything. Source runtime
files remain untouched; prior accepted snapshots are retained on failed checks.
The audit uses the same foreground service and native lock as the server, and
Stop cancels it. SQLite quick_check/file hashes do not certify game-level saving;
an identifiable Wurm change still needs verification after restart.

See **STORAGE_VERIFICATION.md** for scope, WAL handling, limitations, build/test
instructions and every changed file. Existing lifecycle/recovery instructions
are in MANAGED_SERVER_TEST.md. The release includes corresponding JRE sources,
notices and checksums. Development signing remains ephemeral, hence the separate
package and deliberate working-ZIP transfer; durable signing is subsequent work.
