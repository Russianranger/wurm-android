# Wurm Server 0.10.2 — Android client fonts

Your 0.10.1 Thor report confirms keybindings, player profile and all three resource
packs now load. The client advanced into HUD construction, then failed in Java
font initialization: `Fontconfig head is null`.

This release maps Java's logical fonts to the Thor's readable system fonts before
Wurm starts. It verifies real text measurement/rasterization and logs font files,
hashes and any failure's deepest cause. Wurm's original FontTexture is retained.
The exact old failure was reproduced with the private client JAR; the fix passed
its real font metrics and glyph drawing for 12 font/style combinations on the host.

Install `Wurm-Server.apk` alongside the working server: **0.10.2-managed-preview**
(16), package `io.github.russianranger.wurmlauncher.clientfonts`.

1. Import the same complete client ZIP in this new app.
2. Start Adventure in your working 0.6.0 server app.
3. Select **0.10.2 → Client → Start Local Game**.
4. After exit or Stop Client, select **Export Client Report** and send
   **wurm-client-report.txt**, describing what appeared.

No new game files, fonts, JavaFX, PC, root or Termux commands are required. The
triangle/controller test does not need repeating. See **CLIENT_FONTS_FIX.md** for
exact steps and every changed file. This remains a two-minute startup diagnostic;
ARM64 font rendering, full Wurm graphics/audio, login and world entry need testing.

No proprietary game files or system font binaries are bundled. The working server,
SQLite fix, Steam shim and native window/input backend are unchanged. Public
source, notices and matching runtime/graphics source accompany the APK.
