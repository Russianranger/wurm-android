# Wurm Server 0.10.13 — Java 17 server login fix

The Thor passed both 0.10.12 memory tests and sustained the real client login
attempt with Serial GC. The current blocker is on the server: LoginHandler
throws NoClassDefFoundError for the removed sun.misc.BASE64Encoder, then exits.

This release redirects one verified encoder reference to an authored Java 17
adapter in a session-private overlay. SHA-1/UTF-8, password comparisons and all
login decisions remain unchanged. The imported JAR, existing item and position
SQLite fixes, POC, client Serial GC and graphics/controller path are retained.
No proprietary files are bundled. Full login/world entry remains unverified;
the previous native corruption's origin also remains unresolved.

1. Install Wurm-Server.apk (0.10.13, code 27, separate loginbase64 package).
2. Stop the 0.10.12 client and older servers. Keep older apps/data installed.
3. Import your same prepared server runtime ZIP into 0.10.13; select Adventure.
4. Import the same complete client ZIP into 0.10.13. Keep player name Thor.
5. Choose Start Local Game in 0.10.13. Both sides must run in this version so
   the encoder fix and owned-server diagnostics are active.
6. Screenshot and export after 60 seconds, or immediately on failure before Retry.
7. Send **Client Report** and **Server tab → Export session report**, both from
   **0.10.13**, plus the screenshot. The server export is the Session Report,
   not Storage Report. Stop client/server after exporting.

No repeat memory test, new game files, PC, root, Termux or manual patching is
needed. See attached SERVER_LOGIN_BASE64_FIX.md for evidence, file requirements,
validation limits, recovery choices and every changed file.
