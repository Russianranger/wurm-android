package io.github.russianranger.wurmlauncher

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Graphics assets are used by the client graphics stages only; no server classpath changes. */
object GraphicsRuntime {
    fun prepare(context: Context, session: File, log: (String) -> Unit): List<File> {
        val info = JSONObject(context.assets.open("client-graphics.json").bufferedReader().use { it.readText() })
        val native = File(context.applicationInfo.nativeLibraryDir)
        val pins = info.getJSONObject("nativeSha256")
        val names = setOf("libwurm_lwjgl3.so", "libwurm_lwjgl3_opengl.so", "libgl4es.so", "libwurm_graphics.so")
        check(pins.keys().asSequence().toSet() == names) { "Unexpected graphics native manifest" }
        names.forEach { name ->
            check(ProbeInputs.sha256(File(native, name)) == pins.getString(name)) { "Graphics native checksum mismatch: $name" }
            log("[graphics] NATIVE_VERIFIED $name sha256=${pins.getString(name)}")
        }
        val assets = info.getJSONObject("assetsSha256")
        return listOf("graphics-probe.jar", "pojav-wurm-api.jar", "wurm-window.jar").map { name ->
            val path = File(session, name)
            context.assets.open(name).use { input -> path.outputStream().use { input.copyTo(it) } }
            check(ProbeInputs.sha256(path) == assets.getString(name)) { "Graphics Java checksum mismatch: $name" }
            log("[graphics] ASSET_VERIFIED $name sha256=${assets.getString(name)}")
            path
        }
    }
}
