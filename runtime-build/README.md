# Android OpenJDK 17.0.20 build

This source build advances the diagnostic's 17.0.10 runtime to the July 2026
OpenJDK 17.0.20 update before using the managed server preview. A successful
cross-build is not a physical-device qualification: the app must first pass its
Java/SQLite preflight on Android, then test Wurm startup, stopping and recovery.

Inputs are SHA-256 pinned in `build.sh`:

- OpenJDK `jdk17u` commit `8cbbca61432426a3441aa08838d930ef954ea1ba` (17.0.20 GA).
- FCL-Team Android-OpenJDK-Build commit `1474641f7cd2ec2f736d0ad51c93b7cacc041848`.
- NDK r21, required by that Android port's GCC-compatible Clang wrappers.
- FreeType 2.10.0 (headless build dependency) and CUPS 2.2.4 (headers).

`android-17.0.20.patch` derives from the pinned FCL Android patch, itself carrying
OpenJDK and Android-port copyright/license notices. Preserve these notices and
the JRE's `legal/` directory. The companion source archives, this patch and the
complete build recipe are distributed alongside the runtime binary. OpenJDK is
GPL v2 with the Classpath Exception where specified; consult the supplied source
and module legal files for the individual components' terms.

Four upstream patch contexts changed in 17.0.20: the Linux C flags now retain
`_FILE_OFFSET_BITS=64`; JDWP retains its new Microsoft warning entry; HotSpot's
temporary-directory patch now honors the app's absolute `TMPDIR`; and NIO
retains the new statx declarations alongside the Android API-21 compatibility
functions. No patch rejects are ignored. The pinned source includes upstream
17.0.20 fixes; this is not a certified Java distribution or a claim that all
Android-port components are current. FreeType/font rendering is not exercised
by the headless server milestone.

To reproduce on Ubuntu 22.04 with a host JDK 17:

```bash
sudo apt-get update
sudo apt-get install -y autoconf python3 python-is-python3 unzip zip \
  systemtap-sdt-dev libxtst-dev libasound2-dev libelf-dev libfontconfig1-dev \
  libx11-dev libxext-dev libxrandr-dev libxrender-dev libxt-dev
bash runtime-build/build.sh /absolute/path/to/new-build-directory
```

The output is `dist/android-jre17.0.20-arm64.tar.xz`, source archives, recipe,
patch and `SHA256SUMS`. This is a cross-build for an APK; it does not depend on
Termux paths, root, or Wurm files. APK packaging installs ELF libraries in the
package's executable native-library directory and data in private storage.
