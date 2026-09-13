package io.github.russianranger.wurmlauncher

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.*

/** Portable, checksummed files plus typed preferences. Caller owns both runtimes and native locks. */
class AppBackup(private val files: File, private val limit: Long=64L*1024*1024*1024,
    private val checkpoint: (String)->Unit={}) {
    interface Preferences { fun read(): ByteArray; fun validate(bytes: ByteArray); fun replace(bytes: ByteArray) }
    private val transaction get()=File(files,"backup-restore")
    private val roots=listOf("managed-preview","managed-client","controller.properties")
    private val reserve=128L*1024*1024
    private fun interrupted() { if(Thread.currentThread().isInterrupted) throw InterruptedException("Backup operation cancelled") }
    private fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(65536); while(true) {
            interrupted(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun write(file: File, bytes: ByteArray) {
        file.parentFile!!.mkdirs(); FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private fun marker(name: String) { write(File(transaction,name),byteArrayOf(1)); checkpoint(name) }
    private fun finishTransaction() {
        val cleanup=File(files,"backup-cleanup")
        if(cleanup.exists()) check(cleanup.deleteRecursively())
        move(transaction,cleanup) // Never partially delete a live recovery journal.
        cleanup.deleteRecursively()
    }
    private fun move(from: File,to: File) { to.parentFile!!.mkdirs(); Files.move(from.toPath(),to.toPath(),ATOMIC_MOVE) }
    private fun props(file: File)=Properties().apply { file.inputStream().use { load(it) } }
    private fun eligible(): List<File> {
        val selected=mutableListOf<File>()
        val server=ManagedWorkspace(File(files,"managed-preview"))
        server.imports.current()?.let { selected+=File(server.home,"original/current"); selected+=File(server.home,"original/${it.generation}") }
        server.working()?.let { selected+=File(server.home,"working"); selected+=it }
        listOf("before-start.zip","recovery-required").map { File(server.home,it) }.filter(File::exists).forEach(selected::add)
        val client=ClientStore(File(files,"managed-client"))
        client.current()?.let { selected+=File(client.home,"current"); selected+=File(client.home,it.id) }
        File(client.home,"user").takeIf(File::exists)?.let(selected::add)
        File(files,"controller.properties").takeIf(File::exists)?.let(selected::add)
        return selected
    }
    fun export(output: OutputStream,prefs: Preferences,version: String,progress: (String)->Unit) {
        check(!transaction.exists()) { "Finish pending recovery before backup" }
        val manifest=Properties().apply { setProperty("format","1"); setProperty("appVersion",version); setProperty("created",java.time.Instant.now().toString()) }
        var index=0; var bytes=0L
        ZipOutputStream(output.buffered()).use { zip ->
            fun add(name: String, source: File?, data: ByteArray?=null) {
                interrupted(); val directory=source?.isDirectory==true
                val entryName="data/$name"+(if(directory) "/" else "")
                zip.putNextEntry(ZipEntry(entryName))
                val digest=MessageDigest.getInstance("SHA-256"); var size=0L
                if(!directory) {
                    (source?.inputStream() ?: ByteArrayInputStream(requireNotNull(data))).use { input ->
                        val buffer=ByteArray(65536); while(true) { interrupted(); val n=input.read(buffer); if(n<0) break
                            bytes+=n; size+=n; require(bytes<=limit) { "Backup exceeds size limit" }
                            zip.write(buffer,0,n); digest.update(buffer,0,n)
                        }
                    }
                }
                zip.closeEntry()
                manifest.setProperty("entry.$index.name",name)
                manifest.setProperty("entry.$index.type",if(directory) "directory" else "file")
                manifest.setProperty("entry.$index.size",size.toString())
                manifest.setProperty("entry.$index.sha",digest.digest().joinToString("") { "%02x".format(it) })
                index++; require(index<=200000) { "Too many backup entries" }
                if(index%100==0) progress("Backing up ${bytes/1024/1024} MiB · $index entries")
            }
            for(root in eligible()) root.walkTopDown().onFail { f,e -> throw IOException("Cannot read ${f.name}",e) }.forEach { file ->
                require(!Files.isSymbolicLink(file.toPath()) && (file.isDirectory || file.isFile)) { "Unsupported backup file: ${file.name}" }
                add(file.relativeTo(files).invariantSeparatorsPath,file)
            }
            add("preferences.properties",null,prefs.read())
            manifest.setProperty("count",index.toString()); manifest.setProperty("bytes",bytes.toString())
            zip.putNextEntry(ZipEntry("backup.properties")); manifest.store(zip,"Wurm complete backup"); zip.closeEntry()
        }
        progress("Backup complete · ${bytes/1024/1024} MiB · $index entries")
    }
    private fun validName(name: String): Boolean = name.isNotBlank() && name.length<=1024 && !name.startsWith('/') &&
        '\\' !in name && ':' !in name && name.none(Char::isISOControl) && name.split('/').none { it.isEmpty() || it=="." || it==".." }
    private fun allowed(name: String)=name=="preferences.properties" || roots.any { name==it || (it!="controller.properties" && name.startsWith("$it/")) }
    private fun validate(stage: File,prefs: Preferences) {
        val manifest=props(File(stage,"backup.properties")); require(manifest.getProperty("format")=="1") { "Unsupported backup format" }
        val count=manifest.getProperty("count").toInt(); require(count in 1..200000)
        val expected=mutableSetOf<String>(); var total=0L
        for(i in 0 until count) {
            val name=manifest.getProperty("entry.$i.name") ?: error("Incomplete backup manifest")
            require(validName(name) && allowed(name) && expected.add(name)) { "Invalid backup manifest path" }
            val file=File(stage,"data/$name"); val kind=manifest.getProperty("entry.$i.type")
            require(kind in listOf("file","directory"))
            require(if(kind=="directory") file.isDirectory else file.isFile) { "Missing backup entry: $name" }
            val size=manifest.getProperty("entry.$i.size").toLong(); require(size>=0)
            if(kind=="file") { require(file.length()==size && hash(file)==manifest.getProperty("entry.$i.sha")) { "Damaged backup entry: $name" }; total+=size }
        }
        require(total==manifest.getProperty("bytes").toLong()) { "Backup size mismatch" }
        File(stage,"data").walkTopDown().filter { it.isFile }.forEach { require(it.relativeTo(File(stage,"data")).invariantSeparatorsPath in expected) { "Unlisted backup file" } }
        val data=File(stage,"data"); prefs.validate(File(data,"preferences.properties").readBytes())
        val server=ManagedWorkspace(File(data,"managed-preview"))
        // Metadata must resolve entirely inside staged roots before publication.
        server.imports.current()?.let { require(it.runtime.canonicalFile.toPath().startsWith(File(data,"managed-preview/original").canonicalFile.toPath())) }
        server.working(); ClientStore(File(data,"managed-client")).current()
        File(data,"controller.properties").takeIf(File::isFile)?.let { ControllerProfile.load(it) }
        File(data,"managed-preview").takeIf(File::exists)?.let {
            require(server.imports.current()!=null) { "Backup server import metadata missing" }
        }
        File(data,"managed-client").takeIf(File::exists)?.let {
            require(ClientStore(it).current()!=null || it.listFiles().orEmpty().all { f -> f.name=="user" }) { "Backup client metadata missing" }
        }
    }
    fun restore(input: InputStream,prefs: Preferences,progress: (String)->Unit) {
        check(!transaction.exists()) { "Finish pending recovery first" }
        check(transaction.mkdirs()); val stage=File(transaction,"incoming").apply { mkdirs() }
        try {
            var bytes=0L; var count=0; val names=mutableSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                val buffer=ByteArray(65536)
                while(true) {
                    interrupted(); val entry=zip.nextEntry ?: break; val name=entry.name.removeSuffix("/")
                    require(++count<=200001 && validName(name) && names.add(name)) { "Invalid/duplicate backup entry" }
                    require(name=="backup.properties" || name.startsWith("data/") && allowed(name.removePrefix("data/"))) { "Choose a full app backup, not a runtime ZIP" }
                    val dest=File(stage,name)
                    if(entry.isDirectory) check(dest.mkdirs() || dest.isDirectory) else {
                        dest.parentFile!!.mkdirs(); FileOutputStream(dest).use { out ->
                            var entryBytes=0L
                            while(true) { interrupted(); val n=zip.read(buffer); if(n<0) break
                                bytes+=n; entryBytes+=n; require(bytes<=limit) { "Backup exceeds size limit" }
                                if(name=="backup.properties") require(entryBytes<=64L*1024*1024)
                                if(name=="data/preferences.properties") require(entryBytes<=1024*1024)
                                check(files.usableSpace>reserve) { "Free more internal storage; existing data retained" }
                                out.write(buffer,0,n)
                            }; out.fd.sync()
                        }
                    }
                    zip.closeEntry(); if(count%100==0) progress("Checking backup ${bytes/1024/1024} MiB · $count entries")
                }
            }
            progress("Validating backup contents…"); validate(stage,prefs)
            write(File(transaction,"previous-preferences"),prefs.read())
            roots.filter { File(files,it).exists() }.forEach { write(File(transaction,"had-$it"),byteArrayOf(1)) }
            marker("prepared")
            // Journaled rename sequence: recovery restores every old root until committed exists.
            for(root in roots) {
                val live=File(files,root); val old=File(transaction,"old/$root"); val incoming=File(stage,"data/$root")
                if(live.exists()) move(live,old)
                marker("old-$root")
                if(incoming.exists()) move(incoming,live)
                checkpoint("new-$root")
            }
            prefs.replace(File(stage,"data/preferences.properties").readBytes()); checkpoint("new-preferences")
            marker("committed")
            finishTransaction(); progress("Restore complete. Server and client are stopped.")
        } catch(failure: Exception) {
            // Preserve interrupt status, but recovery must complete even after Cancel.
            val interrupted=Thread.interrupted()
            try { recover(prefs) } catch(recovery: Exception) { failure.addSuppressed(recovery) }
            finally { if(interrupted) Thread.currentThread().interrupt() }
            throw failure
        }
    }
    fun recover(prefs: Preferences) {
        File(files,"backup-cleanup").takeIf(File::exists)?.let { check(it.deleteRecursively()) }
        if(!transaction.exists()) return
        if(File(transaction,"prepared").isFile && !File(transaction,"committed").exists()) {
            for(root in roots.reversed()) {
                val old=File(transaction,"old/$root"); val live=File(files,root)
                if(File(transaction,"had-$root").exists()) {
                    if(old.exists()) { if(live.exists()) check(live.deleteRecursively()); move(old,live) }
                    // No old tree means it was never moved or was already recovered.
                } else if(File(transaction,"old-$root").exists()) { if(live.exists()) check(live.deleteRecursively()) }
            }
            prefs.replace(File(transaction,"previous-preferences").readBytes())
        }
        finishTransaction()
    }
}
