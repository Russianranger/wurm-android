# Wurm Server 0.10.0 — client window and LWJGL input

Install `Wurm-Server.apk` on the AYN Thor. Package
`io.github.russianranger.wurmlauncher.clientwindow`, version
`0.10.0-managed-preview` (14), installs alongside the working server and previews.
No PC, root, Termux or separate Java installation is required.

The Thor passed 0.9.1's native graphics test twice. This release advances to a
Pojav Java GLFW window adapter and actual LWJGL2 keyboard/mouse queues.

1. Client tab → LWJGL Window / Input Test → Run Window / Input Test.
2. Left stick moves the triangle, right stick moves the cyan crosshair; A/RT
   change its color with left-click, LT with right-click. Test the other mapped
   keys, then Finish Window Test. Export **Client Report**.
3. Import the same complete legally obtained client ZIP in this new app. Start
   the working 0.6.0 server, then use 0.10.0 → Start Local Game. Export a second
   **Client Report** after the attempt, even if no game image appears.

See attached `GRAPHICS_THOR_TEST.md` for exact steps and file contents. The window
probe runs for up to 90 seconds; Wurm startup is a two-minute diagnostic. Frames
are bounded readbacks, not a production-rate Android surface. Full Wurm rendering,
audio, authentication and world entry remain unqualified. The report exposes
classpath, window/input markers, startup stack traces and crash/exit information.

The server runtime/POC/SQLite implementation and Steam shim are unchanged. No
proprietary game files are bundled. Source pins, notices and corresponding runtime
and graphics source accompany the APK, including the adapted Pojav Java layer.
