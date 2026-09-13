package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.InputStream
import java.util.Properties

/** Versioned preparation of the verified Thor server inputs, entirely inside import staging. */
class ServerRuntimePreparation(
    private val openAsset: (String) -> InputStream,
    private val gamePins: Map<String,String> = GAME_PINS,
    private val dependencyPins: Map<String,String> = ProbeInputs.BASELINE
) {
    fun prepare(root: File, progress: (String) -> Unit = {}) {
        gamePins.forEach { (name,hash) ->
            require(File(root,name).isFile && ProbeInputs.sha256(File(root,name))==hash) {
                "Unsupported $name. This recipe requires the verified Thor server files with their existing item SQLite fixes. Clean desktop server preparation is not yet qualified. Your existing installation is unchanged."
            }
        }
        dependencyPins.forEach { (name,hash) ->
            val output=File(root,"poc-lib/$name")
            if(output.exists()) {
                require(output.isFile && ProbeInputs.sha256(output)==hash) {
                    "$name differs from the verified SQLite dependency; existing files were not replaced."
                }
            } else {
                progress("Preparing Android SQLite: $name")
                check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
                openAsset(name).use { source -> output.outputStream().use { target ->
                    val buffer=ByteArray(64*1024); var bytes=0L
                    while(true) {
                        if(Thread.currentThread().isInterrupted) throw InterruptedException("Preparation cancelled")
                        val count=source.read(buffer); if(count<0) break
                        bytes+=count
                        require(bytes<=64L*1024*1024) { "Bundled SQLite dependency exceeds the size limit." }
                        check(root.usableSpace>64L*1024*1024+buffer.size) { "Not enough free internal storage for preparation." }
                        target.write(buffer,0,count)
                    }
                } }
                require(ProbeInputs.sha256(output)==hash) { "Bundled $name checksum failed; import was not activated." }
            }
        }
        val manifest=Properties().apply {
            setProperty("recipe",RECIPE)
            setProperty("scope","Verified prepared Thor server; clean desktop item patch not qualified")
            gamePins.forEach { (name,hash) -> setProperty("sha256.$name",hash) }
            dependencyPins.forEach { (name,hash) -> setProperty("sha256.poc-lib/$name",hash) }
            setProperty("sha256.wurm-arm64-poc.jar",ManagedRuntimeStore.POC_SHA256)
            setProperty("sqlite.version","3.53.2.1")
            setProperty("gameJars","preserved")
            setProperty("checks","input hashes; world startup preflight runs before launch")
        }
        File(root,"wurm-preparation.properties").outputStream().use { manifest.store(it,"Wurm Android preparation; no world or credential contents") }
        progress("Verified server files; Android dependencies prepared. World preflight runs before launch.")
    }

    companion object {
        const val RECIPE="thor-prepared-sqlite-1"
        val GAME_PINS=linkedMapOf(
            "server.jar" to "9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06",
            "common.jar" to "066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633"
        )
    }
}
