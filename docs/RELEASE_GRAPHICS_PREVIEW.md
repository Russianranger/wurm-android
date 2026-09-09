# Wurm Server 0.10.5 — client buffer startup

The Thor passed the real framebuffer support check in 0.10.4. Wurm then reached
splash texture creation and failed in Java 8-era buffer cleanup on Java 17.

This preview adapts the two SHA-verified buffer classes privately at startup,
adds two client-only module exports and tests real allocation/cleanup before game
launch. The imported files and working server are unchanged. No proprietary files
are included. Full game rendering, audio, login and world entry remain unverified.

Install **0.10.5-managed-preview**, code **19**, package
`io.github.russianranger.wurmlauncher.clientbuffers`, alongside the working server.

1. Import the same complete client ZIP in the new app's **Client** tab.
2. Start Adventure in the working **0.6.0 server app** and wait for its game port.
3. Select **0.10.5 → Client → Start Local Game** (127.0.0.1:3724).
4. After failure, timeout or Stop Client, use **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot if a Wurm screen appears.

No PC, root, Termux, new game files or repeated triangle test is needed. The
preview remains a two-minute startup diagnostic. See **CLIENT_BUFFERS_FIX.md**
for every changed file, technical evidence, checks and exact Thor steps.

The 76 automated tests include real native-buffer cleanup in authored fixtures.
A private probe using the supplied client also restores native direct-buffer
memory across 64 repeated allocations. CI builds/tests/lints all variants and
verifies packaged helpers, runtime/graphics hashes, APK signing and exact POC bytes.
