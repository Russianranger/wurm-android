# Wurm Server 0.10.9 — local client connection

Your 0.10.8 report passed material/GUI/terrain setup and reached Connecting with
at least 375 frames. The app then stopped the client at its two-minute limit.
The connection's authentication/login outcome was absent from the console report.

This build exports Wurm's actual splash/authentication/login/retry messages and
connection snapshots. The startup limit is five minutes; after the client reports
accepted login and closes its startup screen, use Stop Client to end the session.
The observer is read-only. Real Adventure authentication and visible world entry
still need testing; neither is fabricated or claimed complete.

Install **0.10.9-managed-preview**, code **23**, package
`io.github.russianranger.wurmlauncher.clientconnection`, alongside your working server.

1. Import the same complete client ZIP in **0.10.9 → Client**.
2. Start Adventure in the working **0.6.0 server app**; wait for its listening port.
3. Select **0.10.9 → Client → Start Local Game** (127.0.0.1:3724).
4. Watch the connection status. If it remains waiting/retrying for about 60 seconds,
   capture the screen and Stop Client. If the world appears, test controls, capture
   it, then Stop Client. You can also let the five-minute startup budget expire.
5. Export **Client Report → wurm-client-report.txt** from 0.10.9.
6. In the working server app, export **Session Report → wurm-server-report.txt**
   immediately after the same attempt. Send **both reports** and the screenshot.

No new Wurm files, PC, root, Termux commands or repeat triangle test are needed.
No server/POC/SQLite or graphics-native changes are included. The separate client
preview needs its own import because CI debug signing changes between releases.

All **87 automated tests** and **four new Kotlin state/deadline tests** pass locally.
The real-JAR loopback fixture verifies original ticket dispatch, real auth parsing,
login dispatch and read-only observation. It does not authenticate against a Wurm
server. CI builds/tests/lints all variants and verifies signing, packaged monitor,
runtime/graphics hashes and exact POC bytes before publication.
See **CLIENT_CONNECTION_TEST.md** for every changed file and exact test details.
