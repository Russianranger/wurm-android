# 0.10.42 — stock-world database path fix

The 0.10.41 stock import succeeded, including Android SQLite and all three
compatibility overlays. Server startup then failed with `SQLITE_CANTOPEN` in
Flyway's migration inspection. This was a preparation omission: the untouched
world INIs contain `DB_HOST=localhost`, and Wurm's SQLite configuration uses
that value as a directory relative to the runtime. The supplied databases are
in Adventure/sqlite and Creative/sqlite; there is no localhost/sqlite.
Setting the selected map/world path does not set this separate database path.

The working prepared installation has a real localhost/sqlite directory, which
is why older exports worked. Its selected database must not be silently switched
to a different copy. The later report status about retaining one original import
came from retrying import after the failed start; it is not the startup cause.

## Device evidence and scope

Support ZIP: 14,538 bytes, SHA-256
`2d16501c21a5adef54374d8e8e9c16ffcc34732ef433fe64f0c14eadb992bf46`.
Header: `.stockprep`, exported 2026-09-13T13:09:08.584744Z. The original import
completed at 13:06:57. Preparation preflight passed and all four item overlays
were active. Wurm recognized Adventure and its resource directory, then failed
opening the first database. Exit 1 followed after 904 ms; no successful start
or normal save was recorded. A failed session's recovery guard remains valid.

0.10.41's checks covered driver operation in a scratch database, overlay
selection, world folder recognition and recipes. They did not exercise the
selected world's actual DB_HOST connection path. This release adds that missing
coverage, including a full disposable Wurm server startup and restart.

## Fix

The stock recipe is now `thor-stock-sqlite-2`. After flattening the desktop dist/
layout, it checks each world's configured database directory. If that directory
exists, all nine expected database files must be present with SQLite headers;
its DB_HOST value and database selection remain unchanged. Partial existing
shared databases cause a clear failure, not a switch to a template copy.

Only the known desktop case—literal `DB_HOST=localhost` with an absent localhost
directory—can be repaired automatically. All nine databases must exist in that
world's own sqlite/ directory. Preparation then replaces only the DB_HOST line
with the relative world name. Adventure and Creative therefore use independent
databases. Java Properties escaping supports names with spaces/Unicode; comments,
line endings, other settings, credentials, LOGIN_DB_HOST and SITE_DB_HOST retain
their bytes. Custom missing/outside/ambiguous paths are rejected. Plans for all
worlds are validated before INI edits, and every edit stays inside unpublished
import staging. Import failure retains the prior selection.

Game JARs, maps and database bytes are preserved. The original uploaded ZIP is
never modified. Existing prepared imports keep their previous recipe and files.
The world settings GUI already resolves this same DB_HOST path, so it now edits
the fresh world's actual login/settings database as well.

Preflight now receives the selected world and checks the configured directory,
all nine expected files, header/size/writability and containment before Wurm
starts. It neither creates empty databases nor opens SQLite connections. Missing
or linked/outside paths fail with `WORLD_DATABASE_PATH_FAILED`; a pass records
`WORLD_DATABASE_PATHS_OK directory=Adventure/sqlite; databases=9` (or the actual
configured relative directory). Existing JVM/network/SQL overlay checks follow.
Original, working-copy, checkpoint and abnormal-exit recovery behavior remains.
No game code/POC, native graphics/audio, ASan, GC, keyboard or SQLite version changed.

## Validation

- 40 focused Kotlin/JUnit tests pass, covering exact INI changes, both worlds,
  idempotent preparation, real shared-directory preservation, incomplete/corrupt
  databases, unknown/ambiguous paths, escaping, unchanged data and import rollback,
  existing input/storage/checkpoint behavior and selected-world launch arguments.
- Host suite: 179 tests pass, with 16 unavailable fixture/platform skips locally.
  The five new database-path tests pass, including the owner's private inputs.
- Wurm's actual SqliteConnectionFactory reproduces `SQLITE_CANTOPEN` with the
  old value, then opens all nine databases after correction. Both supplied worlds
  open their own nine original databases without changing their bytes.
- End-to-end import of the exact stock ZIP checks all 711 supplied files: only
  the two DB_HOST lines change; the other 709 files and all database bytes match.
  Final runtime: 715 files, 245,543,648 bytes including supplied app dependencies
  and the recipe manifest. The new preflight passes for the selected world.
- A disposable copy starts the actual server with the normal helper/POC/overlays
  on host Java 17 and pinned SQLite. Flyway opens Adventure/sqlite databases,
  personal mode is active, startup returns, STOP saves/exits 0. A second start
  reopens that same working copy, accepts a loopback TCP connection on 3724,
  then saves/exits 0 again. The imported source stays unchanged throughout.
  No player/client session was simulated; Android play/save still needs the Thor.
- Release CI and APK evidence are recorded in HANDOFF.md after publication.
  Proprietary JARs, databases, disassembly and server logs are never committed or
  included in CI/release assets.

## Retest

VersionCode 56, package `io.github.russianranger.wurmlauncher.stockdbfix`, tag
`v0.10.42-stock-database-fix`. Keep the working 0.10.40 app and its complete backup.
Preview signing remains per-run, so this installs alongside earlier versions.

1. Install 0.10.42 and use its fresh Server → Setup & runtime → Prepare / import
   server ZIP screen. Select the same untouched WurmServerLauncher.zip; no file
   edits or repacking are needed. Keep other local Wurm servers stopped.
2. Choose Adventure, then Start server. The report should show
   WORLD_DATABASE_PATHS_OK, successful Flyway openings under Adventure/sqlite,
   all overlays active, and server readiness.
3. Import the usual client ZIP for play. Log in, make an inventory change,
   save/stop normally, restart, and confirm persistence. Export the support bundle.
4. Do not restore the failed 0.10.41 complete backup for this fresh-import test;
   complete restore preserves that old configuration and its recovery marker.
   To preserve an existing 0.10.41 setup instead, export its before-start server
   checkpoint and import that ZIP through 0.10.42's preparation screen. The stock
   recipe also repairs such exports. Keep complete backups for the client/settings.
   A normal 0.10.40 full backup remains the supported previous-world migration path.

The one-original-import rule is unchanged. No additional original archive upload
is needed; the requested follow-up is the resulting support bundle.
