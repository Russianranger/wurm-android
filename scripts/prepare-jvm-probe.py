#!/usr/bin/env python3
"""Build-time only: verified Android JRE assets and a tiny native process runner.

No Wurm files or SQLite binaries are downloaded. The diagnostic gets the two
SQLite JARs from the owner's runtime ZIP on the device.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FCL_COMMIT = "c9f4590d4a8de4b6a58a145fcc7c8b1f0f0be354"
BASE = f"https://raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/{FCL_COMMIT}/FCL/src/main/jreAssets/app_runtime/java/jre17/"
ARCHIVES = {
    "bin-arm64.tar.xz": "67f4510b0fa9c64ed851f4af924a5a8898538ada453f66d64542b72a0f04f92d",
    "universal.tar.xz": "d14ffda3b15b93a7000de26be864dd81b15f7eae8dbebe5ee71074973492a73b",
}


def download(cache, name, expected):
    target = cache / name
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        partial = target.with_suffix(".partial")
        subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "300", BASE + name, "-o", str(partial)], check=True)
        if hashlib.sha256(partial.read_bytes()).hexdigest() != expected:
            raise ValueError(f"Runtime checksum mismatch: {name}")
        partial.replace(target)
    return target


def safe_path(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or "\\" in name:
        raise ValueError(f"Unsafe packaged runtime path: {name}")
    return str(path)


def prepare(ndk, cache):
    output = ROOT / "app/build/generated/jvmProbe"
    assets = output / "assets"
    native = output / "jniLibs/arm64-v8a"
    cache.mkdir(parents=True, exist_ok=True)
    archives = {name: download(cache, name, digest) for name, digest in ARCHIVES.items()}
    if output.exists():
        shutil.rmtree(output)  # Only this script's reproducible generated output.
    assets.mkdir(parents=True)
    native.mkdir(parents=True)
    links = {}
    with zipfile.ZipFile(assets / "jre17-data.zip", "w", zipfile.ZIP_DEFLATED) as data:
        with tarfile.open(archives["universal.tar.xz"]) as source:
            for entry in source:
                if entry.isdir():
                    continue
                path = safe_path(entry.name)
                # Resolve archive-internal legal symlinks into ordinary ZIP files.
                if not (entry.isfile() or entry.issym()):
                    raise ValueError(f"Unsupported runtime member: {path}")
                if path.startswith("bin/") or path.endswith(".so"):
                    raise ValueError(f"Unexpected executable in universal archive: {path}")
                data.writestr(path, source.extractfile(entry).read())
        with tarfile.open(archives["bin-arm64.tar.xz"]) as source:
            for entry in source:
                path = safe_path(entry.name)
                if path.endswith(".so"):
                    if not entry.isfile():
                        raise ValueError(f"Expected native regular file: {path}")
                    contents = source.extractfile(entry).read()
                    # ELF64, little-endian, AArch64. Reject a desktop/iOS runtime.
                    if contents[:6] != b"\x7fELF\x02\x01" or int.from_bytes(contents[18:20], "little") != 183:
                        raise ValueError(f"Not an ARM64 ELF: {path}")
                    name = PurePosixPath(path).name
                    target = native / name
                    if target.exists() and target.read_bytes() != contents:
                        raise ValueError(f"Conflicting native basename: {name}")
                    target.write_bytes(contents)
                    links[path] = name
                elif path == "release":
                    release = source.extractfile(entry).read()
                    if b'JAVA_VERSION="17.0.10"' not in release or b'OS_ARCH="aarch64"' not in release:
                        raise ValueError("Unexpected Android runtime version/architecture")
                    data.writestr("release", release)
        data.writestr("native-links.properties", "\n".join(f"{path}={name}" for path, name in sorted(links.items())))
    manifest = {"id": "fcl-jre17-" + FCL_COMMIT[:12], "javaVersion": "17.0.10", "sourceCommit": FCL_COMMIT,
                "archives": {name: {"url": BASE + name, "sha256": digest} for name, digest in ARCHIVES.items()},
                "dataSha256": hashlib.sha256((assets / "jre17-data.zip").read_bytes()).hexdigest(),
                "nativeSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(native.glob("*.so"))}}
    (assets / "jvm-runtime.json").write_text(json.dumps(manifest, indent=2) + "\n")
    # Leave upstream binaries byte-identical, including notices in jre17-data.zip.
    compiler = Path(ndk) / "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang"
    if not compiler.is_file():
        raise ValueError("JVM test build requires Linux x86_64 NDK 26.1.10909125; set ANDROID_NDK_HOME")
    subprocess.run([str(compiler), "-O2", "-Wall", "-Wextra", "-Werror", "-fPIE", "-pie",
                    "-Wl,-z,max-page-size=16384", str(ROOT / "runtime-probe/native/jvm_runner.c"),
                    "-ldl", "-o", str(native / "libwurmjvm_runner.so")], check=True)
    print("Prepared pinned Android OpenJDK 17.0.10 and native runner; no game files included.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ndk", default=os.environ.get("ANDROID_NDK_HOME", ""))
    parser.add_argument("--cache", type=Path, default=ROOT / "app/build/jvmDownloads")
    args = parser.parse_args()
    prepare(args.ndk, args.cache)
