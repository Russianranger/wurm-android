# Build from native ARM64 Termux

Use **normal Termux, outside proot, not `su`**. Root is needed to *run the server
from the app*, not to build an APK. Keep the checkout in Termux's private home,
not `/sdcard` (shared storage has unsuitable execution/permission semantics).
Allow several GB of free storage and an internet connection for the first build.

Pinned toolchain: JDK 17, Gradle 8.2.1, Android Gradle Plugin 8.2.2, Kotlin 1.9.22,
SDK platform 34, Build Tools 34.0.0. The older, deliberately fixed SDK/toolchain
keeps this Android 13 sideloading milestone reproducible.

## 1. Record your server Java, then install build dependencies

Before installing another JDK, record the Java binary your working POC uses:

```bash
readlink -f "$(command -v java)"
java -version
```

Keep that full resolved path for the launcher's Java field. Installing a JDK can
change Termux's default `java` alternative; **building with JDK 17 does not mean
you should replace the POC's working server JRE**.

```bash
pkg update
pkg install git curl unzip openjdk-17 aapt2 bash util-linux
```

Current Termux repositories provide `aapt2` as its own package. If unavailable,
check your Termux repository/mirror configuration first (`termux-change-repo`).
Do not substitute a desktop x86-64 binary or fetch an untrusted prebuilt tool.

## 2. Install Google's command-line SDK tools

The commands below use a dedicated `android-sdk-wurm` directory. If you already
have a complete SDK, use its directory for `ANDROID_HOME` and skip extraction.
Do not move another SDK installation or overwrite existing `cmdline-tools/12.0`.

```bash
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="$HOME/android-sdk-wurm"
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$ANDROID_HOME/cmdline-tools"
wurm_sdk_tmp=$(mktemp -d)
curl -fL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
  -o "$wurm_sdk_tmp/commandline-tools.zip"
unzip -q "$wurm_sdk_tmp/commandline-tools.zip" -d "$wurm_sdk_tmp"
test ! -e "$ANDROID_HOME/cmdline-tools/12.0" && \
  mv "$wurm_sdk_tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/12.0"
```

This pins command-line tools 12.0, which can run with JDK 17. The temporary
download is left intact; you can delete that specific temporary directory later.
Use Bash explicitly for SDK scripts in Termux:

```bash
bash "$ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager" \
  --sdk_root="$ANDROID_HOME" --licenses

bash "$ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager" \
  --sdk_root="$ANDROID_HOME" "platforms;android-34" "build-tools;34.0.0"
```

Read and accept the required Google SDK licenses yourself when prompted.
No emulator, desktop platform-tools, NDK, or CMake is needed.

**ARM64 caveat:** Google's Linux SDK build-tools contain desktop-native
executables. For this Kotlin-only project, the helper selects Termux's native
`$PREFIX/bin/aapt2` instead of Gradle's downloaded desktop resource compiler.
It also disables optional Gradle native integration/file watching. The project
uses no AIDL, shaders, native build, or native-symbol stripping. Do not replace
whole SDK directories with ARM binaries; the SDK metadata and Java tools are
still needed. This is a community-style on-device build workflow, not a claim
that Google officially supports its desktop SDK on Android.

## 3. Clone and compile

For a new checkout:

```bash
cd "$HOME"
git clone https://github.com/Russianranger/wurm-android.git
cd wurm-android
bash scripts/build-termux.sh
```

For an existing checkout, enter it and run the helper (review/commit local edits
before using `git pull`). The helper selects JDK 17 **inside its own process**,
uses the SDK above by default, and builds the debug APK plus JVM unit tests.
For a differently located SDK:

```bash
ANDROID_HOME=/absolute/path/to/your/sdk bash scripts/build-termux.sh
```

To run Android lint too:

```bash
bash scripts/build-termux.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The Gradle wrapper downloads the pinned distribution and verifies its SHA-256.
The initial Maven/Google dependency downloads can take several minutes. Do not
upgrade only one component of the pinned toolchain while troubleshooting.

## 4. Install the debug APK

```bash
termux-setup-storage
cp app/build/outputs/apk/debug/app-debug.apk "$HOME/storage/downloads/wurm-launcher-debug.apk"
termux-open --view "$HOME/storage/downloads/wurm-launcher-debug.apk"
```

If the installer does not open, use Android Files → Downloads and tap the APK.
Allow installs from the app opening it when Android asks. Debug builds are
automatically signed with your local debug key; no release key is needed.
The copy command replaces the earlier APK of that same name in Downloads.

## Alternative: build on GitHub from your phone

1. Open the repository's **Actions** tab and select **Android debug APK**.
2. Open a successful run, or select **Run workflow** on `main`.
3. In **Artifacts**, download `wurm-launcher-debug`, unzip it, and install
   `app-debug.apk` using Android Files. You must be signed in to download artifacts.

Actions uses a temporary debug signing key per fresh runner. An APK built in
Termux, or in another Actions run, may have a different key and fail to update
an installed copy. Uninstall the old launcher before switching keys (this clears
launcher settings/logs, **not** the Wurm runtime in Termux). Stop the server first.

## Troubleshooting

| Error | Check |
| --- | --- |
| `aapt2` cannot execute / wrong architecture | Run the helper, then verify `$PREFIX/bin/aapt2 version`. Do not run the Maven Linux binary. |
| SDK location not found | Set `ANDROID_HOME`; ensure `platforms/android-34/android.jar` exists. Remove an obsolete local `sdk.dir` entry only after checking `local.properties`. |
| Unsupported Java / missing compiler | `"$PREFIX/lib/jvm/java-17-openjdk/bin/javac" -version`; build using JDK 17. |
| Gradle native library / file watching failure | Use the helper's `org.gradle.native=false` and `org.gradle.vfs.watch=false` options. |
| Build killed / exit 137 | Stop Wurm and other memory-heavy apps; try `--max-workers=1`. Keep Termux foreground for the initial build. |
| Dependencies cannot be downloaded | Check network, clock, and SDK licenses. First build cannot work offline. |
| Install reports signature mismatch | Stop the server, uninstall the old launcher, then install the new debug APK. |
| App says Java/Bash/flock missing | These are **runtime** dependencies in Termux; APK build success does not install a server/JRE. |

## Desktop build (optional)

With JDK 17 and the same SDK packages on a supported desktop:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Do not pass the Termux `aapt2` override on desktop.

## Upstream references

- [AGP 8.2 compatibility: Gradle 8.2+, JDK 17, SDK 34](https://developer.android.com/build/releases/agp-8-2-0-release-notes)
- [Google SDK command-line setup and licenses](https://developer.android.com/tools/sdkmanager)
- [Termux aapt/aapt2 build recipe](https://github.com/termux/termux-packages/tree/master/packages/aapt)
- [Termux OpenJDK 17 package](https://github.com/termux/termux-packages/tree/master/packages/openjdk-17)
