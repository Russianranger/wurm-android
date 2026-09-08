# JVM probe runtime provenance

This document describes the open-source runtime used only by the `jvmProbe`
build type. It contains no Wurm code. Normal `debug` builds do not bundle it.

- Binary provider: [FCL-Team/FoldCraftLauncher](https://github.com/FCL-Team/FoldCraftLauncher/tree/c9f4590d4a8de4b6a58a145fcc7c8b1f0f0be354/FCL/src/main/jreAssets/app_runtime/java/jre17).
- Pinned provider commit: `c9f4590d4a8de4b6a58a145fcc7c8b1f0f0be354`.
- `bin-arm64.tar.xz`: SHA-256 `67f4510b0fa9c64ed851f4af924a5a8898538ada453f66d64542b72a0f04f92d`.
- `universal.tar.xz`: SHA-256 `d14ffda3b15b93a7000de26be864dd81b15f7eae8dbebe5ee71074973492a73b`.
- The binary `release` file identifies Java **17.0.10**, `aarch64`, source
  `ca760c86642a+`, and build `17.0.10-internal+0-adhoc.runner.openjdk`.
- Base source: [OpenJDK jdk17u](https://github.com/openjdk/jdk17u/tree/ca760c86642aa2e0d9b571aaabac054c0239fbdc),
  full commit `ca760c86642aa2e0d9b571aaabac054c0239fbdc` (17.0.10 release).
- Android build/patch source: [FCL-Team/Android-OpenJDK-Build](https://github.com/FCL-Team/Android-OpenJDK-Build/tree/ce21ce33b4f495e678c2cfdecb6abe893bf561ee),
  commit `ce21ce33b4f495e678c2cfdecb6abe893bf561ee`. Its `clone_jdk.sh` selects
  `jdk-17.0.10-ga`; `build_jdk.sh` applies `patches/jdk17u_android.diff`.
- Patch provenance is inferred from the provider's June 26, 2024 runtime update
  and the June 21, 2024 Android build commit (IPv6 fix). The binary does not embed
  a full Android patch commit. This project has not independently rebuilt that
  binary and does not claim bit-for-bit source-build reproduction.

The JRE's `legal/` files (GPL v2, Classpath/assembly exceptions and third-party
notices) are preserved in `assets/jre17-data.zip`. Archive-internal legal symlinks
are resolved to ordinary files during packaging. JRE native libraries are
packaged without modification or stripping; installation verifies their hashes.
The JRE's desktop command-line executables are not packaged. The new handwritten
`runtime-probe/native/jvm_runner.c` calls the JLI entry point in a fresh native
process, without copying Fold Craft Launcher's launcher/graphics implementation.

Since 0.3.2, the runner also builds the handwritten `jvm_layout.c` adapter. It
exports `dl_iterate_phdr` and `dladdr`, forwards both to the system linker, and
changes only the selected `libjvm.so`'s reported filename to its verified existing
`JAVA_HOME/lib/server/libjvm.so` alias. The alias resolves to the same APK-installed
file; mappings, symbols and native bytes are unchanged. This accommodates the
pinned Android HotSpot's early image-directory derivation. It applies only inside
the diagnostic child, before Java initializes. The adapter source is included in
this repository and the release tag's source archive.

The unchanged `lib/modules` image is 81,720,691 bytes, SHA-256
`2cd3abc75196790da2ad94fffbf93c43b70415d8172a824e96619b401c408139`.
Build and device installation both verify this core-class image.

Companion source archives are published with the diagnostic APK:
`OpenJDK17-base-source.tar.gz` and `Android-OpenJDK-build-source.tar.gz`.
The latter includes the Android patches, build scripts, toolchain settings and
the upstream dependency download instructions (FreeType 2.10.0/CUPS headers).
Upstream build/source references are also retained here for review and rebuilding.

Do not silently replace these archives with a newer or different architecture
under the existing pins. A runtime change requires new checksums, notice/source
review and a new Thor diagnostic run. The final server runtime should be built
from a maintained source version with explicit patch provenance; this old
candidate is confined to the current local diagnostic.

References for the process approach:
[JNI Invocation API](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html),
[Android executable restrictions](https://developer.android.com/about/versions/10/behavior-changes-10).
