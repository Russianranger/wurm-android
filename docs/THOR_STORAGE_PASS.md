# Thor 0.5.0 storage persistence PASS — 2026-09-08

The stopped-storage milestone passed. Actual runtime files changed during the
server run and retained their bytes after normal Stop and app close/reopen.
Both checks passed all 36 candidate SQLite databases without changing source
file bytes. This establishes file persistence for this run; verification of a
specific in-game change after restarting Wurm remains a separate test.

## Supplied evidence and order

The user explicitly identified `wurm-storage-report (1).txt` as the second
report, taken after closing/reopening the app. Times below are UTC report times.

| Evidence | Time | Result |
| --- | --- | --- |
| `wurm-server-report (3).txt` | Session on 2026-09-08 | Baseline captured; preflight passed; Wurm reached TCP 3724; requested Stop exited 0; first post-stop audit completed. |
| `wurm-storage-report.txt` | 18:30:06.882686598 | First post-stop check; `LAST_CHECK_NONE`; 36 databases passed. |
| `wurm-storage-report (1).txt` | 18:30:46.255405489 | Post-reopen check; `LAST_CHECK_MATCH`; 36 databases passed. |

The session report covers the first check. The second storage report and the
user's observation establish the post-reopen comparison.

Both storage reports identify:

- Wurm Server 0.5.0, mode `check`, world `Adventure`.
- Working copy `work-6b3472d2-7882-4a09-9861-e2666201069a`.
- `FILES=12007 BYTES=749842098`.
- Baseline timestamp `2026-09-08T18:29:01.186567091Z`.
- `BASELINE_DIFF ... added=2 changed=16 removed=0`.
- `DATABASES=36 FAILED=0`, `SOURCE_UNCHANGED` and `STORAGE_CHECK_COMPLETE`.

The second check compares against the first accepted check at
`2026-09-08T18:30:07.960270243Z` and records:

```text
LAST_CHECK_MATCH since=2026-09-08T18:30:07.960270243Z added=0 changed=0 removed=0
```

All 36 database entries retain the same hashes and aggregate table/row counts
across the two reports. There are nine databases in each of `Adventure/sqlite`,
`dist/Adventure/sqlite`, `dist/Creative/sqlite` and `localhost/sqlite`.
All report `wal=false` for this test.

## What changed during the server run

The baseline differences are identical in both reports:

| Change | Paths |
| --- | --- |
| Four changed map files | `Adventure/flags.map`, `Adventure/map_cave.map`, `Adventure/resources.map`, `Adventure/top_layer.map` |
| Three changed databases | `localhost/sqlite/wurmcreatures.db`, `localhost/sqlite/wurmdeities.db`, `localhost/sqlite/wurmlogin.db` |
| Six changed logs | `localhost/Logs/wurm.log.0` through `localhost/Logs/wurm.log.5` |
| Three other changed files | `entities.xml`, `stats.html`, `stats.xml` |
| Two added logs | `localhost/Logs/wurm.log.6`, `localhost/Logs/wurm.log.7` |
| Removed files | None |

The changed map and database bytes survived reopening. The observation is more
than persistence of the selected Adventure name. Hash changes and aggregate row
counts alone do not identify the particular gameplay state represented.

The database writes under `localhost/sqlite` agree with earlier Wurm JDBC logs.
This is an observed path mapping, not evidence of a newly introduced path bug.
Do not relocate or merge these databases with `Adventure/sqlite` based only on
directory names. The current whole-runtime audit includes both locations.

## Corresponding server session

The session ran on Android 13/API 33 as ordinary UID/eUID 10198:

- Baseline audit child PID 2988 completed `BASELINE_CAPTURED`.
- Preflight PID 3177 reported `PREFLIGHT_PASS`.
- Server PID 3349 reached `TCP_READY port=3724`.
- Normal Stop produced `SHUTDOWN_REQUESTED`, then
  `SERVER_EXIT=0; stopRequested=true; force=false; startupCancelled=false`.
- Post-stop audit PID 3516 completed `STORAGE_CHECK_COMPLETE`.

This confirms the storage comparison follows a completed managed server run and
requested normal exit, rather than only auditing a newly imported runtime.

## Next smallest milestone

Add a read-only view/report of the effective world and configuration: selected
GameFolder, map locations, database locations and confirmed listener settings.
Resolve these from the actual supported runtime's configuration/API and observed
logs; do not infer a database root from the selected folder name. Preserve the
current working layout and SQLite-patched inputs. Do not add configuration writes
or multi-world switching until the effective paths have been verified.

Then test LAN reachability and a supported external Wurm client/admin route.
Use that route to make one identifiable persistent change, Stop normally, start
the same working copy and verify the change in Wurm. Include an item insert/update
path to test the preserved SQLite compatibility work. Android client execution
remains separate groundwork.

The current reports do not establish:

- Wurm-level client login or a complete playable session.
- Semantic correctness of a particular saved gameplay change or item SQL path;
  `wurmitems.db` did not change in this run.
- Physical-device WAL/journal recovery: these databases all reported no WAL.
- Recovery after force-stop/power loss, long-duration or memory-pressure behavior.
- Backup restoration or seamless signed APK upgrades.

For the Thor now, keep the installed 0.5.0 app and its working copy. While Stopped,
use **Export working runtime ZIP** to preserve this accepted state. No reinstall,
re-import, new baseline, Java installation or Termux command is required for this
passed test. The [0.5.0 APK](https://github.com/Russianranger/wurm-android/releases/tag/v0.5.0-storage-preview)
remains unchanged. No game files or raw session logs are added to the repository.

This evidence update changes only this file, the top-level README,
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) and
[STORAGE_VERIFICATION.md](STORAGE_VERIFICATION.md).
