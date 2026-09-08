package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProbeInputsTest {
    @get:Rule val temp = TemporaryFolder()
    private val fixture = "known input, no game code".toByteArray()
    private val pin = MessageDigest.getInstance("SHA-256").digest(fixture).joinToString("") { "%02x".format(it) }
    private val expected = linkedMapOf("driver.jar" to pin, "natives.jar" to pin)

    @Test fun copiesOnlyTheTwoVerifiedInputsFromWrappedRuntime() {
        val directory = temp.newFolder()
        val files = linkedMapOf("runtime/poc-lib/driver.jar" to fixture, "runtime/poc-lib/natives.jar" to fixture,
            "runtime/Adventure/sqlite/items.db" to byteArrayOf(1, 2, 3), "runtime/server.jar" to fixture)
        val copied = ProbeInputs(expected).install(zip(files).inputStream(), directory)
        assertEquals(listOf("driver.jar", "natives.jar"), copied.map { it.name })
        assertEquals(listOf("driver.jar", "natives.jar"), directory.list()!!.sorted())
        copied.forEach { assertArrayEquals(fixture, it.readBytes()) }
    }

    @Test fun rejectsMissingOrAmbiguousInputs() {
        fails { ProbeInputs(expected).install(zip(mapOf("poc-lib/driver.jar" to fixture)).inputStream(), temp.newFolder()) }
        fails {
            ProbeInputs(expected).install(zip(mapOf("poc-lib/driver.jar" to fixture, "copy/poc-lib/driver.jar" to fixture,
                "poc-lib/natives.jar" to fixture)).inputStream(), temp.newFolder())
        }
    }

    @Test fun rejectsChangedChecksumAndRemovesTemporaryArchive() {
        val directory = temp.newFolder()
        fails { ProbeInputs(expected).install(zip(mapOf("poc-lib/driver.jar" to byteArrayOf(9), "poc-lib/natives.jar" to fixture)).inputStream(), directory) }
        assertFalse(File(directory, "selected-runtime.zip").exists())
    }

    @Test fun boundsBothCompressedZipAndIndividualJar() {
        val archive = zip(mapOf("poc-lib/driver.jar" to fixture, "poc-lib/natives.jar" to fixture))
        fails { ProbeInputs(expected, archiveLimit = 5).install(archive.inputStream(), temp.newFolder()) }
        fails { ProbeInputs(expected, jarLimit = 5).install(archive.inputStream(), temp.newFolder()) }
    }

    @Test fun ignoresTraversalNamesRatherThanExtractingThem() {
        val directory = temp.newFolder()
        val archive = zip(mapOf("../../outside" to fixture, "poc-lib/driver.jar" to fixture, "poc-lib/natives.jar" to fixture))
        ProbeInputs(expected).install(archive.inputStream(), directory)
        assertEquals(listOf("driver.jar", "natives.jar"), directory.list()!!.sorted())
    }

    private fun fails(action: () -> Unit) = assertNotNull(runCatching(action).exceptionOrNull())
    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
        } }
        return output.toByteArray()
    }
}
