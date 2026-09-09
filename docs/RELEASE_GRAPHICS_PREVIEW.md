# Wurm Server 0.10.14 — local login credential fix

The 0.10.13 Thor server used the Java 17 encoder successfully and stayed alive
until Stop. It accepted local authentication but rejected the launcher's blank
login credential. This build supplies the same persisted local identity used by
the existing Steam shim. The separate server password remains empty for this
local preview. No Steam account password is needed.

The subsequent EGL/Scudo abort happened during client exit after login rejection.
Its origin remains unresolved; this release does not claim a native crash fix.
Server authentication checks, encoder/SQLite patches, POC, Serial GC and the
graphics/controller path are retained. Login and world entry need the next test.

1. Install **Wurm-Server.apk 0.10.14** (code 28, separate loginidentity package).
   Keep older versions and their data; stop their clients and servers.
2. Import your same prepared server runtime ZIP into 0.10.14; select Adventure.
3. Import the same complete client ZIP into 0.10.14; keep player name Thor.
4. Choose **Start Local Game** in this version. Let it pass authentication/login
   or display a failure. Startup has a five-minute limit.
5. If the world appears, test movement/look/clicks for 60 seconds. Take a screenshot.
6. Stop Client if still active; export **Client tab → Export Client Report**.
   Stop Server if still active; export **Server tab → Export session report**.
   Export before Retry. Send both reports from 0.10.14 and the screenshot.

No new game files, manual JAR patching, account passwords, memory-test rerun,
PC, root or Termux are required. The existing two ZIPs must be imported again
because this preview has separate app storage. Keep the original world and
checkpoints; gameplay persistence is not yet qualified.

See attached **CLIENT_LOGIN_IDENTITY_FIX.md** for the exact archive layout,
diagnostic markers, validation limits and every changed file. This completes a
testable Gate 5 credential correction, not full client login/world acceptance.
