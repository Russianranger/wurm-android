package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** Call only inside ManagedSession.mutate/workspace.exclusive, with the server stopped. */
class WorldSettingsStore(private val open: (File,Boolean)->WorldSettingsDatabase, private val log: (String)->Unit = {}) {
    data class Snapshot(val runtime: File, val world: String, val database: File, val server: Long,
                        val values: Map<String,String>)
    private fun row(db: WorldSettingsDatabase): Pair<Long,Map<String,String>> {
        val columns=db.query("PRAGMA table_info(SERVERS)").map { it.getValue("name")!!.uppercase() }.toSet()
        check(columns.containsAll(listOf("SERVER","LOCAL"))) { "This database has no supported server settings table." }
        val available=WorldSettings.options.filter { it.column in columns }
        check(available.isNotEmpty()) { "No supported gameplay settings in this database." }
        val query="SELECT SERVER,"+available.joinToString(",") { it.column }+" FROM SERVERS WHERE LOCAL=1 LIMIT 2"
        val rows=db.query(query)
        check(rows.size==1) { "Expected exactly one local server row. Start and normally stop this world once, or check its configuration." }
        val row=rows.single()
        return row.getValue("SERVER")!!.toLong() to available.associate { option ->
            option.column to requireNotNull(row[option.column]) { "${option.label} is null; editing is unavailable." }
        }
    }

    fun read(runtime: File, world: String): Snapshot {
        val file=WorldSettings.database(runtime,world)
        return open(file,true).use { db ->
            val (id, values)=row(db)
            Snapshot(runtime.canonicalFile,world,file,id,values)
        }
    }
    fun save(before: Snapshot, edits: Map<String,String>): Int {
        check(WorldSettings.database(before.runtime,before.world) == before.database) { "World configuration changed. Reopen settings." }
        val changed=linkedMapOf<String,String>()
        edits.forEach { (column,text) ->
            val option=WorldSettings.options.single { it.column == column }
            val previous=requireNotNull(before.values[column])
            // Preserve imported values outside the editor's range when the user did not edit them.
            if (text != option.display(previous)) {
                val value=option.value(text)
                if (value.toBigDecimal().compareTo(previous.toBigDecimal()) != 0) changed[column]=value
            }
        }
        if (changed.isEmpty()) return 0
        open(before.database,false).use { db ->
            check(row(db) == (before.server to before.values)) { "Server settings changed. Reopen the editor before saving." }
            // SQLite itself captures all committed pages, including WAL. Never copy just the main DB file.
            val backups=File(before.runtime,"android-settings-backups").apply { check(isDirectory || mkdirs()) }
            check(backups.usableSpace > before.database.length()*2 + 32L*1024*1024) { "Free space is needed for the settings backup." }
            val pending=File(backups,"${UUID.randomUUID()}.pending")
            val backup=File(backups,pending.name.removeSuffix(".pending")+".db")
            try {
                db.execute("VACUUM INTO ?",listOf(pending.path))
                RandomAccessFile(pending,"rw").use { it.fd.sync() }
                java.nio.file.Files.move(pending.toPath(),backup.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                db.transaction {
                    check(row(db) == (before.server to before.values)) { "Server settings changed during backup; save cancelled." }
                    val sql="UPDATE SERVERS SET "+changed.keys.joinToString(",") { "$it=?" }+" WHERE SERVER=? AND LOCAL=1"
                    check(db.update(sql,changed.values.toList()+before.server.toString()) == 1) { "Local server row changed." }
                }

                log("[world-settings] SAVED world=${before.world} server=${before.server} fields=${changed.keys.joinToString(",")} backup=${backup.name}; applies on next start")
            } finally { pending.delete() }
        }
        return changed.size
    }
}
