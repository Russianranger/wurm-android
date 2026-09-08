package io.github.russianranger.wurmlauncher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipInputStream

/** JRE data is private; executable runner and native libraries stay APK-installed. */
object ProbeRuntime {
    fun install(context: Context, log: (String) -> Unit): File {
        val info = JSONObject(context.assets.open("jvm-runtime.json").bufferedReader().use { it.readText() })
        val id = info.getString("id")
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        log("[runtime] Candidate Android Java ${info.getString("javaVersion")}; Termux baseline Java 17.0.20")
        log("[runtime] Source: FCL-Team/FoldCraftLauncher ${info.getString("sourceCommit")}")
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val pins = info.getJSONObject("nativeSha256")
        pins.keys().forEach { name ->
            require(ProbeInputs.sha256(File(nativeDir, name)) == pins.getString(name)) { "Installed native checksum mismatch: $name" }
        }
        val home = File(context.filesDir, id)
        if (!File(home, ".complete").isFile) {
            val stage = File(context.filesDir, "$id.pending")
            stage.deleteRecursively()
            check(stage.mkdirs())
            val assetZip = File(stage, "runtime-data.zip")
            try {
                context.assets.open("jre17-data.zip").use { input -> assetZip.outputStream().use { input.copyTo(it) } }
                require(ProbeInputs.sha256(assetZip) == info.getString("dataSha256")) { "JRE data checksum mismatch" }
                ZipInputStream(assetZip.inputStream().buffered()).use { zip ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Cancelled")
                        val entry = zip.nextEntry ?: break
                        val target = File(stage, entry.name)
                        require(!entry.name.startsWith('/') && target.canonicalPath.startsWith(stage.canonicalPath + File.separator))
                        if (entry.isDirectory) check(target.isDirectory || target.mkdirs()) else {
                            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
                assetZip.delete()
                File(stage, ".complete").writeText(id)
                if (home.exists()) home.deleteRecursively()
                Files.move(stage.toPath(), home.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { stage.deleteRecursively() }
        }
        val links = Properties().apply { File(home, "native-links.properties").inputStream().use { load(it) } }
        // Refresh links after an APK update changes nativeLibraryDir.
        links.stringPropertyNames().forEach { path ->
            val link = File(home, path)
            require(!path.startsWith('/') && path.split('/').none { it == ".." })
            val name = links.getProperty(path)
            require(name.matches(Regex("lib[a-zA-Z0-9_]+\\.so")))
            check(link.parentFile!!.isDirectory || link.parentFile!!.mkdirs())
            Files.deleteIfExists(link.toPath())
            Files.createSymbolicLink(link.toPath(), File(nativeDir, name).toPath())
        }
        log("[runtime] JRE data and APK native libraries verified")
        return home
    }
}
