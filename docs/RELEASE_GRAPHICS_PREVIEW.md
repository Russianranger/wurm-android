# Wurm Server 0.10.15 — personal-server character creation

The 0.10.14 Thor test authenticates locally and renders the splash, but the server
rejects missing player Thor. The POC enabled personal mode, then disabled it through
runServer(false, true). The first argument controls personal mode; the second
controls offline mode. This build uses runServer(true, true) and checks the mode
after startup, enabling Wurm's original first-time character creation path.

Client credentials, authentication, SQLite/encoder fixes, Serial GC and graphics/
controllers are retained. World entry and persistence need your test. The older
EGL/Scudo exit issue remains unresolved.

1. Install **Wurm-Server.apk 0.10.15** (code 29, separate personalserver package).
   Keep older apps/data, but stop their clients and servers.
2. Import the same prepared server ZIP; select **Adventure**.
3. Import the same complete client ZIP; keep player name **Thor**.
4. Choose **Start Local Game in 0.10.15**. Allow up to five minutes or capture
   the first failure. Complete character setup if it appears in the game.
5. If the world appears, test movement/look/clicks for 60 seconds and screenshot.
6. Stop Client if active and export **Client Report**. Stop Server, wait, then
   export **Server Session Report**. Send both reports and screenshot before Retry.
7. If creation and clean shutdown succeeded, start again in this same app without
   reimporting. Confirm the same character returns and whether its location
   persisted. Keep checkpoints and the original archive.

No desktop launcher, new game files, manual patches, account password, memory-test
repeat, root or Termux is required. Both existing ZIPs need importing because this
preview has separate storage. See **SERVER_PERSONAL_MODE_FIX.md** for the verified
cause, diagnostic marker, tests, complete file list and remaining limitations.
