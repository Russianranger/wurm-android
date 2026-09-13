package io.github.russianranger.wurmlauncher

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRuntimePreparationTest {
    @get:Rule val temp=TemporaryFolder()
    private fun zip(files: Map<String,ByteArray>)=ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { out -> files.forEach { (name,bytes) ->
            out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry()
        } }
    }.toByteArray()
    private val jar=zip(mapOf("fixture" to "Authored fixture, no Wurm code".toByteArray()))
    private val gamePins=ServerRuntimePreparation.GAME_PINS.mapValues { ManagedRuntimeStore.sha256(jar) }
    private val deps=ProbeInputs.BASELINE.mapValues { ManagedRuntimeStore.sha256(jar) }
    private val files=linkedMapOf("server.jar" to jar,"common.jar" to jar,"lib/fixture.jar" to jar,
        "Adventure/wurm.ini" to "retain=world\r\n".toByteArray(),
        "Adventure/sqlite/items.db" to byteArrayOf(7,1,9),"mods/example.properties" to "enabled=false".toByteArray())
    private val poc by lazy {
        Base64.getMimeDecoder().decode(listOf(File("../poc/artifacts/wurm-arm64-poc.jar.base64"),
            File("poc/artifacts/wurm-arm64-poc.jar.base64")).first { it.isFile }.readText())
    }
    private fun prepared(store: ManagedRuntimeStore, entries: Map<String,ByteArray> = files,
                         asset: (String) -> java.io.InputStream = { jar.inputStream() }): ManagedRuntimeStore.Installed {
        val prep=ServerRuntimePreparation(asset,gamePins,deps)
        return store.importPreparedZip(zip(entries).inputStream(),poc,{ prep.prepare(it) })
    }
    private fun fails(action: () -> Unit) {
        try { action(); fail("Expected preparation failure") } catch(expected: Exception) { }
    }
    @Test fun suppliesOfflineDependenciesAndPocPreservingGameWorldAndMods() {
        val store=ManagedRuntimeStore(temp.newFolder())
        var opens=0
        val result=prepared(store,files.mapKeys { "runtime/${it.key}" }) { opens++; jar.inputStream() }
        assertEquals(2,opens)
        files.forEach { (name,bytes) -> assertArrayEquals(bytes,File(result.runtime,name).readBytes()) }
        deps.forEach { (name,hash) -> assertEquals(hash,result.jarHashes["poc-lib/$name"]) }
        assertArrayEquals(poc,File(result.runtime,"wurm-arm64-poc.jar").readBytes())
        val inventory=result.runtime.walkTopDown().filter { it.isFile }.toList()
        assertEquals(inventory.size,result.fileCount)
        assertEquals(inventory.sumOf { it.length() },result.bytes)
        val manifest=Properties().apply { File(result.runtime,"wurm-preparation.properties").inputStream().use { load(it) } }
        assertEquals(ServerRuntimePreparation.RECIPE,manifest.getProperty("recipe"))
        assertEquals(result,store.current())
    }
    @Test fun reimportPreparedExportReusesVerifiedDependenciesWithoutAssetReads() {
        val store=ManagedRuntimeStore(temp.newFolder()); val first=prepared(store)
        val entries=first.runtime.walkTopDown().filter { it.isFile }.associate { it.relativeTo(first.runtime).invariantSeparatorsPath to it.readBytes() }
        val second=prepared(store,entries) { error("No dependency read expected") }
        files.forEach { (name,bytes) -> assertArrayEquals(bytes,File(second.runtime,name).readBytes()) }
    }
    @Test fun unknownGameAndTamperedDependencyLeavePreviousImportSelected() {
        val store=ManagedRuntimeStore(temp.newFolder()); val first=prepared(store)
        fails { prepared(store,files+mapOf("server.jar" to zip(mapOf("unknown" to byteArrayOf(1))))) { error("Must reject before asset read") } }
        assertEquals(first,store.current())
        fails { prepared(store,files+mapOf("poc-lib/${deps.keys.first()}" to byteArrayOf(1))) }
        assertEquals(first,store.current())
        assertArrayEquals(files.getValue("Adventure/sqlite/items.db"),File(first.runtime,"Adventure/sqlite/items.db").readBytes())
    }
    @Test fun corruptAssetOrInterruptedPreparationCannotPublish() {
        val home=temp.newFolder(); val store=ManagedRuntimeStore(home); val first=prepared(store)
        fails { prepared(store) { byteArrayOf(9,8,7).inputStream() } }
        assertEquals(first,store.current())
        fails { prepared(store) { throw InterruptedException("Simulated cancellation") } }
        assertEquals(first,store.current())
        assertEquals(listOf(first.generation),home.listFiles()!!.filter { it.isDirectory }.map { it.name })
    }
    @Test fun preparationAddedBytesCountTowardImportLimit() {
        val home=temp.newFolder(); val first=prepared(ManagedRuntimeStore(home))
        val limited=ManagedRuntimeStore(home,maxBytes=files.values.sumOf { it.size.toLong() }+jar.size)
        fails { prepared(limited) }
        assertEquals(first,limited.current())
    }
}
