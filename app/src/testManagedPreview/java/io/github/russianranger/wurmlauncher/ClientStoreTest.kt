package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.*

class ClientStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun zip(entries: Map<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name,bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()
    private fun files() = linkedMapOf(
        "client.jar" to zip(mapOf("com/wurmonline/client/WurmClientBase.class" to byteArrayOf(1))),
        "common.jar" to zip(mapOf("fixture.txt" to byteArrayOf(2))),
        "lib/lwjgl.jar" to zip(mapOf("org/lwjgl/opengl/Display.class" to byteArrayOf(3))),
        "packs/test.asset" to byteArrayOf(4,5,6))
    @Test fun importWrapperPersistsClasspathAndHashesAcrossReopen() {
        val store = ClientStore(temp.newFolder("client"))
        val input = zip(files().mapKeys { "WurmLauncher/${it.key}" })
        val accepted = store.importZip(input.inputStream()) {}
        val reopened = ClientStore(store.home).current()!!
        assertEquals(accepted,reopened); assertEquals("client.jar",reopened.jars.first())
        reopened.jars.forEach { assertEquals(reopened.hashes[it],ProbeInputs.sha256(File(reopened.root,it))) }
        assertArrayEquals(byteArrayOf(4,5,6), File(reopened.root,"packs/test.asset").readBytes())
        assertTrue(reopened.inventory.contains("Resource candidates: packs"))
    }
    @Test fun unsafeMissingDuplicateAndOversizeImportsPreservePreviousGeneration() {
        val store = ClientStore(temp.newFolder("client")); val accepted = store.importZip(zip(files()).inputStream()) {}
        val bad = listOf(files() + ("../escape" to byteArrayOf(1)), files() + ("/absolute" to byteArrayOf(1)),
            files() + ("C:/bad" to byteArrayOf(1)), files() - "common.jar", files() + ("lib/duplicate.jar" to files().getValue("client.jar")))
        for (entries in bad) {
            assertThrows(Exception::class.java) { store.importZip(zip(entries).inputStream()) {} }
            assertEquals(accepted.id,store.current()!!.id)
        }
        assertThrows(Exception::class.java) { ClientStore(store.home, 10).importZip(zip(files()).inputStream()) {} }
        assertEquals(accepted.id,store.current()!!.id)
        assertFalse(File(temp.root,"escape").exists())
    }
}
