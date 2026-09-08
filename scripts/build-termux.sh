#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

: "${PREFIX:?Run this script in native Termux, not inside proot or a root shell.}"
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk-wurm}"
export PATH="$JAVA_HOME/bin:$PATH"
[[ -x "$JAVA_HOME/bin/javac" ]] || { echo 'Install openjdk-17 in Termux first.' >&2; exit 1; }
[[ -x "$PREFIX/bin/aapt2" ]] || { echo 'Install the Termux aapt2 package first.' >&2; exit 1; }
[[ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]] || {
    echo 'SDK platform 34 is missing; follow docs/BUILD_TERMUX.md.' >&2; exit 1;
}
[[ -d "$ANDROID_HOME/build-tools/34.0.0" ]] || {
    echo 'SDK build-tools 34.0.0 is missing; follow docs/BUILD_TERMUX.md.' >&2; exit 1;
}
"$PREFIX/bin/aapt2" version
if [[ $# == 0 ]]; then set -- :app:assembleDebug :app:testDebugUnitTest; fi
# Google/Maven's Linux aapt2 is not an Android ARM64 executable. Gradle's
# optional Linux native integration/file watcher is also unsuitable for Bionic.
exec bash ./gradlew --no-daemon --max-workers=2 \
    -Dorg.gradle.native=false -Dorg.gradle.vfs.watch=false \
    "-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2" "$@"
