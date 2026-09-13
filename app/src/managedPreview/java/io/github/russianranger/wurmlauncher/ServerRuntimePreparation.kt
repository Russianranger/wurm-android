package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Versioned preparation of the verified Thor server inputs, entirely inside import staging. */
class ServerRuntimePreparation(
    private val openAsset: (String) -> InputStream,
    private val gamePins: Map<String,String> = GAME_PINS,
    private val dependencyPins: Map<String,String> = ProbeInputs.BASELINE,
    private val stockPins: Map<String,String> = STOCK_GAME_PINS
) {
    fun prepare(root: File, progress: (String) -> Unit = {}) {
        val selected = selectPins(root,gamePins,stockPins)
        val stock = selected == stockPins
        // The inspected Windows distribution keeps worlds/resources under dist/.
        // Move only the unpublished copy and reject every collision before any move.
        val dist=File(root,"dist")
        if(stock && dist.exists()) {
            require(dist.isDirectory) { "Expected a dist/ directory." }
            val children=dist.listFiles() ?: error("Cannot read dist/.")
            require(children.none { File(root,it.name).exists() }) {
                "Server ZIP contains conflicting root and dist/ files; import was not activated."
            }
            progress("Preparing desktop world and resource layout")
            children.forEach {
                if(Thread.currentThread().isInterrupted) throw InterruptedException("Preparation cancelled")
                Files.move(it.toPath(),File(root,it.name).toPath(),StandardCopyOption.ATOMIC_MOVE)
            }
            check(dist.delete()) { "Cannot finish desktop layout preparation." }
        }
        if(stock) ServerDatabaseLayout.prepare(root,progress)
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
            setProperty("recipe",if(stock) STOCK_RECIPE else RECIPE)
            setProperty("scope",if(stock) "Verified stock server; personal-server item SQL overlay at startup" else "Verified prepared Thor server")
            selected.forEach { (name,hash) -> setProperty("sha256.$name",hash) }
            dependencyPins.forEach { (name,hash) -> setProperty("sha256.poc-lib/$name",hash) }
            setProperty("sha256.wurm-arm64-poc.jar",ManagedRuntimeStore.POC_SHA256)
            setProperty("sqlite.version","3.53.2.1")
            setProperty("gameJars","preserved")
            if(stock) setProperty("worldConfig","Missing desktop localhost path redirected to each world's SQLite directory; existing database directories preserved")
            setProperty("checks","input hashes; world startup preflight runs before launch")
        }
        File(root,"wurm-preparation.properties").outputStream().use { manifest.store(it,"Wurm Android preparation; no world or credential contents") }
        progress("Verified server files; Android dependencies prepared. World preflight runs before launch.")
    }

    companion object {
        const val RECIPE="thor-prepared-sqlite-1"
        const val STOCK_RECIPE="thor-stock-sqlite-2"
        val GAME_PINS=linkedMapOf(
            "server.jar" to "9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06",
            "common.jar" to "066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633"
        )
        val STOCK_GAME_PINS=GAME_PINS + ("server.jar" to "ba5301b2e9b56dc9ab7eae9d8ac45188336835e37184e329c90128ea8ab01f64")
        fun verifiedGamePins(root: File): Map<String,String> = selectPins(root,GAME_PINS,STOCK_GAME_PINS)
        private fun selectPins(root: File, prepared: Map<String,String>, stock: Map<String,String>): Map<String,String> {
            val actual=(prepared.keys+stock.keys).associateWith { name ->
                val file=File(root,name)
                require(file.isFile) { "Missing $name. Include the complete server runtime." }
                ProbeInputs.sha256(file)
            }
            return listOf(prepared,stock).firstOrNull { it == actual }
                ?: error("Unsupported server.jar/common.jar combination. Use the verified original WurmServerLauncher ZIP or the supported Thor export. Import was not activated.")
        }
    }
}
