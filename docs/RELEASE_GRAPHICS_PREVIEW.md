# Wurm Server 0.10.12 — client GC compatibility test

The 0.10.11 server stayed alive after the client crash and stopped on request
without the earlier position SQL errors. The client now supplies a confirmed
native heap-corruption abort on a GC thread. The detection frame is in G1-related
bookkeeping; the original corrupting write or race remains unknown.

This preview tests Serial GC for actual client entry, verifies the collector
really selected, and exports GC/safepoint logs. A new JVM Memory Test compares
G1 and Serial without loading game files or graphics. This is an experimental
workaround, not a proven heap-corruption repair. Login/world entry remain unverified.

1. Install Wurm-Server.apk (0.10.12, code 26, separate clientgc package).
2. Open Client tab → JVM Memory Test; wait for both results. No imports needed.
3. Export Client Report as wurm-memory-0.10.12.txt. If Serial fails, send it and
   stop here. If Serial passes, proceed even if G1 failed.
4. Import the same complete client ZIP into 0.10.12.
5. Start Adventure in the working 0.10.11 app. Leave it running. In 0.10.12 choose
   Start Local Game to use that external listener at 127.0.0.1:3724.
6. Screenshot the result and export Client Report from 0.10.12 after 60 seconds
   or immediately on failure. Export Server Session Report from 0.10.11 too.
7. Send all three reports. Stop client and server separately after testing.

Keep 0.10.11 installed with its files/checkpoint. No server migration, new game
files, PC/root/Termux or manually patched JAR is needed. Runtime and graphics
pins, POC, server/item/position fixes and controllers are retained. No proprietary
Wurm files are bundled. See attached CLIENT_GC_TEST.md for exact steps, verified
scope, source evidence and every changed file.
