# Wurm Server 0.10.6 — client material startup

The Thor rendered the actual Wurm splash in 0.10.5. Startup then failed while
preparing terrain because the Gaussian-blur material's GLSL 3.30 shaders could
not compile through the current graphics path.

This preview converts only those two verified imported resources to equivalent
GLSL 1.20 syntax in the private session overlay. It also corrects a pinned LWJGL
uniform/attribute output bug and tests native shader reflection before material
loading. Imports and the working server remain unchanged; no proprietary files
are included. Full terrain rendering, audio, login and world entry remain pending.

Install **0.10.6-managed-preview**, code **20**, package
`io.github.russianranger.wurmlauncher.clientmaterials`, alongside the working server.

1. Import the same complete client ZIP in the new app's **Client** tab.
2. Start Adventure in the working **0.6.0 server app** and wait for its game port.
3. Select **0.10.6 → Client → Start Local Game** (127.0.0.1:3724).
4. After failure, timeout or Stop Client, select **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot of any new screen.

No PC, root, Termux commands, new game files or repeat triangle test is needed.
The preview retains its two-minute startup limit. See **CLIENT_MATERIALS_FIX.md**
for the evidence, architecture, every changed file and exact Thor steps.

All 78 automated tests pass. The 317-member client API audit and real native
window/controller regression pass. A private probe loads the actual Wurm blur
material and verifies its rendered pixel through GL4ES; original shaders reproduce
the failure. CI builds/tests/lints all variants and verifies packaged helpers,
runtime/graphics hashes, signing and exact POC bytes.
