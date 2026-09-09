# Wurm Server 0.10.10 — server login trace

The paired 0.10.9 client / 0.6.0 server reports show local authentication accepted
and a login request sent, followed by a wait with no further input. The server
also records an unrequested exit, without a timestamp or detailed login logs.
The exact cause and successful login/world entry remain unverified.

This release routes server Java logs to the app, captures bounded server threads
while login waits, records timestamped exit/shutdown evidence, and explains when
an owned server stops. Client Report now includes this app's Server Session Report.
The POC, Steam shim, SQLite fixes, world recovery and graphics/protocol code remain
unchanged. No proprietary files are bundled.

**Run both sides in this preview for the next test:**

1. Keep 0.6.0 installed. With its server stopped, export its working runtime ZIP.
2. Install Wurm-Server.apk (0.10.10, code 24, separate logintrace package).
3. Import that server ZIP into 0.10.10 and select Adventure. Import the same
   complete client ZIP into its Client tab.
4. Leave the old server stopped. Choose Start Local Game in 0.10.10.
5. After about 60 seconds of a login wait, screenshot and Stop Client Test.
6. Export Client Report and Server Session Report from **0.10.10**, and send both.
   The client export also embeds that app's server history. Stop the server
   separately after testing; Stop Client only stops the client.

No PC/root/Termux or replacement game files are needed. The previous unexpected
server exit did not confirm a save; keep its working files/checkpoint and original
app. No restore or data repair is performed by this milestone.

See attached **CLIENT_LOGIN_TEST.md** for exact steps, evidence, gate status,
validation and every changed file. All three Android variants build/test/lint
before publication; runtime, POC and new diagnostic classes are verified in the
APK. 91 diagnostic tests and seven Kotlin connection/server-watch tests pass.
