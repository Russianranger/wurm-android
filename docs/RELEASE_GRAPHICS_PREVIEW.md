# Wurm Server 0.10.3 — client window startup

The Thor's 0.10.2 report confirms the font fix and entry into Wurm's main thread.
Window cleanup then hid the startup error. Testing the actual client reproduced
JavaFX icon loading and a desktop maximized-window/AWT screen-size dependency.

This release supplies a headless icon helper, applies a real 960x540 display option,
makes window cleanup safe, and preserves original errors without a Swing dialog.
The actual client's window initialization now passes on host GL4ES, with imported
icons, keyboard/mouse setup, checked sample-frame readback and repeated cleanup.
The existing triangle/input regression and all 71 automated tests also pass.

Install **0.10.3-managed-preview** (code **17**), package
`io.github.russianranger.wurmlauncher.clientwindowfix`, alongside the working server.

1. Import the same complete client ZIP into this new app.
2. Start Adventure in the working **0.6.0 server app**; wait for its listening port.
3. Choose **0.10.3 → Client → Start Local Game** (127.0.0.1:3724).
4. After exit, timeout or Stop Client, choose **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot if a Wurm screen appeared.

No additional game files, PC, root or Termux commands are needed. No repeat
triangle/controller test is required. See **CLIENT_WINDOW_START_FIX.md** for the
exact test, diagnostic markers, every changed file and remaining limitations.

This is a Gate 4 window-startup correction and remains a two-minute diagnostic.
Host window creation does not prove Wurm scene rendering, audio, authentication
or world entry. The next Thor report must establish the next stage reached.

No proprietary game files are bundled. The working server/runtime, SQLite fix,
Steam shim and native graphics backend retain their existing behavior. Public
source, notices and corresponding runtime/graphics source accompany the APK.
