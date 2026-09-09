# Wurm Server 0.10.8 — client crash diagnostic

The Thor confirms 0.10.7's accurate graphics capabilities and existing legacy
renderer selection. The client now exits **134 during builtin material preload**,
without a Java exception or native cause in the report.

This build adds bounded, flushed graphics-call breadcrumbs and source hashes,
plus available own-app Android crash details. The failure status retains the exit
and unfinished call. This is a diagnostic milestone; the device crash is not yet
fixed. Full rendering, audio, local login and world entry remain pending.

Install **0.10.8-managed-preview**, code **22**, package
`io.github.russianranger.wurmlauncher.clientcrash`, alongside the working server.

1. Import the same complete client ZIP in **0.10.8 → Client**.
2. Start Adventure in the working **0.6.0 server app** and wait for its game port.
3. Select **0.10.8 → Client → Start Local Game** (127.0.0.1:3724).
4. Let it fail or time out; wait for **Collecting crash details** to finish.
5. Select **Back to Client / Export → Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot of any new screen.

No PC, root, Termux commands, new game files or repeat triangle test is needed.
The preview retains its two-minute startup limit. Android may withhold native
crash details; explicit unavailable markers and graphics traces remain useful.
See **CLIENT_CRASH_DIAGNOSTIC.md** for evidence, every changed file and Thor steps.

All **84** Python/Java/native tests and **five new Kotlin tests** pass locally.
The 317-member API audit, native graphics/controller regression and private real-
client material/blur probe pass with tracing. CI builds/tests/lints all variants
and verifies signing, runtime/graphics hashes, packaged trace delegates and exact
POC bytes before publication. Native pins and the private overlay are unchanged;
proprietary Wurm files are not included.
