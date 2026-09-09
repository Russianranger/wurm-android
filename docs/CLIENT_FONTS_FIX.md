# AYN Thor: 0.10.2 Android font configuration

## What 0.10.1 proved

The September 9 `wurm-client-report (3)(1).txt` confirms inventory and local Steam
compatibility, 70 actions / 78 keys loaded and preserved, real PlayerProfile
creation, and all three resource packs opened. The adapter then invoked
`WurmClientBase.launch(PlayerProfile, Resources, false)`. The private client JAR
SHA-256 remains `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.

HUD construction failed while FontTexture requested Java 2D font metrics:

```
java.lang.RuntimeException: Fontconfig head is null, check your fonts or fonts configuration
    at java.desktop/sun.awt.FontConfiguration.getVersion(...)
    ...
    at com.wurmonline.client.renderer.gui.text.FontTexture.<init>(FontTexture.java:112)
```

No game frame or login was reached. This is a new dependency exposed after the
successful profile fix. The prior window/controller test remains qualified.

## Implementation

Only the client entry process receives `wurm.client.fontDir=/system/fonts` and
an owned session `wurm.client.fontConfig` path. Before loading the client,
`ClientFonts` creates an explicit OpenJDK configuration, sets `sun.awt.fontconfig`
and `sun.java2d.fontpath`, validates the chosen physical fonts, and measures/draws
sample text for five logical families and four styles. Each sample must have
supported characters, usable metrics and visible pixels. Wurm then uses its
original FontTexture and Java 2D implementation.

Selection prefers Roboto (then NotoSans/DroidSans) for sans-serif, NotoSerif or
DroidSerif for serif, and DroidSansMono/NotoSansMono/RobotoMono for fixed pitch.
Available bold/italic files are used; missing styles use synthesized styles.
Missing optional families fall back to sans-serif with an explicit log; that
fallback cannot promise fixed-pitch appearance. Missing required fonts, invalid
files, failed rasterization or an existing configuration fail visibly.

OS font files are read, hashed and logged, never copied into the repo or release.
The configuration is new and private to that session; existing cleanup removes
it. No runtime image, server/SQLite code, native graphics, controller mapping or
Steam shim changes are needed. This supplies CPU text rendering, not a JavaFX
window, audio backend or replacement gameplay implementation. Broad multilingual
coverage is unqualified; the initial raster check uses Latin text. The real engine
retains its own glyph selection and pack fonts.

The first error/status line now follows nested exception causes instead of only
showing an InternalError/InvocationTargetException wrapper. Full traces remain.

The mapping follows the [OpenJDK 17 font configuration format](https://docs.oracle.com/en/java/javase/17/intl/font-configuration-files.html).
The bundled runtime's pinned [FontConfiguration](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/java.desktop/share/classes/sun/awt/FontConfiguration.java)
supports the explicit property; its [X11FontManager](https://github.com/openjdk/jdk17u/blob/8cbbca61432426a3441aa08838d930ef954ea1ba/src/java.desktop/unix/classes/sun/awt/X11FontManager.java)
selects this configuration without depending on desktop font discovery. Headless
Java 2D remains separate from the existing LWJGL/EGL window bridge.

## Verification and limits

Public tests use installed DejaVu fonts as temporary Android-named fixtures; no
font binaries or proprietary files are committed. An empty native fontconfig
environment prevents reliance on the host's normal mappings. Tests verify that
all 20 logical styles draw pixels, optional fallbacks work, missing/corrupt/large
font failures are visible, original fonts and existing configurations are
preserved, and nested root causes reach the first status line.

With the privately supplied actual client JAR, the original path reproduced the
same FontTexture line 112 / Fontconfig-head error. The new mapping passed the real
constructor, metrics and glyph-image method for SansSerif, Serif and Monospaced in
four styles (12 combinations). The test does not substitute an authored
FontTexture. It requires no game packs or GL upload/login. Host validation uses
x86-64 Java 17; ARM64 and the Thor's actual font bytes still require device testing.

Optional developer regression: compile `scripts/ProbeClientFonts.java` against
the built runtime-probe JAR/classes. Use Java 17 in a disposable directory and this
classpath order: compiled probe, runtime-probe.jar, client-compat.jar,
wurm-window.jar, graphics-probe.jar, pojav-wurm-api.jar, private client.jar.
Set `java.awt.headless=true`, a private `user.home`, `wurm.client.offline=true`,
`wurm.client.host=127.0.0.1`, `wurm.client.port=3724`, `wurm.client.fontDir` to a
fixture directory with the named font files, and `wurm.client.fontConfig` to a
**nonexistent file in an existing disposable directory**. The source-built host
native paths and allocator options from `scripts/test-window-host.py` may be
retained. Never use a live player directory. For the negative comparison omit the
two `wurm.client.font*` properties and use an empty native `FONTCONFIG_FILE` plus
an empty `FONTCONFIG_PATH`.

## Exactly what to install and test

1. Keep the working server and previous client previews installed. Download
   [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.2-client-fonts/Wurm-Server.apk)
   from [0.10.2](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.2-client-fonts).
   Install alongside them. Verify **0.10.2-managed-preview**, version code **16**,
   package `io.github.russianranger.wurmlauncher.clientfonts`. The separate package
   accommodates CI debug signing and preserves the old Adventure/import data.
2. Open **0.10.2 → Client → Import Client ZIP** and import the same complete ZIP
   that already passed. This package needs its own import. Keep `client.jar`,
   `common.jar`, full `lib/`, full `packs/` and the other original assets together.
   No extra game files, fonts, JavaFX, Java installation, PC, root or Termux commands
   are needed; the APK reads the Thor's system fonts.
3. Start Adventure in the working **0.6.0 server app**, wait for its game port,
   then return to **0.10.2 → Client → Start Local Game**. The target remains
   **127.0.0.1:3724**; the server keeps its separate controls.
4. Watch the screen. If anything appears, note whether it is a menu, login or
   world and whether controls respond. This remains a two-minute diagnostic,
   not a qualified playable release. No repeat triangle/controller test is needed.
5. After exit, timeout or **Stop Client**, tap **Export Client Report** and send
   **wurm-client-report.txt**, plus a screenshot if a Wurm image appeared.
   Export **Client Report**, not server Session, Storage or World reports.

Expected new markers: `FONT_CONFIG_BEGIN`, `FONT_MAP`, `FONT_FILE`,
`FONT_CONFIG_READY`, `FONT_FILE_VALIDATED`, `FONT_RASTER_OK` and
`FONT_PREFLIGHT_PASS logicalStyles=20`, then the existing real-client startup
markers or its next exception. The keybinding/profile markers should still pass.
A font preflight is not proof of Wurm rendering or login.

**Gate status:** the observed profile/resource part of Gate 2 is physically
confirmed; the font substep of Gate 4 is host-verified and awaits Thor results.
Full Gate 4 game rendering/audio and Gate 5 authentication/world entry remain open.

## Files changed

| File | Change |
| --- | --- |
| `runtime-probe/src/client/ClientFonts.java` | System-font mapping, file identities and real Java 2D raster preflight. |
| `runtime-probe/src/client/ClientBootstrap.java` | Setup before client loading, developer font probe and nested root causes. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Entry-only font settings; report 0.10.2 and current qualification. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Identify the new version. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Version label; rendering behavior unchanged. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the font-fix APK. |
| `app/build.gradle.kts` | Version code 16, 0.10.2 and separate clientfonts package. |
| `tests/test_client_fonts.py` | Six real-font regression tests without normal desktop font discovery. |
| `tests/test_client_bootstrap.py` | Verify the nested root cause in the first failure line. |
| `scripts/ProbeClientFonts.java` | Optional actual FontTexture metrics/glyph regression. |
| `scripts/verify-managed-apk.py` | Require ClientFonts in the packaged helper. |
| `.github/workflows/android.yml` | Require test font, publish 0.10.2 and attach guide/checksum. |
| `docs/CLIENT_FONTS_FIX.md` | Diagnosis, architecture, references, validation, test steps and file inventory. |
| `docs/IMPLEMENTATION_PLAN.md` | Successful profile test and next font substep. |
| `docs/CLIENT_INTEGRATION.md` | Font integration and qualification. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | 0.10.2 release notes and physical test. |
| `docs/CLIENT_THOR_TEST.md` | Current client test link. |
| `docs/GRAPHICS_THOR_TEST.md` | Current client test link. |
| `README.md` | Current client test link. |
