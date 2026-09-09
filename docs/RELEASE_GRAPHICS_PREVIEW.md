# Wurm Server 0.10.1 — headless client keybindings

Your 0.10.0 Thor window/controller test passed: 366 frames, real LWJGL input and
clean exit. The local-client failure was `javafx/stage/Stage` while Profile loaded
keybindings through the desktop settings class, before login.

This release replaces that settings helper with headless binding storage. It
preserves the real Profile/Options code and unchanged binding files. The exact
failure was reproduced with the supplied client JAR; the fix passed actual
profile/player creation in two fresh host JVMs, loading all 78 default keys.
No server/SQLite, Steam shim or native graphics changes are included.

Install `Wurm-Server.apk` alongside the working server: version
**0.10.1-managed-preview** (15), package
`io.github.russianranger.wurmlauncher.clientsettings`.

1. Import the same complete client ZIP in this new app.
2. Start Adventure in your working 0.6.0 server app.
3. In 0.10.1 select Client tab → Start Local Game.
4. Export **Client Report** after the attempt and send `wurm-client-report.txt`.

No PC, root, Termux, JavaFX or new game-file download is needed. The triangle test
is already confirmed. See attached **CLIENT_SETTINGS_FIX.md** for exact steps,
file requirements, diagnostics and every changed file. Startup remains a
two-minute diagnostic; full Wurm graphics, audio, login and world entry remain
unqualified. Desktop JavaFX settings dialogs are not implemented.

No proprietary game files or compile-only signature stubs are packaged. Public
source, license notices and matching runtime/graphics source accompany the APK.
