package io.github.russianranger.wurmlauncher

import java.io.File
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerDatabaseLayoutTest {
    @get:Rule val temp=TemporaryFolder()
    private val database="SQLite format 3\u0000".toByteArray()+ByteArray(496) { 7 }
    private fun world(root:File,name:String,host:String="localhost"):File {
        val world=File(root,name).apply { mkdirs() }
        File(world,"wurm.ini").writeText("# preserve comments\r\nDB_HOST=$host\r\nLOGIN_DB_HOST=localhost\r\nSITE_DB_HOST=localhost\r\nCUSTOM=retained\\u0020value\r\n",Charsets.ISO_8859_1)
        databases(world)
        return world
    }
    private fun databases(parent:File) {
        File(parent,"sqlite").mkdirs()
        ServerDatabaseLayout.DATABASES.forEach { File(parent,"sqlite/$it").writeBytes(database) }
    }
    private fun host(world:File)=Properties().apply { File(world,"wurm.ini").inputStream().use { load(it) } }.getProperty("DB_HOST")
    private fun fails(action:()->Unit) { try { action(); fail("Expected failure") } catch(expected:Exception) {} }

    @Test fun repairsBothDesktopWorldsPreservingEverythingExceptHostAndIsIdempotent() {
        val root=temp.newFolder()
        for(name in listOf("Adventure","Creative")) world(root,name)
        val before=root.walkTopDown().filter { it.isFile }.associate { it to it.readBytes() }
        ServerDatabaseLayout.prepare(root)
        before.forEach { (file,bytes) ->
            val expected=if(file.name=="wurm.ini") bytes.toString(Charsets.ISO_8859_1)
                .replace("\r\nDB_HOST=localhost","\r\nDB_HOST=${file.parentFile!!.name}").toByteArray(Charsets.ISO_8859_1) else bytes
            assertArrayEquals(expected,file.readBytes())
        }
        assertEquals("Adventure",host(File(root,"Adventure")))
        assertEquals("Creative",host(File(root,"Creative")))
        assertEquals(File(root,"Adventure/sqlite/wurmlogin.db").canonicalFile,WorldSettings.database(root,"Adventure"))
        val once=File(root,"Adventure/wurm.ini").readBytes()
        ServerDatabaseLayout.prepare(root)
        assertArrayEquals(once,File(root,"Adventure/wurm.ini").readBytes())
    }
    @Test fun preservesExistingSharedDatabaseSelection() {
        val root=temp.newFolder();val world=world(root,"Adventure");databases(File(root,"localhost"))
        val before=File(world,"wurm.ini").readBytes()
        ServerDatabaseLayout.prepare(root)
        assertArrayEquals(before,File(world,"wurm.ini").readBytes())
        assertEquals(File(root,"localhost/sqlite/wurmlogin.db").canonicalFile,WorldSettings.database(root,"Adventure"))
    }
    @Test fun missingCorruptOrPartialConfiguredDatabasesFailBeforeEditingAnyWorld() {
        for(mode in listOf("missing","corrupt","shared")) {
            val root=temp.newFolder();val first=world(root,"Adventure");val second=world(root,"Creative")
            when(mode) {
                "missing" -> File(second,"sqlite/wurmitems.db").delete()
                "corrupt" -> File(second,"sqlite/wurmitems.db").writeBytes(ByteArray(512))
                else -> File(root,"localhost/sqlite").mkdirs()
            }
            val before=File(first,"wurm.ini").readBytes()
            fails { ServerDatabaseLayout.prepare(root) }
            assertArrayEquals(before,File(first,"wurm.ini").readBytes())
        }
    }
    @Test fun customMissingExternalOrAmbiguousHostIsNotSilentlyReplaced() {
        for(host in listOf("custom-missing","../outside","localhost\r\nDB_HOST:localhost")) {
            val root=temp.newFolder();val world=world(root,"Adventure",host)
            val before=File(world,"wurm.ini").readBytes()
            fails { ServerDatabaseLayout.prepare(root) }
            assertArrayEquals(before,File(world,"wurm.ini").readBytes())
        }
    }
    @Test fun unicodeAndSpaceWorldNameIsEscapedUsingPropertiesRules() {
        val root=temp.newFolder();val world=world(root,"A world é")
        ServerDatabaseLayout.prepare(root)
        assertEquals(world.name,host(world))
        assertEquals(File(world,"sqlite/wurmlogin.db").canonicalFile,WorldSettings.database(root,world.name))
    }
}
