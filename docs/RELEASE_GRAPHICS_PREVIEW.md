# Wurm Server 0.10.7 — graphics capability fix

The Thor's 0.10.6 report passes the blur and shader-query fixes, then fails at
`material.simple` during water-mesh setup. The underlying issue is inaccurate
capability reporting: a pinned LWJGL workaround advertises OpenGL 3.3 support on
the GL4ES 2.1 backend, enabling Wurm's modern renderer.

This release restores three capability return values and checks the actual GL
capabilities and Wurm renderer selection. Wurm selects its existing legacy/basic-
water path. No new shader conversions, proprietary files or server changes are
included. Full terrain rendering, audio, login and world entry remain pending.

Install **0.10.7-managed-preview**, code **21**, package
`io.github.russianranger.wurmlauncher.clientcapabilities`, alongside your working server.

1. Import the same complete client ZIP in the new app's **Client** tab.
2. Start Adventure in the working **0.6.0 server app** and wait for its game port.
3. Select **0.10.7 → Client → Start Local Game** (127.0.0.1:3724).
4. After failure, timeout or Stop Client, select **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot of any new screen.

No PC, root, Termux commands, new game files or repeat triangle test is needed.
The preview retains its two-minute startup limit. See **CLIENT_CAPABILITIES_FIX.md**
for the evidence, every changed file and exact Thor steps.

All 79 automated tests pass. The 317-member API audit and native graphics/input
regression pass. A private probe reproduces the wrong renderer selection with
the old API and passes the corrected selection, real Volume constructor and
existing blur draw. CI builds/tests/lints all variants and verifies packaged
helpers, runtime/graphics hashes, signing and exact POC bytes.
