# Wurm Server 0.10.4 — client offscreen startup

0.10.3 created Wurm's real 960x540 window on the Thor. It then rejected a legacy
Pbuffer requirement. Its sound engine failed separately and fell back to silent mode.

The supplied client actually uses FBOs for offscreen rendering. This release
adapts its single obsolete capability call, only for the inspected engine hash,
to require a working FBO allocation and checked pixel readback. It generates a
small private overlay per attempt; the imported JAR is unchanged. Other capability
checks remain active and the public LWJGL Pbuffer API remains unsupported.

The real Wurm support check and FBO class pass host tests, including depth texture
allocation/readback. The full triangle/controller/reset regression also passes
with GL4ES's saved shader archive disabled using its documented setting.

Install **0.10.4-managed-preview**, code **18**, package
`io.github.russianranger.wurmlauncher.clientoffscreen`, alongside the working server.

1. Import the same complete client ZIP into this new app.
2. Start Adventure in the working **0.6.0 server app** and wait for its game port.
3. Select **0.10.4 → Client → Start Local Game** (127.0.0.1:3724).
4. After exit, timeout or Stop Client, select **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot if a Wurm screen appears.

No new game files, PC, root or Termux commands are required. No repeat triangle
exercise is needed. See **CLIENT_OFFSCREEN_FIX.md** for exact steps, source
references, every changed file and diagnostic markers.

This remains a two-minute Gate 4 startup diagnostic. Actual Wurm scene rendering,
audio, local authentication and world entry still require device evidence. The
working server, POC, managed runtime and SQLite fix are intact. No proprietary
files or generated game overlay are included in the repository or release assets.
Public graphics/runtime sources and license notices accompany the APK.
