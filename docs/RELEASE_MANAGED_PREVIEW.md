# Wurm Server 0.6.0 — world and configuration preview

Adds **View world/configuration report** and **Export world/configuration report**.
Every Start records configuration file identities, the recognized GameFolder,
logged SQLite paths, Steam shim ports and loopback readiness. Once Running, the
owned Java child observes its open/mapped game files and, where Android allows
it, its own TCP listeners. Unavailable observations are explicit. The report
persists after Stop and app reopening; imported settings are read-only.

0.5.0 passed the Thor storage test: all 12,007 files matched after reopening, all
36 databases passed checks, and changed map/database bytes persisted. This build
retains that audit, exact POC and patched inputs, Java 17.0.20 and existing
managed Start/Stop/Restart/recovery behavior. The Android client remains groundwork.

1. In **0.5.0**, Stop normally and **Export working runtime ZIP** to Downloads.
   Keep the older app/data and exported ZIP.
2. Install **Wurm-Server.apk** alongside it. Open **0.6.0** (package suffix
   `.worldpreview`), import that ZIP, select Adventure, heap 4096 MiB and expected
   TCP 3724. Keep at least 4 GiB free internal storage for this tested runtime.
3. Start and wait for Running. **View world/configuration report**; wait for
   `SNAPSHOT_END`. Expect GameFolder, JDBC paths and loopback readiness.
   `TCP_UNAVAILABLE` is expected where Android blocks proc network access; it
   does not indicate server failure or prove no listener exists.
4. Stop normally, close/reopen and view the saved report. It should retain the
   same launch ID and record the exit. Don't Start again before exporting.
5. Export **world/configuration report (`wurm-world-report.txt`)** and
   **session report (`wurm-server-report.txt`)**. Send those two files.
   **No Storage report or new baseline is needed for this milestone.**

No root, Termux command or separate Java installation. See
[WORLD_CONFIGURATION_TEST.md](https://github.com/Russianranger/wurm-android/blob/main/docs/WORLD_CONFIGURATION_TEST.md)
for exact steps, every changed file, report definitions and remaining device tests.
GameFolder and database paths are separate evidence; no database is moved or
merged. Literal configuration values are not proof of effective settings.
LAN/client login and a particular gameplay save still need testing.

The release includes runtime source provenance and checksums. Development signing
remains ephemeral; the separate package and working-ZIP import preserve 0.5.0.
Durable signing and seamless upgrades remain later work.
