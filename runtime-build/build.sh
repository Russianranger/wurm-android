#!/usr/bin/env bash
# Run from a fresh Ubuntu 22.04 directory, with a JDK 17 boot JDK on PATH.
set -euo pipefail
recipe_dir=$(cd "$(dirname "$0")" && pwd)
work_dir=${1:?Usage: bash runtime-build/build.sh EMPTY_BUILD_DIRECTORY}
mkdir -p "$work_dir"
cd "$work_dir"
download() {
  local name=$1 digest=$2 url=$3
  if ! test -f "$name" || ! echo "$digest  $name" | sha256sum --check --status; then
    curl -fsSL --retry 3 --max-time 900 "$url" -o "$name.partial"
    echo "$digest  $name.partial" | sha256sum --check
    mv "$name.partial" "$name"
  fi
}
download OpenJDK17-source.tar.gz 5f0a079dee3e5a465b2f550f61b650ad51dda40475c7fea9ca6c1009757f7ae8 https://github.com/openjdk/jdk17u/archive/8cbbca61432426a3441aa08838d930ef954ea1ba.tar.gz
download Android-build-source.tar.gz 891fe80e57cc0efa3087d2b2ae9160a449f5a07117fa69c2e6e6d7a4f740c4ab https://github.com/FCL-Team/Android-OpenJDK-Build/archive/1474641f7cd2ec2f736d0ad51c93b7cacc041848.tar.gz
download freetype-source.tar.gz 955e17244e9b38adb0c98df66abb50467312e6bb70eac07e49ce6bd1a20e809a https://downloads.sourceforge.net/project/freetype/freetype2/2.10.0/freetype-2.10.0.tar.gz
download cups-source.tar.gz 596d4db72651c335469ae5f37b0da72ac9f97d73e30838d787065f559dea98cc https://github.com/apple/cups/releases/download/v2.2.4/cups-2.2.4-source.tar.gz
download ndk-r21.zip b65ea2d5c5b68fb603626adcbcea6e4d12c68eb8a73e373bbb9d23c252fc647b https://dl.google.com/android/repository/android-ndk-r21-linux-x86_64.zip
test ! -e build
mkdir build
tar xf Android-build-source.tar.gz --strip-components=1 -C build
cd build
mkdir openjdk
tar xf ../OpenJDK17-source.tar.gz --strip-components=1 -C openjdk
tar xf ../freetype-source.tar.gz
tar xf ../cups-source.tar.gz
unzip -q ../ndk-r21.zip
# Upstream's wrappers/toolchain setup are pinned, but its reset/ignore-reject
# build step is deliberately not used. Our refreshed patch must apply in full.
git -C openjdk apply --check "$recipe_dir/android-17.0.20.patch"
git -C openjdk apply "$recipe_dir/android-17.0.20.patch"
export TARGET=aarch64-linux-android TARGET_JDK=aarch64 NDK_PREBUILT_ARCH=aarch64
export BUILD_FREETYPE_VERSION=2.10.0 JDK_DEBUG_LEVEL=release JVM_VARIANTS=server
chmod +x android-wrapped-clang android-wrapped-clang++
bash make_toolchain.sh
# The upstream helper predates nounset; source its environment explicitly.
source set_devkit_path.sh
bash build_libs.sh
export FREETYPE_DIR=$PWD/freetype-2.10.0/build_android-arm64 CUPS_DIR=$PWD/cups-2.2.4
export CFLAGS="-DLE_STANDALONE -O3 -DANDROID"
export LDFLAGS="$LDFLAGS -L$PWD/dummy_libs"
mkdir dummy_libs
ar cr dummy_libs/libpthread.a
ar cr dummy_libs/librt.a
ar cr dummy_libs/libthread_db.a
ln -s /usr/include/X11 "$ANDROID_INCLUDE/X11"
ln -s /usr/include/fontconfig "$ANDROID_INCLUDE/fontconfig"
ln -s "$CUPS_DIR/cups" "$ANDROID_INCLUDE/cups"
cd openjdk
bash configure --openjdk-target="$TARGET" \
  --with-extra-cflags="$CFLAGS" --with-extra-cxxflags="$CFLAGS" --with-extra-ldflags="$LDFLAGS" \
  --disable-precompiled-headers --disable-warnings-as-errors --enable-option-checking=fatal \
  --enable-headless-only=yes --with-jvm-variants=server \
  --with-jvm-features=-dtrace,-zero,-vm-structs,-epsilongc \
  --with-cups-include="$CUPS_DIR" --with-devkit="$TOOLCHAIN" \
  --with-native-debug-symbols=external --with-debug-level=release \
  --with-fontconfig-include="$ANDROID_INCLUDE" --x-includes="$ANDROID_INCLUDE/X11" \
  --x-libraries=/usr/lib --with-toolchain-type=gcc \
  --with-freetype-include="$FREETYPE_DIR/include/freetype2" --with-freetype-lib="$FREETYPE_DIR/lib" \
  --with-version-build=1 --with-version-opt=wurm-android
make JOBS=4 images
cd ..
# Use the host jlink to create the ARM64 JRE from the just-built target jmods.
bash remove_jdk_debug_info.sh
"$STRIP" --strip-debug jreout/lib/*.so jreout/lib/server/*.so
mkdir -p "$work_dir/dist"
tar -cJf "$work_dir/dist/android-jre17.0.20-arm64.tar.xz" -C jreout .
cp "$work_dir/"*-source.tar.gz "$work_dir/dist/"
cp "$recipe_dir/android-17.0.20.patch" "$recipe_dir/build.sh" "$recipe_dir/README.md" "$work_dir/dist/"
cd "$work_dir/dist"
sha256sum ./* > SHA256SUMS
