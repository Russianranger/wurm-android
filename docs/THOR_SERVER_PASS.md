# Thor 0.4.1: startup, working-copy reopen and Restart passed

**User-observed background result:** after the cumulative report, the user
confirmed switching to other apps for **five minutes** and returning to Running,
then locking the screen for **two minutes** and returning to Running. These short
checks passed by direct observation; they are not inferred from the untimestamped
server log. Extended background operation and memory-pressure survival remain
unqualified. The next implemented milestone is
[0.5.0 stopped storage verification](STORAGE_VERIFICATION.md).

## Follow-up: the same working copy reopened and Restart completed

`wurm-server-report (2).txt` contains the original successful run plus two new
successful runs, including an explicit controller-managed Restart. Report SHA-256:
`f94771cc7012e9822e77f58eed61a1ce9e545a23820574ceffc9bf3a9fc7a019`.

| Run | Server PID | Result |
| --- | --- | --- |
| Original run retained in the cumulative report | 9765 | Preflight/checkpoint/TCP 3724, requested exit 0. |
| Later Start after the first normal stop | 25891 | Same working Adventure path reopened; preflight/checkpoint/TCP 3724, requested exit 0. |
| Controller-managed Restart | 26452 | Previous child exited before new preflight/checkpoint/launch; TCP 3724 returned, then requested exit 0. |

All three use UID/eUID 10197, the same five verified input JAR hashes, and world
path `work-65b29e08-be8f-44f7-a3d8-f8525b0fb7d5/Adventure`. The report records only
the original import and no restore. The second run's `SERVER_EXIT=0` is followed
by `Previous child exited. Taking a new checkpoint before restart.`, a fresh
preflight/checkpoint, the new PID and another `TCP_READY`. Thus Restart itself is
demonstrated, as well as reopening the previously used working runtime.

Totals in this cumulative report are **three** `PREFLIGHT_PASS`, completed
checkpoints, `TCP_READY port=3724`, `SHUTDOWN_REQUESTED` and requested
`SERVER_EXIT=0` results. All exits have `force=false; startupCancelled=false`.
The earlier pointer-tag abort does not appear. An `Exporting complete` operation
is recorded at 15:26 UTC; the generic marker does not identify which ZIP export
was selected or independently verify its saved contents.

The report does not record Activity close/reopen events, Home presses, screen
lock/unlock, continuous port-health samples or exact run durations. It cannot
independently establish that app switching or screen-off survival passed; the
user has now supplied the observations recorded above. No repeat of the passed
startup/Restart sequence is needed for that result. Keep the 0.4.1 working copy
and follow the 0.5.0 export/import guide for storage tests. Changed-world
persistence still requires an identifiable saved change to be
verified after reopening; repeated TCP readiness alone is not that evidence.

This follow-up updates `README.md`, `docs/IMPLEMENTATION_PLAN.md`,
`docs/MANAGED_SERVER_TEST.md` and this file. No application code or release changes.

## Original first-run evidence

The supplied `wurm-server-report (1).txt` records a successful first managed
server lifecycle on the AYN Thor, Android 13 / API 33 / ARM64, on 2026-09-08.
Report SHA-256: `7364cf36295a53f0482a210731de89b78a6b76b35b41228eea533d91ec639ee9`.
Import began at 14:09:35 UTC; preparation at 14:09:53; Wurm initialization logs
are timestamped 14:10:14. The report does not timestamp TCP readiness or exit,
so it cannot establish the full startup time or how long the server stayed up.

Tested release: [v0.4.1-managed-preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.4.1-managed-preview),
source commit `2f14effbcab65fb824e718689f88d97616711bce`, package
`io.github.russianranger.wurmlauncher.managedfix1`. The matching published APK's
SHA-256 is `d39cc6b5a00853b401bcd752c76a0b7efdc9e0a21133d2cfad78be39f7abc219`.
The report identifies the installed version/package; it does not independently
hash the installed APK. Its runtime module and all five input JAR hashes match
the expected values.

## What this run establishes

| Report evidence | Result |
| --- | --- |
| Java 17.0.20-internal; UID/eUID 10197 | APK-packaged JVM ran as an ordinary app user, with no Termux launch path. |
| `HEAP_TAGGING_OFF: before=0xb4 after=0x00` in both children | The child-only allocator compatibility setting was accepted on the Thor. |
| `JAVA_OK`, `SQLITE_OK`, `PROBE_OK`, `PREFLIGHT_PASS` | Java and disposable SQLite commit/reopen preflight passed. |
| `ENUMERATE_OK count=3`, `LOCALHOST_OK`, `NETWORK_OK` | Interface enumeration, localhost resolution and TCP loopback exchange passed. |
| Completed before-start checkpoint, `WORLD_LOCK_OK` | A checkpoint was written and each native child acquired workspace ownership. Export/restore and concurrent exclusion still need their own device tests. |
| Adventure GameFolder, SQLite loading, offline Steam shim, progress past `Loading servers` | The previous exit-134 abort did not recur during this run. |
| `runServer() returned`, `TCP_READY port=3724` | Wurm initialization returned and the expected local TCP port was reachable while the child was alive. |
| `SHUTDOWN_REQUESTED`, `SERVER_EXIT=0; stopRequested=true; force=false; startupCancelled=false` | The control adapter received normal Stop and the child exited with code 0, without forced termination or startup cancellation. |

There is no `SHUTDOWN_RETURNED` marker. The adapter invokes `Server.shutDown()`
after its request marker; Wurm may exit inside that API. The report establishes
the request and normal process exit, not the complete save operation or a
successful subsequent reopen.

Steam connection messages come from the offline shim. They do not establish real
Steam authentication. The selected directory is Adventure; `ServerName` and
`MapName` both report Heavenord from the imported configuration. No configuration
rewrite is indicated or needed by this report.

The compatibility setting got past the observed abort in this run. The precise
native operation that lost its pointer tag remains unidentified, and the setting
still disables heap-tag checks only in the Java child. Do not label it a repair
of the underlying pointer-handling code or proof of long-term stability.

## Lifecycle procedure used for the follow-up

The report above now demonstrates working-copy reopen and Restart from this
procedure. App-switch/screen-lock observations are now confirmed above; do not
repeat the whole sequence for qualification of 0.4.1.

No new APK, runtime ZIP, Java installation, root access or Termux command is
required. Continue in **0.4.1** with **Adventure**, **4096 MiB**, TCP **3724**.
Keep any other server stopped. Do not reimport or restore between these checks:
the point is to reopen the working copy just used, not the original snapshot.

1. While Stopped, use **Export working runtime ZIP** and save it in Downloads
   under a recognizable name such as `wurm-after-first-stop.zip`. Keep it locally
   as the baseline. The ZIP contains private game/world files and need not be
   shared to report the lifecycle result.
2. Close the app normally and reopen **0.4.1**. Confirm Adventure is still selected.
   Tap **Start Server** and wait for **Running / TCP_READY**. This checks that the
   existing working runtime can reopen after the first normal stop.
3. While Running, tap **Restart Server** once. Wait for Running again. The report
   should show the old `SERVER_EXIT=0` before the next preflight, checkpoint and
   `TCP_READY`. Export the session report at this point.
4. After restart succeeds, press Home and use another app for about two minutes.
   Return and check Running, the notification and logs. Then lock the screen for
   about two minutes and check again. Note any interruption and whether the port
   remains reachable. This is a short background check, not an endurance result.
5. Tap normal **Stop Server**, wait for Stopped, and export the session report
   again. Send that report and tell us whether reopen, Restart and background
   checks passed. Export the stopped working ZIP again for comparison/backup.

If any step fails, stop the sequence and export the report. If recovery is
required, export the working copy/checkpoint before an explicit restore. A
successful export or normal exit alone does not certify database consistency.

## World-data persistence and the following development gate

Adventure selection persistence was already demonstrated; it is a saved app
setting. The follow-up also demonstrates reopening the same working runtime
after Stop and through Restart. Proving **changed world data** persists requires
an identifiable Wurm change, a normal stop and verification of that same change
after reopening.

If an existing supported external Wurm client/admin route is available, use it to
make a small identifiable change in this test copy and verify it after restart.
Do not invent SQL edits or treat a changed database hash as proof of semantic
correctness. Real item insert/update behavior still needs testing despite the
unchanged known-good JAR hashes. The Android client remains import/Settings
groundwork and cannot yet perform that test.

After the lifecycle checks, the next bounded development work is to improve
persistence diagnostics and establish durable APK signing/upgrade handling before
using long-lived worlds. LAN/client protocol, item SQL, checkpoint restore,
process-death recovery and extended background/memory behavior remain distinct
acceptance gates. This report does not require another runtime rewrite.

## Documentation update

This evidence update changes only `README.md`, `docs/IMPLEMENTATION_PLAN.md`,
`docs/MANAGED_SERVER_TEST.md`, `docs/THOR_NATIVE_HEAP_FIX.md` and this file.
No application/runtime/POC code, input pin, APK or published release was changed.
