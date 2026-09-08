#!/usr/bin/env python3
"""Package the separately source-built Android JRE; never fetch game files."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    pin = json.loads((ROOT / "runtime-build/runtime.json").read_text())
    cache = ROOT / "app/build/managedDownloads"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / "android-jre17.0.20-arm64.tar.xz"
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != pin["sha256"]:
        partial = archive.with_suffix(".partial")
        subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "600", pin["url"], "-o", str(partial)], check=True)
        if hashlib.sha256(partial.read_bytes()).hexdigest() != pin["sha256"]:
            raise ValueError("Maintained runtime archive checksum mismatch")
        partial.replace(archive)
    spec = importlib.util.spec_from_file_location("probe_packager", ROOT / "scripts/prepare-jvm-probe.py")
    probe = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(probe)
    output = ROOT / "app/build/generated/managedRuntime"
    if output.exists():
        shutil.rmtree(output)
    assets = output / "assets"
    native = output / "jniLibs/arm64-v8a"
    assets.mkdir(parents=True)
    native.mkdir(parents=True)
    links, native_hashes = {}, {}
    modules_hash = None
    release = None
    legal_count = 0
    with tarfile.open(archive) as source, zipfile.ZipFile(assets / "jre17-data.zip", "w", zipfile.ZIP_DEFLATED) as data:
        for entry in source:
            path = probe.safe_path(entry.name)
            if entry.isdir() or path == "." or path.startswith("bin/"):
                continue
            if not (entry.isfile() or entry.issym() or entry.islnk()):
                raise ValueError(f"Unsupported JRE member: {path}")
            contents = source.extractfile(entry).read()
            helpers = {"lib/jspawnhelper": "libwurm_jspawnhelper.so", "lib/jexec": "libwurm_jexec.so"}
            if path.endswith(".so") or path in helpers:
                if contents[:6] != b"\x7fELF\x02\x01" or int.from_bytes(contents[18:20], "little") != 183:
                    raise ValueError(f"Not ARM64 ELF: {path}")
                name = helpers.get(path, PurePosixPath(path).name)
                if not re.fullmatch(r"lib[a-zA-Z0-9_]+\.so", name):
                    raise ValueError(f"Unsupported native name: {name}")
                digest = hashlib.sha256(contents).hexdigest()
                if name in native_hashes and native_hashes[name] != digest:
                    raise ValueError(f"Conflicting native library: {name}")
                (native / name).write_bytes(contents)
                native_hashes[name] = digest
                links[path] = name
            else:
                if contents.startswith(b"\x7fELF"):
                    raise ValueError(f"Unexpected writable executable: {path}")
                if path == "lib/modules": modules_hash = hashlib.sha256(contents).hexdigest()
                if path == "release": release = contents
                if path.startswith("legal/"): legal_count += 1
                info = zipfile.ZipInfo(path, (2026, 7, 21, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                data.writestr(info, contents)
        data.writestr("native-links.properties", "\n".join(f"{key}={value}" for key, value in sorted(links.items())))
    if release is None or b'JAVA_VERSION="17.0.20"' not in release or b'OS_ARCH="aarch64"' not in release:
        raise ValueError("Expected source-built Android OpenJDK 17.0.20 ARM64")
    if modules_hash is None or legal_count < 10 or not {"libjli.so", "libjvm.so", "libjava.so"}.issubset(native_hashes):
        raise ValueError("Incomplete runtime modules, notices or native libraries")
    probe.build_runner(os.environ.get("ANDROID_NDK_HOME", ""), native)
    # Check the native dependency closure against libraries available to ordinary
    # Android apps. A missing extra runtime library must fail the APK build.
    readelf = Path(os.environ["ANDROID_NDK_HOME"]) / "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
    system = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so"}
    for library in native.glob("*.so"):
        dynamic = subprocess.check_output([str(readelf), "-d", str(library)], text=True)
        for dependency in re.findall(r"\(NEEDED\).*?\[(.*?)\]", dynamic):
            if dependency not in system and not (native / dependency).is_file():
                raise ValueError(f"Missing Android dependency {dependency} for {library.name}")
    manifest = dict(id="wurm-jre17-0-20-build1", javaVersion="17.0.20", provider="OpenJDK + FCL Android port (source build)",
                    sourceCommit="8cbbca61432426a3441aa08838d930ef954ea1ba", modulesSha256=modules_hash,
                    archives={archive.name: pin}, nativeSha256=native_hashes,
                    dataSha256=hashlib.sha256((assets / "jre17-data.zip").read_bytes()).hexdigest())
    (assets / "jvm-runtime.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Packaged source-built Android Java 17.0.20, full legal notices and native process runner.")


if __name__ == "__main__":
    main()
