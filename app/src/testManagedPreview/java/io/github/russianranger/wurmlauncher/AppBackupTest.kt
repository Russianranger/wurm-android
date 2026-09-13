package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.Base64
import java.util.zip.*

class AppBackupTest {
    @get:Rule val temp=TemporaryFolder()
    private class Prefs(var value: String): AppBackup.Preferences {
        override fun read()=value.toByteArray()
        override fun validate(bytes: ByteArray) { require(bytes.toString(Charsets.UTF_8).startsWith("prefs:")) }
        override fun replace(bytes: ByteArray) { validate(bytes); value=bytes.toString(Charsets.UTF_8) }
    }
    private class ProcessDeath: Error()
    private val poc by lazy { Base64.getMimeDecoder().decode(listOf(File("../poc/artifacts/wurm-arm64-poc.jar.base64"),File("poc/artifacts/wurm-arm64-poc.jar.base64")).first(File::isFile).readText()) }
    private fun zip(entries: Map<String,ByteArray>)=ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z ->
        entries.forEach { (name,bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
    } }.toByteArray()
    private fun entries(bytes: ByteArray): Map<String,ByteArray> {
        val result=linkedMapOf<String,ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z -> while(true) { val e=z.nextEntry ?: break; result[e.name]=z.readBytes() } }
        return result
    }
    private fun file(root: File,path: String,text: String) { File(root,path).apply { parentFile!!.mkdirs(); writeText(text) } }
    private fun installation(name: String): File {
        val root=temp.newFolder(name)
        val jar=zip(mapOf("fixture" to byteArrayOf(1)))
        val server=ManagedWorkspace(File(root,"managed-preview"))
        server.imports.importZip(zip(ManagedRuntimeStore.REQUIRED_JARS.associateWith { jar } + mapOf(
            "lib/fixture.jar" to jar,"Adventure/wurm.ini" to "SQLITE=true".toByteArray(),"Adventure/sqlite/items.db" to name.toByteArray())).inputStream(),poc)
        val work=server.ensureWorking {}
        file(work,"mods/active.config","settings=$name")
        file(work,"android-mods/disabled/off/data.txt","disabled=$name")
        server.saveCheckpoint {}
        val client=ClientStore(File(root,"managed-client"))
        val c=client.importZip(zip(mapOf(
            "client.jar" to zip(mapOf(ClientStore.ENGINE to byteArrayOf(1))),"common.jar" to jar,
            "lib/lwjgl.jar" to zip(mapOf("org/lwjgl/opengl/Display.class" to byteArrayOf(1))),
            "PlayerFiles/Thor/keybindings.txt" to "bind $name".toByteArray(),
            "android-mods/disabled/map/config.txt" to "map=$name".toByteArray())).inputStream()) {}
        file(c.root,"mods/livemap/data.txt","live=$name")
        file(client.home,"user/PlayerFiles/Thor/settings.txt","player=$name")
        file(client.home,"session-obsolete/frame.bin","excluded")
        ControllerProfile(mouseSpeed=650f).save(File(root,"controller.properties"))
        file(root,"client-graphics-frame.bin","excluded")
        return root
    }
    private fun archive(root: File,prefs: Prefs)=ByteArrayOutputStream().also { AppBackup(root).export(it,prefs,"test") {} }.toByteArray()
    private fun assertWorld(root: File,name: String) {
        val server=ManagedWorkspace(File(root,"managed-preview")); val client=ClientStore(File(root,"managed-client"))
        assertEquals(name,File(server.working(),"Adventure/sqlite/items.db").readText())
        assertEquals("settings=$name",File(server.working(),"mods/active.config").readText())
        assertEquals("disabled=$name",File(server.working(),"android-mods/disabled/off/data.txt").readText())
        assertEquals("bind $name",File(client.current()!!.root,"PlayerFiles/Thor/keybindings.txt").readText())
        assertEquals("player=$name",File(client.home,"user/PlayerFiles/Thor/settings.txt").readText())
        assertEquals("live=$name",File(client.current()!!.root,"mods/livemap/data.txt").readText())
        assertTrue(server.checkpoint.isFile)
    }
    @Test fun completeRoundTripPreservesBothWorldCopiesModsBindingsUserAndPreferences() {
        val source=installation("source"); val target=installation("target")
        val bytes=archive(source,Prefs("prefs:source")); val prefs=Prefs("prefs:target")
        assertFalse(entries(bytes).keys.any { "session-obsolete" in it || "client-graphics-frame" in it || it.endsWith(".lock") })
        AppBackup(target).restore(bytes.inputStream(),prefs) {}
        assertWorld(target,"source"); assertEquals("prefs:source",prefs.value)
        val original=ManagedWorkspace(File(target,"managed-preview")).imports.current()!!.runtime
        assertEquals("source",File(original,"Adventure/sqlite/items.db").readText())
        assertFalse(File(target,"backup-restore").exists())
        assertEquals(650f,ControllerProfile.load(File(target,"controller.properties")).mouseSpeed,0f)
    }
    @Test fun corruptTruncatedUnknownAndOversizedArchivesLeaveCurrentInstallationUntouched() {
        val source=installation("source"); val target=installation("target"); val prefs=Prefs("prefs:target")
        val bytes=archive(source,Prefs("prefs:source")); val content=entries(bytes)
        val changed=content.keys.first { it.endsWith("items.db") }
        for(bad in listOf(zip(content+(changed to "damaged".toByteArray())),bytes.copyOf(bytes.size/2),zip(mapOf("server.jar" to byteArrayOf(1))),zip(content+("data/../bad" to byteArrayOf(1))))) {
            assertThrows(Exception::class.java) { AppBackup(target).restore(bad.inputStream(),prefs) {} }
            assertWorld(target,"target"); assertEquals("prefs:target",prefs.value)
        }
        assertThrows(Exception::class.java) { AppBackup(target,10).restore(bytes.inputStream(),prefs) {} }
        assertWorld(target,"target")
    }
    @Test fun processDeathAtEveryPublicationBoundaryRollsBackUntilCommittedAndRecoveryIsRepeatable() {
        val source=installation("source"); val bytes=archive(source,Prefs("prefs:source"))
        val boundaries=listOf("prepared","old-managed-preview","new-managed-preview","old-managed-client","new-managed-client",
            "old-controller.properties","new-controller.properties","new-preferences","committed")
        boundaries.forEachIndexed { i,boundary ->
            val target=installation("target$i"); val prefs=Prefs("prefs:target$i")
            assertThrows(ProcessDeath::class.java) { AppBackup(target,checkpoint={ if(it==boundary) throw ProcessDeath() }).restore(bytes.inputStream(),prefs) {} }
            AppBackup(target).recover(prefs); AppBackup(target).recover(prefs)
            val expected=if(boundary=="committed") "source" else "target$i"
            assertWorld(target,expected); assertEquals("prefs:$expected",prefs.value)
        }
    }
    @Test fun restoringToEmptyInstallationAndFailureAfterPreferencesDoNotLosePriorAbsence() {
        val source=installation("source"); val bytes=archive(source,Prefs("prefs:source")); val target=temp.newFolder()
        val prefs=Prefs("prefs:empty")
        assertThrows(ProcessDeath::class.java) { AppBackup(target,checkpoint={ if(it=="new-preferences") throw ProcessDeath() }).restore(bytes.inputStream(),prefs) {} }
        AppBackup(target).recover(prefs)
        assertFalse(File(target,"managed-client").exists()); assertFalse(File(target,"managed-preview").exists())
        assertEquals("prefs:empty",prefs.value)
        AppBackup(target).restore(bytes.inputStream(),prefs) {}; assertWorld(target,"source")
    }
    @Test fun maintenanceReservationExcludesBothRuntimeOwners() {
        assertTrue(OperationGate.enter()); assertTrue(OperationGate.enter()); assertFalse(OperationGate.beginMaintenance())
        OperationGate.leave(); OperationGate.leave()
        assertTrue(OperationGate.beginMaintenance()); assertFalse(OperationGate.enter()); assertFalse(OperationGate.beginMaintenance())
        OperationGate.endMaintenance(); assertTrue(OperationGate.enter()); OperationGate.leave()
    }
}
