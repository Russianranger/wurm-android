# Wurm Server 0.10.11 — position SQL fix

The 0.10.10 reports prove the local server exits first. Repeated position-save
errors still use MySQL SQL against SQLite; the initiating fatal exception was
lost to log rotation. The supplied server.jar confirms this separate defect.

This build generates a guarded, private position-class overlay from your imported
server.jar and verifies it before Wurm initializes. It preserves the earlier item
SQL fixes and the imported JAR bytes. A separate first-error file keeps early
severe messages in Client Report and Server Session Report through log rotation.
Graphics, controllers, offline Steam and existing world recovery are retained.
No proprietary game files are bundled. Login/world entry remain unverified.

1. Keep 0.10.10 installed. With its server stopped, Export before-start checkpoint
   ZIP. Keep its current files and original import as well.
2. Install Wurm-Server.apk (0.10.11, code 25, separate positionsqlite package).
3. Import the checkpoint ZIP, select Adventure, and import the same full client
   ZIP into its Client tab. Keep older servers stopped.
4. Choose Start Local Game. If still Connecting after 60 seconds, screenshot;
   if it fails earlier, export without Retry.
5. Export **Client Report and Server Session Report** from 0.10.11 and send both.
   Stop client/server separately if still running; export the server report
   again after Stop. Keep the checkpoint; exit zero alone does not prove saving.

No PC/root/Termux or manually patched JAR is needed. See the attached
**SERVER_POSITION_SQLITE_FIX.md** for exact files, steps, evidence and every
changed file. Host tests reproduce both original position-save failures with
the supplied class and pinned SQLite JDBC; the overlay fixes both and preserves
the personal-server UPDATE path. Device testing must identify any remaining
fatal server error and establish real login/world entry.
