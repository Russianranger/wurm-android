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
    assert b"HEAP_ASAN_READY" in runner and b"HEAP_ASAN_ERROR" in runner
    assert apk.read("assets/wurm-arm64-poc.jar") == base64.b64decode((ROOT / "poc/artifacts/wurm-arm64-poc.jar.base64").read_bytes())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/runtime-probe.jar"))) as helper:
        assert int.from_bytes(helper.read("client/ClientHudVisibility.class")[6:8], "big") == 61
        for name in ("probe/RuntimeProbe.class", "probe/NetworkProbe.class", "server/ManagedServerMain.class", "server/ServerDiagnostics.class", "server/ServerLogHandler.class", "server/ServerSqlitePatch.class", "server/ServerLoginPatch.class", "server/LegacyBase64Encoder.class", "server/ServerPreflight.class", "server/WorldProbe.class", "persistence/StorageAudit.class", "client/ClientBootstrap.class", "client/ClientJvmDiagnostics.class", "client/ClientMemoryProbe.class", "client/ClientMemoryProbe$Kernel.class", "client/ClientMemoryProbe$Loader.class", "client/ClientFonts.class", "client/DirectClientLaunch.class", "client/ClientConnectionMonitor.class", "client/ClientConnectionMonitor$Sample.class", "client/ClassInventory.class", "client/ClientGraphicsPatch.class", "client/ClientBuffers.class", "client/ClientShaderResources.class", "client/ClientSettingsPatch.class", "client/ClientVisualOptions.class", "client/DesktopInput.class"):
            assert int.from_bytes(helper.read(name)[6:8], "big") == 61
        assert not any(n.startswith(("SteamJni/Steam_api", "com/wurmonline/client/")) for n in helper.namelist())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/client-compat.jar"))) as compat:
        classes = {n for n in compat.namelist() if n.endswith(".class")}
        assert classes == {"SteamJni/Steam_api.class", "wurm/android/compat/LocalSession.class", "wurm/android/compat/ClientHooks.class",
                           "com/wurmonline/client/launcherfx/WurmMain.class", "com/wurmonline/client/launcherfx/WurmMain$1.class",
                           "com/wurmonline/client/launcherfx/WurmSettingsFX.class", "com/wurmonline/client/launcherfx/WurmStage.class",
                           "com/wurmonline/client/ErrorReporterPanel.class", "wurm/android/compat/KeybindStore.class",
                           "wurm/android/compat/SettingsDispatch.class"}, classes
        assert all(int.from_bytes(compat.read(n)[6:8], "big") == 61 for n in classes)
    assert not any(name in apk.namelist() for name in ("assets/server.jar", "assets/common.jar", "assets/client.jar"))
    # Position and login overlays are generated from the owner's input at runtime, never bundled.
    for asset in (name for name in apk.namelist() if name.startswith("assets/") and name.endswith(".jar")):
        with zipfile.ZipFile(io.BytesIO(apk.read(asset))) as jar:
            assert "com/wurmonline/server/creatures/CreaturePos.class" not in jar.namelist(), asset
            assert "com/wurmonline/server/LoginHandler.class" not in jar.namelist(), asset
    graphics = json.loads(apk.read("assets/client-graphics.json"))
    assert graphics["sources"] == json.loads((ROOT/"graphics-compat/native-sources.json").read_text())
    assert set(graphics["nativeSha256"]) == {"libwurm_lwjgl3.so", "libwurm_lwjgl3_opengl.so", "libgl4es.so", "libwurm_graphics.so", "libwurm_openal.so", "libclang_rt.asan-aarch64-android.so", "libc++_shared.so"}
    assert graphics["nativeHeapDiagnostic"] == "ASan / client graphics stages only / NDK 26.1.10909125"
    assert b"ASAN_READY" in apk.read("lib/arm64-v8a/libwurm_graphics.so")
    assert graphics["audioBackend"] == "OpenAL Soft 1.23.1 / Android OpenSL ES"
    assert "vao-buffer-offset-addresses" in graphics["gl4esPatches"]
    assert "legacy-openal-context-lifecycle" in graphics["lwjglPatches"]
    for name, digest in graphics["nativeSha256"].items():
        data = apk.read("lib/arm64-v8a/"+name)
        assert hashlib.sha256(data).hexdigest() == digest
        if name.startswith(("libwurm_", "libgl4es")): assert b"__asan_init" in data, name
        assert data[:6] == b"\x7fELF\x02\x01" and int.from_bytes(data[18:20], "little") == 183
    for name, digest in graphics["assetsSha256"].items():
        assert hashlib.sha256(apk.read("assets/"+name)).hexdigest() == digest
    assert "lib/arm64-v8a/liblwjgl.so" not in apk.namelist(), "Graphics test must not replace imported LWJGL2 native lookup"
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/graphics-probe.jar"))) as probe:
        assert set(probe.namelist()) == {"wurm/graphics/GraphicsProbe.class", "wurm/graphics/GlChecks.class", "wurm/graphics/FrameFile.class", "wurm/graphics/NativeEgl.class", "wurm/graphics/LibraryNames.class"}
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/pojav-wurm-api.jar"))) as adapter:
        assert "META-INF/LICENSE.lwjgl.txt" in adapter.namelist()
        assert "org/lwjgl/opengl/ARBProgram.class" in adapter.namelist()
        assert b"OPENAL_CONTEXT_READY" in adapter.read("org/lwjgl/openal/AL.class")
        assert int.from_bytes(adapter.read("wurm/graphics/GraphicsTrace.class")[6:8], "big") == 52
        assert b"wurm/graphics/GraphicsTrace" in adapter.read("org/lwjgl/opengl/GL20.class")
        assert not any(n.startswith(("com/wurmonline/", "SteamJni/")) for n in adapter.namelist())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/wurm-window.jar"))) as window:
        assert int.from_bytes(window.read("wurm/graphics/WurmVisibility.class")[6:8], "big") == 61
        assert b"WURM_VISIBILITY_POLICY" in window.read("wurm/graphics/WurmVisibility.class")
        assert b"wurm/graphics/WurmVisibility" in window.read("wurm/graphics/CapabilityChecks.class")
        assert int.from_bytes(window.read("wurm/graphics/FramePacer.class")[6:8], "big") == 61
        assert "META-INF/LICENSE.pojav.txt" in window.namelist()
        for name in ("org/lwjgl/glfw/GLFW.class", "wurm/graphics/WindowBackend.class", "wurm/graphics/WindowInput.class", "wurm/graphics/WindowProbe.class", "wurm/graphics/OffscreenSupport.class", "wurm/graphics/ShaderQueries.class", "wurm/graphics/CapabilityChecks.class"):
            assert int.from_bytes(window.read(name)[6:8], "big") == 61
        assert not any(n.startswith(("com/wurmonline/", "SteamJni/")) for n in window.namelist())
print("Verified maintained runtime, notices, native runner, Java 17 helper classes and exact POC artifact.")
