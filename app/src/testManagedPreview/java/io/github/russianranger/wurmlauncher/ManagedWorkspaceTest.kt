package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ManagedWorkspaceTest {
    @get:Rule val temp = TemporaryFolder()
    private val poc by lazy {
        Base64.getMimeDecoder().decode(listOf(File("../poc/artifacts/wurm-arm64-poc.jar.base64"),
            File("poc/artifacts/wurm-arm64-poc.jar.base64")).first { it.isFile }.readText())
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { zip -> files.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun workspace(): ManagedWorkspace {
        val fixtureJar = zip(mapOf("fixture" to "preserved SQL patch fixture, no Wurm code".toByteArray()))
        val entries = ManagedRuntimeStore.REQUIRED_JARS.associateWith { fixtureJar }.toMutableMap().apply {
            put("lib/", byteArrayOf()); put("lib/sql-patch-fixture.jar", fixtureJar)
            put("Adventure/wurm.ini", "unchanged\r\n".toByteArray()); put("Adventure/sqlite/items.db", byteArrayOf(1, 2, 3))
        }
        return ManagedWorkspace(temp.newFolder()).also { workspace -> workspace.exclusive {
            workspace.imports.importZip(zip(entries).inputStream(), poc)
            workspace.ensureWorking {}
        } }
    }
    @Test fun workingChangesDoNotTouchOriginalAndCheckpointRestoresThePreStartState() {
        val workspace = workspace()
        workspace.exclusive {
            val original = workspace.imports.current()!!.runtime
            val working = workspace.working()!!
            workspace.imports.current()!!.jarHashes.forEach { (path, expected) ->
                assertEquals(expected, ProbeInputs.sha256(File(working, path)))
            }
            val db = File(working, "Adventure/sqlite/items.db")
            db.writeBytes(byteArrayOf(4, 5, 6))
            workspace.saveCheckpoint {}
            db.writeBytes(byteArrayOf(7, 8, 9))
            workspace.recoveryRequired.writeText("simulated interrupted server")
            assertArrayEquals(byteArrayOf(1, 2, 3), File(original, "Adventure/sqlite/items.db").readBytes())
            val recovered = workspace.restoreCheckpoint(poc) {}
            assertFalse(workspace.recoveryRequired.exists())
            assertArrayEquals(byteArrayOf(4, 5, 6), File(recovered, "Adventure/sqlite/items.db").readBytes())
            assertArrayEquals("unchanged\r\n".toByteArray(), File(recovered, "Adventure/wurm.ini").readBytes())
            assertTrue(workspace.checkpoint.isFile)
            val restored = workspace.restoreOriginal {}
            assertArrayEquals(byteArrayOf(1, 2, 3), File(restored, "Adventure/sqlite/items.db").readBytes())
        }
        assertEquals(workspace.working(), ManagedWorkspace(workspace.home).working())
    }
    @Test fun corruptCheckpointOrOriginalCannotReplaceWorkingCopy() {
        val workspace = workspace()
        workspace.exclusive {
            val before = workspace.working()!!
            workspace.checkpoint.writeText("broken ZIP")
            workspace.recoveryRequired.writeText("simulated interrupted server")
            assertTrue(runCatching { workspace.restoreCheckpoint(poc) {} }.isFailure)
            assertEquals(before, workspace.working())
            assertTrue(workspace.recoveryRequired.exists())
            File(workspace.imports.current()!!.runtime, "server.jar").appendText("corrupt")
            assertTrue(runCatching { workspace.restoreOriginal {} }.isFailure)
            assertEquals(before, workspace.working())
            assertArrayEquals(byteArrayOf(1, 2, 3), File(before, "Adventure/sqlite/items.db").readBytes())
        }
    }
    @Test fun exportContainsRuntimeRelativePathsAndRejectsLinksWithoutReplacingCheckpoint() {
        val workspace = workspace()
        workspace.exclusive {
            workspace.saveCheckpoint {}
            val saved = workspace.checkpoint.readBytes()
            val out = ByteArrayOutputStream()
            workspace.export(workspace.working()!!, out)
            val names = mutableListOf<String>()
            ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; names.add(entry.name) }
            }
            assertTrue("server.jar" in names)
            assertTrue("Adventure/sqlite/items.db" in names)
            assertFalse(names.any { it.startsWith("/") || "original/" in it })
            val outside = temp.newFile().apply { writeText("outside data must not be exported") }
            Files.createSymbolicLink(File(workspace.working(), "outside").toPath(), outside.toPath())
            assertTrue(runCatching { workspace.saveCheckpoint {} }.isFailure)
            assertArrayEquals(saved, workspace.checkpoint.readBytes())
        }
    }
    @Test fun simultaneousWorkspaceOperationIsRejectedAndLockReleasesAfterFailure() {
        val workspace = workspace()
        workspace.exclusive {
            assertTrue(runCatching { ManagedWorkspace(workspace.home).exclusive { error("must not run") } }.isFailure)
        }
        assertTrue(runCatching { workspace.exclusive { error("operation failed") } }.isFailure)
        assertEquals("released", workspace.exclusive { "released" })
    }
    @Test fun worldAndArgumentBoundariesCannotEscapeTheImportedRuntime() {
        listOf("../Adventure", "/Adventure", "Adventure/../other", "Adventure\n").forEach { world ->
            assertTrue(runCatching { ManagedLaunch(world).validate(listOf(world)) }.isFailure)
        }
        val config = ManagedLaunch("My Adventure", 4096, 3724)
        config.validate(listOf("My Adventure"))
        val args = config.arguments(File("/native"), File("/java"), File("/session/tmp"), File("/work"), File("/helper.jar"), false)
        assertEquals("My Adventure", args.last())
        val cp = args[args.indexOf("-cp") + 1].split(':')
        assertEquals("wurm-arm64-poc.jar", cp.first())
        assertTrue(cp.indexOf("poc-lib/sqlite-jdbc-3.53.2.1.jar") < cp.indexOf("lib/*"))
        assertFalse("-Dwurm.probe.network=true" in args)
        val probe = config.arguments(File("/native"), File("/java"), File("/session/tmp"), File("/work"), File("/helper.jar"), true)
        assertTrue("-Dwurm.probe.network=true" in probe)
        val probeCp = probe[probe.indexOf("-cp") + 1]
        assertFalse("server.jar" in probeCp)
        assertFalse("common.jar" in probeCp)
    }
}
