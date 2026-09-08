package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ManagedRuntimeStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val poc: ByteArray by lazy {
        val artifact = listOf(File("../poc/artifacts/wurm-arm64-poc.jar.base64"), File("poc/artifacts/wurm-arm64-poc.jar.base64"))
            .first { it.isFile }
        Base64.getMimeDecoder().decode(artifact.readText())
    }
    private val fakeJar = zip(linkedMapOf("test-fixture.txt" to "SQLite-compatible item update fixture; no game code".toByteArray()))
    private val worldDb = byteArrayOf(0, 1, -1, 18, 29)

    private fun runtime(): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        ManagedRuntimeStore.REQUIRED_JARS.forEach { put(it, fakeJar) }
        put("lib/", byteArrayOf())
        put("lib/item-fix-fixture.jar", fakeJar)
        put("Adventure/wurm.ini", "PRESERVE_EVERY_BYTE = yes\r\n".toByteArray())
        put("Adventure/sqlite/items.db", worldDb)
        put("Creative/sqlite/", byteArrayOf())
    }

    @Test fun packagesExactlyTheTwoJava17PocClasses() {
        assertEquals(ManagedRuntimeStore.POC_SHA256, ManagedRuntimeStore.sha256(poc))
        val classes = mutableSetOf<String>()
        ZipInputStream(poc.inputStream()).use { jar ->
            while (true) {
                val entry = jar.nextEntry ?: break
                if (entry.name.endsWith(".class")) {
                    classes.add(entry.name)
                    val bytes = jar.readBytes()
                    assertEquals(61, ((bytes[6].toInt() and 255) shl 8) or (bytes[7].toInt() and 255))
                }
            }
        }
        assertEquals(setOf("poc/AndroidServerMain.class", "SteamJni/SteamServerApi.class"), classes)
    }

    @Test fun importsWrappedRuntimeAndPreservesPatchedJarsWorldAndConfiguration() {
        val files = runtime()
        val store = ManagedRuntimeStore(temp.newFolder())
        val result = store.importZip(zip(files.mapKeys { "runtime/${it.key}" }).inputStream(), poc)
        files.filterKeys { !it.endsWith("/") }.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, File(result.runtime, path).readBytes())
        }
        assertArrayEquals(poc, File(result.runtime, "wurm-arm64-poc.jar").readBytes())
        assertEquals(listOf("Adventure", "Creative"), result.worlds)
        assertEquals(result, store.current())
        assertEquals(ManagedRuntimeStore.sha256(fakeJar), result.jarHashes["lib/item-fix-fixture.jar"])
        assertFalse(result.report("Creative").contains("PRESERVE_EVERY_BYTE"))
        assertTrue(result.report("Creative").contains("patch behavior not verified"))
    }

    @Test fun acceptsIdenticalPocWithoutChangingIt() {
        val files = runtime().apply { put("wurm-arm64-poc.jar", poc) }
        val result = ManagedRuntimeStore(temp.newFolder()).importZip(zip(files).inputStream(), poc)
        assertArrayEquals(poc, File(result.runtime, "wurm-arm64-poc.jar").readBytes())
    }

    @Test fun rejectsConflictingPocAndRetainsPreviousRuntime() {
        val store = ManagedRuntimeStore(temp.newFolder())
        val first = store.importZip(zip(runtime()).inputStream(), poc)
        val files = runtime().apply { put("wurm-arm64-poc.jar", fakeJar) }
        fails("POC differs") { store.importZip(zip(files).inputStream(), poc) }
        assertEquals(first, store.current())
        assertArrayEquals(worldDb, File(first.runtime, "Adventure/sqlite/items.db").readBytes())
    }

    @Test fun rejectsTraversalAbsoluteWindowsAndControlPaths() {
        listOf("../escape", "/escape", "a/../../escape", "C:/escape", "a\\escape", "a/./escape", "a//escape", "a\nescape").forEach { name ->
            val home = temp.newFolder()
            fails("unsafe path") { ManagedRuntimeStore(home).importZip(zip(linkedMapOf(name to worldDb)).inputStream(), poc) }
            assertNull(ManagedRuntimeStore(home).current())
            assertTrue(home.listFiles()!!.isEmpty())
        }
    }

    @Test fun validatesNativeJarAndWorldBeforeCommitting() {
        val store = ManagedRuntimeStore(temp.newFolder())
        val first = store.importZip(zip(runtime()).inputStream(), poc)
        val missingNative = runtime().apply { remove("poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar") }
        fails("Missing poc-lib") { store.importZip(zip(missingNative).inputStream(), poc) }
        val noWorld = runtime().filterKeys { !it.startsWith("Adventure/") && !it.startsWith("Creative/") }
        fails("No world found") { store.importZip(zip(noWorld).inputStream(), poc) }
        assertEquals(first, store.current())
    }

    @Test fun boundsUncompressedDataEntryCountAndFreeSpace() {
        fails("import limit") { ManagedRuntimeStore(temp.newFolder(), maxBytes = 10).importZip(zip(runtime()).inputStream(), poc) }
        fails("entries") { ManagedRuntimeStore(temp.newFolder(), maxEntries = 1).importZip(zip(runtime()).inputStream(), poc) }
        fails("free internal storage") {
            ManagedRuntimeStore(temp.newFolder(), reserveBytes = Long.MAX_VALUE - 65536).importZip(zip(runtime()).inputStream(), poc)
        }
    }

    @Test fun anInterruptedReadCannotReplaceTheLastCommittedImport() {
        val home = temp.newFolder()
        val store = ManagedRuntimeStore(home)
        val first = store.importZip(zip(runtime()).inputStream(), poc)
        val bytes = zip(runtime())
        fails { store.importZip(bytes.copyOf(bytes.size / 3).inputStream(), poc) }
        assertEquals(first, store.current())
        assertEquals(listOf("current", first.generation).sorted(), home.list()!!.sorted())
    }

    @Test fun replacesOnlyAfterSuccessAndCleansInterruptedStagingOnRetry() {
        val home = temp.newFolder()
        val store = ManagedRuntimeStore(home)
        val first = store.importZip(zip(runtime()).inputStream(), poc)
        val abandoned = File(home, "00000000-0000-0000-0000-000000000000.pending").apply { mkdirs() }
        File(abandoned, "partial.db").writeBytes(worldDb)
        val nextFiles = runtime().apply { put("Adventure/sqlite/items.db", byteArrayOf(7, 8, 9)) }
        val second = store.importZip(zip(nextFiles).inputStream(), poc)
        assertEquals(second, ManagedRuntimeStore(home).current())
        assertArrayEquals(byteArrayOf(7, 8, 9), File(second.runtime, "Adventure/sqlite/items.db").readBytes())
        assertFalse(first.runtime.exists())
        assertFalse(abandoned.exists())
    }

    @Test fun rejectsFileDirectoryConflictsWithoutCommitting() {
        val store = ManagedRuntimeStore(temp.newFolder())
        fails { store.importZip(zip(linkedMapOf("a" to worldDb, "a/b" to worldDb)).inputStream(), poc) }
        assertNull(store.current())
    }

    private fun fails(message: String? = null, action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertNotNull("Expected rejected import", error)
        if (message != null) assertTrue(error!!.message, error.message.orEmpty().contains(message))
    }

    companion object {
        private fun zip(files: Map<String, ByteArray>): ByteArray {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                files.forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    if (!name.endsWith("/")) zip.write(data)
                    zip.closeEntry()
                }
            }
            return bytes.toByteArray()
        }
    }
}
