# Thor 0.7.0 client bootstrap findings — 2026-09-08

The supplied `wurm-client-report.txt` is 62,324 bytes, SHA-256
`4ee77094821c1862fdfe456a29af243f9938cd5da5e66de9e8d543434e5f7eb3`.
Only these engineering findings are committed; the raw report and game files
are not repository inputs. The published APK remains the immutable 0.7.0 release.

## Observations

| Area | Evidence | Accepted scope |
| --- | --- | --- |
| Import | `CLIENT_IMPORT_OK`, 22 entries, 1,635,657,345 bytes; generation `1eec50e9-f02b-47f7-ab66-01ccc0b757ad` | Core client import validation passed; assets need real startup to establish completeness |
| Layout | Engine, LWJGL2 Display/Keyboard/Mouse/OpenAL and JInput class providers all in `client.jar`; `packs` and `nativelibs` present | This is a client JAR with bundled dependencies, not a layout requiring separate Java LWJGL JARs |
| Runtime | Java `17.0.20-internal`, `aarch64`, ordinary UID 10200; runtime hashes verified | APK-owned Java startup passed |
| Engine | `ENTRY_INITIALIZED com.wurmonline.client.WurmClientBase` | Class initialization passed; the game launch method was not invoked |
| Local server | `TCP_PROBE target=127.0.0.1:3724 reachable=true` | Same-device TCP reachability passed; no Wurm login |
| Android graphics | Qualcomm Adreno 740, OpenGL ES 3.2; surfaces 1896x544 and 1896x506 | Android GLES context/surface worked; no Wurm rendering |
| Controller | Xbox Wireless Controller, id 10, vendor 8224, product 274; X/Y, Z/RZ, hats and both trigger-axis variants observed | Android detection, WASD/mouse/buttons/modifiers/hotbar translation and reset events observed |
| JVM input delivery | No `Client operation: input`, `INPUT_READY` or `INPUT_RECEIVED` in supplied report | Receiver test is unverified; Android `TRANSLATE` lines alone do not establish transport delivery |
| Persisted profile | Profile section empty, no `PROFILE_SAVED` event | Defaults were available; edited profile persistence is not demonstrated by this report |

The client JAR SHA-256 is
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
The common JAR hash remains
`066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633`.
Do not infer missing dependencies solely from the four-JAR classpath: the
inventory explicitly finds LWJGL and other classes inside this client JAR.

## Exact bootstrap contract now known

```java
public static void com.wurmonline.client.WurmClientBase.launch(
    com.wurmonline.client.settings.Profile.PlayerProfile profile,
    com.wurmonline.client.resources.Resources resources,
    boolean option);
```

`option` is a placeholder name in this document, not a recovered parameter name
or an assertion about its meaning. The JVM descriptor is:

```text
(Lcom/wurmonline/client/settings/Profile$PlayerProfile;Lcom/wurmonline/client/resources/Resources;Z)V
```

The preview's zero-argument-only adapter rejected this method with
`ENTRY_ABI_REQUIRED`; this is an explicit unimplemented adapter, not a failure
to load Wurm or Java. Need the actual construction/factory and launch call path
before supplying a profile, resources or boolean. The report does not contain
those method bodies and does not establish a connection-setting API.

The discovered client Steam class is `SteamJni.Steam_api` **inside client.jar**,
with an instance constructor taking `com.wurmonline.client.steam.SteamHandler`.
It has instance JNI methods including `SteamAPI_Init()Z`,
`IsSteamUserLoggedOn()Z`, `GetCSteamIDString()Ljava/lang/String;`, and
`GetAuthSessionTicket()Lcom/wurmonline/client/steam/SteamAuthTicket;`.
Do not reuse the server's `SteamServerApi` shim or assume tickets are returned as
raw byte arrays. Inspect SteamHandler, SteamAuthTicket and their call sites to
build only the observed local/offline adapter. No Steam ticket acceptance was
attempted in this report.

## First graphics blocker and source correction

The graphics child exits 42 with `UnsatisfiedLinkError` for `libawt_xawt.so`.
Its stack passes through AWT Toolkit, `org.lwjgl.LinuxSysImplementation`,
`org.lwjgl.Sys` and `org.lwjgl.opengl.Display` static initialization. Therefore
`Display.create()` did not yet execute, and there is no desktop OpenGL context
or GL capability result from Wurm/LWJGL in this report.

This first blocker is caused by the preview's `-Djava.awt.headless=false` option.
`runtime-build/build.sh` builds Java with `--enable-headless-only=yes`. The
released APK contains `libawt.so`, `libawt_headless.so` and `libjawt.so`, with no
`libawt_xawt.so`. Source now selects `-Djava.awt.headless=true` and logs the AWT
mode explicitly. That aligns AWT initialization with the packaged runtime; it
does not supply Android LWJGL native functions, X11, a Wurm window or rendering.
The next native result still requires a physical run of a future build.

Keep the [Pojav LWJGLX/native-window/GL4ES integration route](CLIENT_INTEGRATION.md)
as the graphics work. The imported client bundles its LWJGL2 classes, so an
adapter must deliberately take classpath precedence without rewriting that JAR.

## Matching client supplied; 0.8.0 follow-up

The matching client.jar has now been provided and inspected privately. Its
30,177,833 bytes hash to the value above. No further JAR upload is needed.

The actual desktop launcher creates Profile.PlayerProfile through the Profile
singleton/factory and passes Resources(File,List) plus false to launch. The
boolean is not read in that launch method. Launch starts a separate gameThread
and returns. The engine itself still uses WurmMain console/logging and endpoint
utilities, so bypassing the JavaFX UI requires a small source-built utility class.
The engine's connection path reads getServerIp/getServerPort, obtains the typed
SteamAuthTicket, sends its normal packet and waits for auth before login.

Host testing of the authored 0.8.0 compatibility asset against this actual JAR
passed SteamHandler.initializeSteam and creation of the real SteamAuthTicket,
with `STEAM_COMPAT_OK` and exit 0. Broad reflection on SteamHandler first exposed
an unused JavaFX browser method signature; typed ClientHooks fixed that without
supplying fake JavaFX classes. Ticket acceptance by the server was not tested.

The real Profile setup then reached Options → DisplayOption → DisplayDevice →
LWJGL Sys/Display and failed to load native lwjgl on the development host. This
occurred before invoking launch and before loading real external resource packs.
The supplied input was the client JAR alone, not the full client installation.
0.8.0 also validates local packs before profile construction on the device.
Thor native behavior after the headless-AWT correction remains to be measured.

The user could not find the separate receiver-start option. The cause is not
established from a screenshot. 0.8.0 removes that prerequisite: **Client tab →
Start Controller Test** opens the screen and starts the receiver automatically.
Touch Exit/Stop/Retry controls sit in a horizontally scrollable top row.
Follow [CLIENT_THOR_TEST.md](CLIENT_THOR_TEST.md), wait for READY and export
Client Report. No Wurm import is required for the controller test.

[CLIENT_INTEGRATION.md](CLIENT_INTEGRATION.md) lists every 0.8.0 file change.
The 0.7.0 release is kept immutable and the 0.8.0 package installs separately.
