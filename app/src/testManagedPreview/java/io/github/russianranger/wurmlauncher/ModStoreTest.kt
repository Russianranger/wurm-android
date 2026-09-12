package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.jar.*
import java.util.zip.*

class ModStoreTest {
    private fun <T> runtime(action: (File)->T): T {
        val root=Files.createTempDirectory("mod-store-test").toFile()
        // Exact public client loader fixture; test file movements with the same pin used by the app.
        val loader=File(requireNotNull(System.getenv("WURM_CLIENT_MOD_LOADER_JAR")))
        File(root,"android-mods/loader/modlauncher.jar").apply { parentFile.mkdirs(); loader.copyTo(this) }
        return try { action(root) } finally { root.deleteRecursively() }
    }
    private fun zip(files: Map<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (name,data)->z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() } }
    }.toByteArray()
    private fun mod(name: String, side: String="server", extra: String="", defaults: String="", config: String="", prefix: String="mods/"): Map<String,ByteArray> {
        val manifest=Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION]="1.0"; mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION]="1.2" }
        val jar=ByteArrayOutputStream().also { out -> JarOutputStream(out,manifest).use { j ->
            fun entry(path: String, data: String) { j.putNextEntry(JarEntry(path)); j.write(data.toByteArray()); j.closeEntry() }
            // Authored class bytes are sufficient for packaging-side interface inventory; JVM loading is tested separately.
            entry("test/Main.class",if(side=="server") "WurmServerMod" else "WurmClientMod")
            entry("META-INF/org.gotti.wurmunlimited.modloader/$name.properties",defaults)
            entry("META-INF/org.gotti.wurmunlimited.modloader/$name.config",config)
        } }.toByteArray()
        return mapOf("$prefix$name.properties" to "classname=test.Main\nclasspath=$name.jar\n$extra".toByteArray(), "$prefix$name/$name.jar" to jar)
    }
    private fun fails(block: ()->Unit): String {
        try { block() } catch(e: Exception) { return e.message.orEmpty() }
        throw AssertionError("Expected rejected operation")
    }
    @Test fun importsWrappedAndBareLayoutsDisabledThenMovesEveryOwnedUnit() = runtime { root ->
        val store=ModStore(root,"client")
        val files=mod("one","client",prefix="release/mods/")+mapOf("release/mods/one.config" to "volume=2".toByteArray(),"release/mods/one/data/settings.txt" to "kept".toByteArray())
        store.importZip(zip(files).inputStream()); store.importZip(zip(mod("two","client",prefix="")).inputStream())
        assertEquals(listOf(false,false),store.entries().map { it.enabled })
        assertFalse(File(root,"mods/one").exists()); store.toggle("one",true)
        assertEquals("volume=2",File(root,"mods/one.config").readText())
        assertEquals("kept",File(root,"mods/one/data/settings.txt").readText())
        store.toggle("one",false); assertFalse(File(root,"mods/one").exists())
        assertEquals(2,store.validate().size)
    }
    @Test fun generatedConfigAndOwnedDataSurviveRestartDisableEnable() = runtime { root ->
        val store=ModStore(root,"client"); store.importZip(zip(mod("one","client")).inputStream()); store.toggle("one",true)
        File(root,"mods/one.config").writeText("volume=7")
        File(root,"mods/one/cache.dat").writeText("state")
        ModStore(root,"client").toggle("one",false); store.toggle("one",true)
        assertEquals("volume=7",File(root,"mods/one.config").readText())
        assertEquals("state",File(root,"mods/one/cache.dat").readText())
        assertTrue(store.entries().single().files.containsKey("one/cache.dat"))
    }
    @Test fun interruptedToggleCompletesEveryBoundaryWithFreshOwner() {
        for(boundary in 0..2) runtime { root ->
            val store=ModStore(root,"client"); store.importZip(zip(mod("one","client")+mapOf("mods/one.config" to "value=1".toByteArray())).inputStream())
            fails { ModStore(root,"client") { index -> if(index==boundary) error("interruption") }.toggle("one",true) }
            val resumed=ModStore(root,"client"); resumed.recover(); resumed.recover()
            assertTrue(resumed.entries().single().enabled); assertEquals(1,resumed.validate().size)
            assertTrue(File(root,"mods/one/one.jar").isFile); assertFalse(File(root,"android-mods/transaction.properties").exists())
        }
    }
    @Test fun interruptedImportCanRecoverAndClearStage() = runtime { root ->
        fails { ModStore(root,"client") { error("interruption") }.importZip(zip(mod("one","client")).inputStream()) }
        val resumed=ModStore(root,"client"); resumed.recover()
        assertEquals(1,resumed.entries().size); assertFalse(resumed.entries().single().enabled)
        assertFalse(File(root,"android-mods").listFiles()!!.any { it.name.startsWith("import-") })
    }
    @Test fun dependenciesFromJarDefaultsAndConfigAreEnforced() = runtime { root ->
        val store=ModStore(root,"client")
        store.importZip(zip(mod("base","client")+mod("dependent","client",defaults="depend.requires=base",config="depend.import=base")).inputStream())
        assertTrue(fails { store.toggle("dependent",true) }.contains("requires base"))
        store.toggle("base",true); store.toggle("dependent",true)
        assertTrue(fails { store.toggle("base",false) }.contains("requires base"))
        store.toggle("dependent",false); store.toggle("base",false)
        assertTrue(store.entries().none { it.enabled })
    }
    @Test fun conflictsAndExternalStructuralEditsDoNotPartiallyToggle() = runtime { root ->
        val store=ModStore(root,"client")
        store.importZip(zip(mod("one","client")+mod("two","client",extra="depend.conflicts=one")).inputStream())
        store.toggle("one",true); assertTrue(fails { store.toggle("two",true) }.contains("conflicts"))
        assertFalse(store.entries().last().enabled)
        File(root,"mods/one.config").writeText("classpath=other.jar")
        assertTrue(fails { store.validate() }.contains("configuration changed"))
    }
    @Test fun wrongSideAndLegacyImportsAreRejectedWithoutManifest() = runtime { root ->
        assertTrue(fails { ModStore(root,"client").importZip(zip(mod("one")).inputStream()) }.contains("other side"))
        assertTrue(ModStore(root,"client").entries().isEmpty())
        assertFalse(File(root,"mods").exists())
    }
    @Test fun duplicateImportAndDestinationCollisionPreserveOriginal() = runtime { root ->
        val store=ModStore(root,"client"); val data=zip(mod("one","client")); store.importZip(data.inputStream())
        assertTrue(fails { store.importZip(data.inputStream()) }.contains("already imported"))
        val occupied=File(root,"mods/one.properties"); occupied.parentFile.mkdirs(); occupied.writeText("existing")
        assertTrue(fails { store.toggle("one",true) }.contains("occupied"))
        assertEquals("existing",occupied.readText()); assertFalse(store.entries().single().enabled)
    }
    @Test fun changedCodeAndUntrackedActiveFilesFailBeforeLaunch() = runtime { root ->
        val store=ModStore(root,"client"); store.importZip(zip(mod("one","client")).inputStream()); store.toggle("one",true)
        val jar=File(root,"mods/one/one.jar"); jar.appendText("changed")
        assertTrue(fails { store.validate() }.contains("changed"))
    }
    @Test fun activeUnmanagedFolderIsNotSilentlyLoaded() = runtime { root ->
        File(root,"mods/extra/extra.jar").apply { parentFile.mkdirs(); writeText("other") }
        assertTrue(fails { ModStore(root,"server").validate() }.contains("Unmanaged"))
    }
    @Test fun serverLoaderRequiredOnlyWhenModsEnabled() = runtime { root ->
        val store=ModStore(root,"server"); assertTrue(store.validate().isEmpty())
        store.importZip(zip(mod("one")).inputStream()); assertEquals(1,store.validate().size)
        store.toggle("one",true); assertTrue(fails { store.validate() }.contains("loader 0.47"))
    }
    @Test fun exportedRuntimeCopyRetainsManifestAndToggleState() = runtime { root ->
        val original=File(root,"original").apply { mkdir(); File(root,"android-mods").copyRecursively(File(this,"android-mods")) }; val copy=File(root,"copy")
        val store=ModStore(original,"client"); store.importZip(zip(mod("one","client")+mod("two","client")).inputStream()); store.toggle("one",true)
        original.copyRecursively(copy); val restored=ModStore(copy,"client")
        assertEquals(listOf(true,false),restored.validate().map { it.enabled }); restored.toggle("one",false)
        assertTrue(store.entries().first().enabled)
    }
    @Test fun invalidArchiveLayoutAndDesktopFilesDoNotReplaceRuntime() = runtime { root ->
        val store=ModStore(root,"server")
        assertTrue(fails { store.importZip(zip(mod("one")+mapOf("mods/one/native.dll" to byteArrayOf(1))).inputStream()) }.contains("desktop/native"))
        assertTrue(fails { store.importZip(zip(mod("one")+mapOf("mods/server.jar" to byteArrayOf(1))).inputStream()) }.contains("outside"))
        assertTrue(store.entries().isEmpty())
    }
    @Test fun actualLiveMapPackageImportsSharedAndRetainsSettingsAcrossDisableAndCopy() = runtime { root ->
        val store=ModStore(root,"client")
        File(requireNotNull(System.getenv("WURM_LIVEMAP_ZIP"))).inputStream().use { store.importZip(it) }
        assertEquals("true",store.entries().single().properties["sharedClassLoader"])
        store.toggle("livemap",true); assertEquals(1,store.validate().size)
        File(root,"mods/livemap.config").writeText("hiResMap=false\nshowHiddenOre=false\n")
        assertTrue(fails { store.setClientLoaderEnabled(false) }.contains("Turn off"))
        store.toggle("livemap",false);store.setClientLoaderEnabled(false)
        assertFalse(store.clientLoaderEnabled());assertEquals(1,store.validate().size)
        store.setClientLoaderEnabled(true);store.toggle("livemap",true)
        assertTrue(File(root,"mods/livemap.config").readText().contains("hiResMap=false"))
        val copy=File(root.parentFile,root.name+"-restored")
        try {
            root.copyRecursively(copy)
            val restored=ModStore(copy,"client")
            assertTrue(restored.clientLoaderEnabled());assertTrue(restored.loaderInstalled())
            assertTrue(restored.validate().single().enabled)
        } finally { copy.deleteRecursively() }
    }
    @Test fun loaderImportPinIsSideSpecificAndDoesNotInstallBundledMods() = runtime { root ->
        val bytes=File(requireNotNull(System.getenv("WURM_CLIENT_MOD_LOADER_JAR"))).readBytes()
        val archive=zip(mapOf("modlauncher.jar" to bytes,"patcher.jar" to byteArrayOf(1),"mods/unselected/unselected.jar" to byteArrayOf(2)))
        val store=ModStore(root,"client");store.loader.delete()
        assertTrue(store.importZip(archive.inputStream()).contains("Client loader 0.15 installed"))
        assertTrue(store.loaderInstalled());assertTrue(store.clientLoaderEnabled());assertTrue(store.validate().isEmpty())
        assertFalse(File(root,"patcher.jar").exists());assertFalse(File(root,"mods/unselected").exists())
        assertTrue(fails { ModStore(root,"server").importZip(archive.inputStream()) }.contains("server-modlauncher-0.47"))
        assertTrue(store.loaderInstalled())
        store.loader.appendText("changed")
        assertTrue(fails { store.validate() }.contains("client loader 0.15"))
    }
    @Test fun serverSharedLoadingRemainsDeferredAndClientNeedsEnabledLoader() = runtime { root ->
        assertTrue(fails { ModStore(root,"server").importZip(zip(mod("shared",extra="sharedClassLoader=true")).inputStream()) }.contains("shared server"))
        val store=ModStore(root,"client");store.setClientLoaderEnabled(false)
        store.importZip(zip(mod("one","client")).inputStream());store.toggle("one",true)
        assertTrue(fails { store.validate() }.contains("Enable the client loader"))
        store.setClientLoaderEnabled(true);assertEquals(1,store.validate().size)
    }
}
