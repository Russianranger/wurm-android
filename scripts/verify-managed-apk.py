#!/usr/bin/env python3
"""Verify packaged runtime identity and handwritten JVM assets before release."""
import base64
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(sys.argv[1]) as apk:
    info = json.loads(apk.read("assets/jvm-runtime.json"))
    assert info["javaVersion"] == "17.0.20"
    data = apk.read("assets/jre17-data.zip")
    assert hashlib.sha256(data).hexdigest() == info["dataSha256"]
    with zipfile.ZipFile(io.BytesIO(data)) as jre:
        assert hashlib.sha256(jre.read("lib/modules")).hexdigest() == info["modulesSha256"]
        assert any(name.startswith("legal/") for name in jre.namelist())
        assert not any(jre.read(name).startswith(b"\x7fELF") for name in jre.namelist() if not name.endswith("/"))
    for name, digest in info["nativeSha256"].items():
        assert hashlib.sha256(apk.read("lib/arm64-v8a/" + name)).hexdigest() == digest, name
    runner = apk.read("lib/arm64-v8a/libwurmjvm_runner.so")
    assert runner[:6] == b"\x7fELF\x02\x01" and int.from_bytes(runner[18:20], "little") == 183
    assert b"HEAP_TAGGING_OFF" in runner and b"HEAP_TAGGING_ERROR" in runner
    assert apk.read("assets/wurm-arm64-poc.jar") == base64.b64decode((ROOT / "poc/artifacts/wurm-arm64-poc.jar.base64").read_bytes())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/runtime-probe.jar"))) as helper:
        for name in ("probe/RuntimeProbe.class", "probe/NetworkProbe.class", "server/ManagedServerMain.class", "server/WorldProbe.class", "persistence/StorageAudit.class", "client/ClientBootstrap.class", "client/DirectClientLaunch.class", "client/ClassInventory.class", "client/DesktopInput.class"):
            assert int.from_bytes(helper.read(name)[6:8], "big") == 61
        assert not any(n.startswith(("SteamJni/Steam_api", "com/wurmonline/client/")) for n in helper.namelist())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/client-compat.jar"))) as compat:
        classes = {n for n in compat.namelist() if n.endswith(".class")}
        assert classes == {"SteamJni/Steam_api.class", "wurm/android/compat/LocalSession.class", "wurm/android/compat/ClientHooks.class",
                           "com/wurmonline/client/launcherfx/WurmMain.class", "com/wurmonline/client/launcherfx/WurmMain$1.class"}, classes
        assert all(int.from_bytes(compat.read(n)[6:8], "big") == 61 for n in classes)
    assert not any(name in apk.namelist() for name in ("assets/server.jar", "assets/common.jar", "assets/client.jar"))
print("Verified maintained runtime, notices, native runner, Java 17 helper classes and exact POC artifact.")
