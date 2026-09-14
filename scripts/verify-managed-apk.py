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
    dex=b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
    for name in ("ServerDatabaseLayout", "ServerRuntimePreparation", "FrameBuffers", "FrameBuffers$Lease", "FrameTimingStats"):
        assert ("Lio/github/russianranger/wurmlauncher/"+name+";").encode() in dex, name
    for name, checksum in {
        "sqlite-jdbc-3.53.2.1.jar": "f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1",
        "sqlite-jdbc-3.53.2.1-natives-android.jar": "011d4edb8d06012ced78d6aa675ffc85bf339d3cd640845684b80873ec5a6e97",
    }.items():
        assert hashlib.sha256(apk.read("assets/" + name)).hexdigest() == checksum, name
    assert apk.read("assets/SQLITE_DEPENDENCY_NOTICES.txt")
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
    assert hashlib.sha256(apk.read("assets/mod-javassist.jar")).hexdigest() == "eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf"
    assert apk.read("assets/MOD_DEPENDENCY_NOTICES.txt") and apk.read("assets/JAVASSIST_LICENSE.html")
    assert "assets/modlauncher.jar" not in apk.namelist(), "Loader must be user-imported"
    runner = apk.read("lib/arm64-v8a/libwurmjvm_runner.so")
    assert runner[:6] == b"\x7fELF\x02\x01" and int.from_bytes(runner[18:20], "little") == 183
    assert b"HEAP_TAGGING_OFF" in runner and b"HEAP_TAGGING_ERROR" in runner
    assert b"HEAP_ASAN_READY" in runner and b"HEAP_ASAN_ERROR" in runner
    assert b"HEAP_ASAN_THREADS_READY" in runner
    assert b"[startup-crash] CAPTURE_READY preinit" in runner
    assert b"[native-heap] STARTUP_PROBE_PASS" in runner
    assert apk.read("assets/wurm-arm64-poc.jar") == base64.b64decode((ROOT / "poc/artifacts/wurm-arm64-poc.jar.base64").read_bytes())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/runtime-probe.jar"))) as helper:
        assert b"KEYCHAR" in helper.read("client/DesktopInput.class")
        assert b"TEXT" in helper.read("client/DesktopInput.class")
        assert int.from_bytes(helper.read("client/ClientHudVisibility.class")[6:8], "big") == 61
        for name in ("probe/RuntimeMeasurements.class", "probe/RuntimeProbe.class", "probe/NetworkProbe.class", "server/ManagedServerMain.class", "server/ServerModBootstrap.class", "server/ServerModLaunch.class", "server/ServerDiagnostics.class", "server/ServerLogHandler.class", "server/ServerSqlitePatch.class", "server/ServerItemSqlitePatch.class", "server/ServerItemSqlitePatch$Rule.class", "server/ServerLoginPatch.class", "server/LegacyBase64Encoder.class", "server/ServerPreflight.class", "server/ServerDatabasePreflight.class", "server/WorldProbe.class", "persistence/StorageAudit.class", "client/ClientBootstrap.class", "client/ClientModBootstrap.class", "client/ClientModLaunch.class", "client/ClientJvmDiagnostics.class", "client/ClientMemoryProbe.class", "client/ClientMemoryProbe$Kernel.class", "client/ClientMemoryProbe$Loader.class", "client/ClientFonts.class", "client/DirectClientLaunch.class", "client/ClientConnectionMonitor.class", "client/ClientConnectionMonitor$Sample.class", "client/ClassInventory.class", "client/ClientGraphicsPatch.class", "client/ClientBuffers.class", "client/ClientShaderResources.class", "client/ClientSettingsPatch.class", "client/ClientVisualOptions.class", "client/ClientKeybindings.class", "client/DesktopInput.class"):
            assert int.from_bytes(helper.read(name)[6:8], "big") == 61
        assert b"java/lang/invoke/MethodHandles" in helper.read("server/ServerModLaunch.class")
        assert b"SERVER_LOADER_STAGE" in helper.read("server/ServerModLaunch.class")
        assert int.from_bytes(helper.read("client/ClientConnectionMonitor$LogGate.class")[6:8], "big") == 61
        assert b"wurm.diagnostics.verbose" in helper.read("client/ClientConnectionMonitor.class")
        for name in ("ClientMethodOwner", "ClientGlCapabilities", "ClientWorldGc", "ClientSoundResources"):
            assert int.from_bytes(helper.read("client/"+name+".class")[6:8], "big") == 61
        assert b"res/wurm-android-missing-sound.wav" in helper.read("client/ClientSoundResources.class")
        assert b"wurm.client.skipPeriodicGc" in helper.read("client/ClientWorldGc.class")
        for name in ("ClientAllocationMeasurements", "ClientAllocationMeasurements$Window", "ClientAllocationMeasurements$Sample", "ClientAllocationMeasurements$Allocation"):
            assert int.from_bytes(helper.read("client/"+name+".class")[6:8], "big") == 61
        assert b"client/ClientAllocationMeasurements" in helper.read("client/ClientBootstrap.class")
        assert b"observedAllocatedBytes" in helper.read("client/ClientAllocationMeasurements.class")
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
    # Position, item and login overlays are generated from the owner's input at runtime, never bundled.
    for asset in (name for name in apk.namelist() if name.startswith("assets/") and name.endswith(".jar")):
        with zipfile.ZipFile(io.BytesIO(apk.read(asset))) as jar:
            assert "com/wurmonline/server/creatures/CreaturePos.class" not in jar.namelist(), asset
            assert "com/wurmonline/server/LoginHandler.class" not in jar.namelist(), asset
            assert not any(n.startswith("com/wurmonline/server/items/") for n in jar.namelist()), asset
    graphics = json.loads(apk.read("assets/client-graphics.json"))
    assert graphics["sources"] == json.loads((ROOT/"graphics-compat/native-sources.json").read_text())
    assert set(graphics["nativeSha256"]) == {"libwurm_lwjgl3.so", "libwurm_lwjgl3_opengl.so", "libgl4es.so", "libwurm_graphics.so", "libwurm_openal.so", "libclang_rt.asan-aarch64-android.so", "libc++_shared.so"}
    assert graphics["asanPatches"] == ["6bbf0c30ca4449e325beb2d28db00d258d3a1a10", "1c792d24e0a228ad49cc004a1c26bbd7cd87f030"]
    assert graphics["nativeHeapDiagnostic"] == "ASan / isolated startup and client graphics stages / LLVM 17.0.2 native ELF TLS with prctl PAC and trampoline BTI fixes"
    assert graphics["asanRuntimeFlags"] == "-target aarch64-linux-android33 -mbranch-protection=standard -fno-emulated-tls -g"
    assert b"ASAN_READY" in apk.read("lib/arm64-v8a/libwurm_graphics.so")
    assert graphics["audioBackend"] == "OpenAL Soft 1.23.1 / Android OpenSL ES"
    assert "vao-buffer-offset-addresses" in graphics["gl4esPatches"]
    assert "program-cache-cleanup" in graphics["gl4esPatches"]
    assert "error-origin-breadcrumbs" in graphics["gl4esPatches"]
    assert "supported-depth24" in graphics["gl4esPatches"]
    assert "mipmap-base-image-and-unit" in graphics["gl4esPatches"]
    assert "synchronous-mipmap-error-context" in graphics["gl4esPatches"]
    assert b"wurm_mipmap_current" in apk.read("lib/arm64-v8a/libgl4es.so")
    assert b"mipmapSite=" in apk.read("lib/arm64-v8a/libwurm_graphics.so")
    assert b"recent-errorGL-sites-not-proof" in apk.read("lib/arm64-v8a/libgl4es.so")
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
        assert b"canQueueInput" in adapter.read("org/lwjgl/input/GLFWInputImplementation.class")
        assert b"remainingEvents" in adapter.read("org/lwjgl/input/EventQueue.class")
        assert "META-INF/LICENSE.lwjgl.txt" in adapter.namelist()
        assert "org/lwjgl/opengl/ARBProgram.class" in adapter.namelist()
        assert b"OPENAL_CONTEXT_READY" in adapter.read("org/lwjgl/openal/AL.class")
        assert int.from_bytes(adapter.read("wurm/graphics/GraphicsTrace.class")[6:8], "big") == 52
        assert b"wurm.diagnostics.verbose" in adapter.read("wurm/graphics/GraphicsTrace.class")
        assert b"wurm/graphics/GraphicsTrace" in adapter.read("org/lwjgl/opengl/GL20.class")
        assert not any(n.startswith(("com/wurmonline/", "SteamJni/")) for n in adapter.namelist())
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/wurm-window.jar"))) as window:
        assert b"canApply" in window.read("wurm/graphics/WindowBackend.class")
        assert b"KEYCHAR" in window.read("wurm/graphics/WindowInput.class")
        assert b"TEXT" in window.read("wurm/graphics/WindowInput.class")
        assert int.from_bytes(window.read("wurm/graphics/WurmVisibility.class")[6:8], "big") == 61
        assert b"WURM_VISIBILITY_POLICY" in window.read("wurm/graphics/WurmVisibility.class")
        assert b"wurm/graphics/WurmVisibility" in window.read("wurm/graphics/CapabilityChecks.class")
        assert int.from_bytes(window.read("wurm/graphics/FramePacer.class")[6:8], "big") == 61
        assert b"wurm.diagnostics.verbose" in window.read("wurm/graphics/WindowBackend.class")
        assert "META-INF/LICENSE.pojav.txt" in window.namelist()
        for name in ("org/lwjgl/glfw/GLFW.class", "wurm/graphics/WindowBackend.class", "wurm/graphics/WindowInput.class", "wurm/graphics/WindowProbe.class", "wurm/graphics/OffscreenSupport.class", "wurm/graphics/ShaderQueries.class", "wurm/graphics/CapabilityChecks.class"):
            assert int.from_bytes(window.read(name)[6:8], "big") == 61
        assert not any(n.startswith(("com/wurmonline/", "SteamJni/")) for n in window.namelist())
print("Verified maintained runtime, notices, native runner, Java 17 helper classes and exact POC artifact.")
