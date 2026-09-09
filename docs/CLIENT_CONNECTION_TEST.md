# 0.10.9: local connection state and startup lifetime

## Evidence from the Thor

`wurm-client-report(6).txt` is **0.10.8**. It shows builtin material preload done,
GUI initialized, terrain preparation, then **Startup Phase - Connecting ..**.
The real window produces at least **375 frames**. Only one entry-stage local
ticket creation is logged. **STAGE_TIMEOUT occurs before exit 134**: this attempt
ended at the app's two-minute budget. It does not establish a spontaneous material
crash. The earlier 0.10.7 failure is not reproduced here, but is not claimed
permanently resolved. Audio still falls back to silence.

The report does not contain Login successful or a visible world. It also lacks
Wurm's detailed splash message, so we cannot yet distinguish an auth wait, login
wait or retry countdown. Do not infer successful authentication from the TCP
probe or LOCAL_TICKET_CREATED.

## Implemented milestone

The supplied client was inspected again: performConnection connects to the
launcher endpoint, uses its original sendSteamAuthTicket(false), processes the
normal auth reply, calls login, then waits for the login result. Auth acceptance
and login acceptance are separate flags. Some error/retry text is rendered only
through StartupRenderer.startupMessage, with no console print.

A new ClientConnectionMonitor starts beside the existing game thread. It reads
those inspected engine/connection/splash fields once per second, logs changes
and five-second snapshots, and samples at most 12 game-thread stack frames every
15 seconds during startup. It closes with the game thread. The observer neither
sets fields nor calls network/game methods. It never prints ticket data, identity
files or password fields. Messages are single-line and capped at 240 characters.
Counter values are approximate snapshots: payloadQueued counts payloads submitted
to the original writer, not bytes proved delivered; bytesRead/pendingBytes do not
prove a Wurm login and counters may reset in normal game code.

The Android UI uses the observed phase and actual message. Full counters/flags
and stack samples remain in Export Client Report. ABI/read failures emit
MONITOR_UNAVAILABLE and leave the original client startup running.

| Report phase | Meaning |
| --- | --- |
| INITIALIZING / SOCKET_CONNECTING | Client setup or connection creation is in progress. |
| AUTH_WAIT | The real client is waiting for its authentication result. |
| AUTH_DENIED | The client records an authentication failure message. |
| LOGIN_WAIT | Authentication flag is true; the client is waiting for login. |
| LOGIN_DENIED | The client records a login rejection message. |
| RETRY_WAIT | The actual splash is in its reconnect/countdown mode; its reason is exported. |
| DISCONNECTED | The client records a disconnect and its reason. |
| LOGIN_ACCEPTED | Client login flag is true; startup remains active. |
| GAME_LOOP | Auth/login/transport flags are true, connecting/disconnected false, and startup renderer is closed. Visible world still needs a device test. |

Entry startup now has a **five-minute** budget. Only the full GAME_LOOP evidence
removes that budget; a TCP probe, ticket, auth flag, splash or LOGIN_ACCEPTED marker
alone cannot. Each new attempt starts fresh. Stop Client remains available, and
other graphics/window/input budgets stay unchanged. A timeout retains the latest
connection phase/message and is labeled Client startup timed out.

## Verification and limits

- **87 automated Python/Java/native tests pass**, including three observer tests
  for state transitions, read-only buffer/counter behavior, bounded messages,
  credential exclusion and observable ABI failure without stopping the game.
- **Four new Kotlin tests pass** for phase/message display, malformed/incomplete
  GAME_LOOP rejection, exact deadline boundaries, preserved other-stage budgets,
  and reset between attempts. UI details stay bounded.
- The developer-only **ProbeClientConnection** runs against the actual supplied
  client JAR. Its original ticket method queues **78 payload bytes**, which the
  original SocketConnection sends as **80 framed bytes** to a private loopback
  receiver. Its real auth parser processes authored rejection/acceptance fixtures,
  and its original login method reaches LOGIN_WAIT. The observer sees those
  states and leaves the outgoing login buffer intact. No real Wurm server or
  accepted login/world is simulated as a pass.
- CI builds/tests/lints all three Android variants, checks signing and packaged
  monitor classes, runtime/native/graphics hashes and exact POC bytes. Publication
  is gated on these checks. The graphics API/native/source pins, five-entry
  overlay, existing trace settings, controller bridge and server code are unchanged.

For developer regression, run the normal Python suite with its pinned SQLite
fixture and :app:testManagedPreviewUnitTest. The authored developer probe is
`scripts/ProbeClientConnection.java`, main `client.ProbeClientConnection`; compile
it with the runtime helper and run with the existing window/API/compat JARs,
legally obtained client JAR, local/offline properties and private native host
setup used for ProbeClientMaterials. It is excluded from the APK. It requires
no full client packs and never modifies the imported JAR.

Gate 4 now progresses through terrain preparation to a rendering connection
screen. Full world rendering/audio still require testing. Gate 5 gains actual
state reporting and a usable startup lifetime; real Adventure authentication,
login and world entry are **not complete**.

No server shim change is justified yet by this report. A Steam BeginAuthSession
return value and later validation callback are distinct in the
[Steamworks API](https://partner.steamgames.com/doc/api/ISteamGameServer#BeginAuthSession),
but the POC intentionally uses Wurm's personal/offline path. The next paired
server/client reports must establish which path and response are actually reached.
The observer uses the standard Java [game-thread stack API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#getStackTrace());
no native signal or graphics interception is added.

## Exact AYN Thor test

1. Keep your working **0.6.0 server app** installed. Install **Wurm-Server.apk**
   from **v0.10.9-client-connection**. Verify **0.10.9** on the app screen:
   **0.10.9-managed-preview**, code **23**, package
   `io.github.russianranger.wurmlauncher.clientconnection`.
2. In **0.10.9 → Client → Import Client ZIP**, import the same complete, legally
   obtained ZIP: `client.jar`, `common.jar`, full `lib/`, full `packs/` including
   `graphics.jar`, `pmk.jar`, `sound.jar`, and remaining original assets. No new
   game files are needed. The separate preview needs its own import because CI
   debug signing currently changes between releases.
3. Start **Adventure** in the working server app and wait for its listening game
   port. If already running, leave it running. Return to **0.10.9 → Client →
   Start Local Game**, target **127.0.0.1:3724**. Do not start a second server.
4. Watch the new connection status and splash text. If it keeps waiting/retrying
   for about **60 seconds**, capture the screen and select **Stop Client Test**.
   You may instead wait for the five-minute startup timeout. If a world appears,
   capture it, try movement/look/clicks, then Stop Client; the startup timer will
   not interrupt an observed game loop. No repeat triangle test is required.
5. In **0.10.9 → Back to Client / Export → Export Client Report**, save
   **wurm-client-report.txt**.
6. Immediately after that same connection attempt, switch to the **working
   server app → Export session report** and save **wurm-server-report.txt**.
   Send **both reports**, plus the screenshot. We need **Client Report and Server
   Session Report**, not Storage or World Report. The server can remain running
   while you export.

These steps require no PC, root, Termux commands, server re-import or manual JVM
settings. The requested pair should show whether the ticket reaches the server,
which auth/login response it sends, and the exact client-side wait/rejection.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.9 and attach/checksum this guide. |
| `README.md` | Current Thor evidence and local connection test link. |
| `app/build.gradle.kts` | Version 0.10.9, code 23, separate clientconnection package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Identify the connection milestone and current version. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientConnectionState.kt` | Parse observed phases and require full game-loop evidence to remove the startup budget. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Consume connection status, apply stage-aware deadlines, preserve the final wait reason and update report evidence. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Current version and five-minute/startup-only lifetime explanation. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Current version and connection milestone label. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ClientConnectionStateTest.kt` | Four phase, input, deadline and reset tests. |
| `docs/CLIENT_CONNECTION_TEST.md` | Evidence, implementation, verification, every changed file and exact paired-report test. |
| `docs/CLIENT_INTEGRATION.md` | Current connection architecture and remaining gates. |
| `docs/CLIENT_THOR_TEST.md` | Current test link. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test link. |
| `docs/IMPLEMENTATION_PLAN.md` | Current connection architecture and remaining gates. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and paired-report instructions. |
| `graphics-compat/README.md` | Current Thor evidence and local connection test link. |
| `runtime-probe/src/client/ClientConnectionMonitor.java` | Read-only engine/splash/transport observations and bounded startup stack samples. |
| `runtime-probe/src/client/DirectClientLaunch.java` | Own the observer for the existing game thread lifetime. |
| `scripts/ProbeClientConnection.java` | Developer-only actual-client ticket wire, auth parser and login-dispatch fixture. |
| `scripts/verify-managed-apk.py` | Require packaged connection monitor and sample classes. |
| `tests/test_client_connection.py` | Three read-only observer, bounded-message and ABI-failure tests. |
