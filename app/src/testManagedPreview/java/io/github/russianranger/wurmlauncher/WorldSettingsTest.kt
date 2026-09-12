package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager

class WorldSettingsTest {
    private class Db(file: File,readOnly: Boolean=false) : WorldSettingsDatabase {
        private val db=DriverManager.getConnection("jdbc:sqlite:"+file.path)
        init { if (readOnly) db.createStatement().use { it.execute("PRAGMA query_only=ON") } }
        override fun query(sql: String)=db.createStatement().use { statement -> statement.executeQuery(sql).use { rows ->
            buildList { while (rows.next()) add((1..rows.metaData.columnCount).associate { rows.metaData.getColumnName(it) to rows.getString(it) }) }
        } }
        override fun execute(sql: String,args: List<String>) { db.prepareStatement(sql).use { s -> args.forEachIndexed { i,v -> s.setString(i+1,v) }; s.execute() } }
        override fun update(sql: String,args: List<String>): Int = db.prepareStatement(sql).use { s -> args.forEachIndexed { i,v -> s.setString(i+1,v) }; s.executeUpdate() }
        override fun transaction(action: ()->Unit) {
            db.autoCommit=false
            try { action(); db.commit() } catch (failure: Throwable) { db.rollback(); throw failure } finally { db.autoCommit=true }
        }
        override fun close() { db.close() }
    }
    private fun fixture(test: (File,File,WorldSettingsStore)->Unit) {
        val root=Files.createTempDirectory("world-settings").toFile()
        try {
            File(root,"Adventure").mkdir(); File(root,"Adventure/wurm.ini").writeText("DB_HOST=localhost\n")
            File(root,"localhost/sqlite").mkdirs(); val file=File(root,"localhost/sqlite/wurmlogin.db")
            Db(file).use { db ->
                db.execute("CREATE TABLE SERVERS(SERVER INTEGER PRIMARY KEY, LOCAL INTEGER, SKILLGAINRATE FLOAT, ACTIONTIMER FLOAT, FIELDGROWTH BIGINT, NAME TEXT)",emptyList())
                db.execute("INSERT INTO SERVERS VALUES(1,1,1,1,28800001,'local'),(2,0,3,3,7200000,'neighbor')",emptyList())
            }
            test(root,file,WorldSettingsStore(::Db))
        } finally { root.deleteRecursively() }
    }
    @Test fun typedBoundsRejectNonfiniteFractionsOverflowAndUnknownWorld() {
        fun option(key: String)=WorldSettings.options.single { it.column==key }
        for (bad in listOf("0","-1","NaN","Infinity","1e999999")) assertTrue(runCatching { option("SKILLGAINRATE").value(bad) }.isFailure)
        assertEquals("0.01",option("SKILLGAINRATE").value("0.01"))
        assertEquals("100",option("SKILLBASICSTART").value("100"))
        assertTrue(runCatching { option("SKILLBASICSTART").value("100.01") }.isFailure)
        assertTrue(runCatching { option("TREEGROWTH").value("1.5") }.isFailure)
        assertTrue(runCatching { option("TREEGROWTH").value("2147483648") }.isFailure)
        assertTrue(runCatching { option("BREEDING").value("0") }.isFailure)
        assertTrue(runCatching { option("BREEDING").value("9223372036854775808") }.isFailure)
        assertTrue(runCatching { option("UPKEEP").value("2") }.isFailure)
        assertEquals("36000",option("FIELDGROWTH").value(".01"))
        assertEquals("4500000",option("FIELDGROWTH").value("1.25"))
        assertTrue(option("FIELDGROWTH").display("28800001").startsWith("8.00000027"))
        fixture { root,_,_ ->
            assertTrue(runCatching { WorldSettings.database(root,"../Adventure") }.isFailure)
            File(root,"Adventure/wurm.ini").writeText("DB_HOST=missing\n")
            assertTrue(runCatching { WorldSettings.database(root,"Adventure") }.isFailure)
        }
    }
    @Test fun sqliteSavePreservesUnchangedColumnsNeighborAndExactMillisecondsWithBackup() = fixture { root,file,store ->
        val before=store.read(root,"Adventure")
        val edits=before.values.mapValues { (key,value) -> WorldSettings.options.single { it.column==key }.display(value) }.toMutableMap()
        assertEquals(0,store.save(before,edits)); assertFalse(File(root,"android-settings-backups").exists())
        edits["SKILLGAINRATE"]="2.5"; assertEquals(1,store.save(before,edits))
        Db(file).use { db ->
            assertEquals(listOf("2.5","3.0"),db.query("SELECT SKILLGAINRATE FROM SERVERS ORDER BY SERVER").map { it["SKILLGAINRATE"] })
            assertEquals("28800001",db.query("SELECT FIELDGROWTH FROM SERVERS WHERE SERVER=1").single()["FIELDGROWTH"])
            assertEquals("local",db.query("SELECT NAME FROM SERVERS WHERE SERVER=1").single()["NAME"])
        }
        val backup=File(root,"android-settings-backups").listFiles()!!.single()
        Db(backup).use { db -> assertEquals("1.0",db.query("SELECT SKILLGAINRATE FROM SERVERS WHERE SERVER=1").single()["SKILLGAINRATE"]) }
        assertEquals("2.5",store.read(root,"Adventure").values["SKILLGAINRATE"])
    }
    @Test fun staleSnapshotAndTransactionFailureLeaveDatabaseUnchanged() = fixture { root,file,store ->
        val before=store.read(root,"Adventure")
        Db(file).use { it.execute("UPDATE SERVERS SET ACTIONTIMER=4 WHERE SERVER=1",emptyList()) }
        assertTrue(runCatching { store.save(before,mapOf("SKILLGAINRATE" to "9")) }.isFailure)
        val fresh=store.read(root,"Adventure")
        Db(file).use { it.execute("CREATE TRIGGER reject_skill BEFORE UPDATE OF SKILLGAINRATE ON SERVERS BEGIN SELECT RAISE(ABORT,'fixture rejection'); END",emptyList()) }
        assertTrue(runCatching { store.save(fresh,mapOf("SKILLGAINRATE" to "9","ACTIONTIMER" to "2")) }.isFailure)
        assertEquals(fresh,store.read(root,"Adventure"))
    }
    @Test fun walBackupIncludesCommittedDataAndAmbiguousRowsAreRejected() = fixture { root,file,store ->
        Db(file).use { held ->
            held.query("PRAGMA journal_mode=WAL")
            held.execute("UPDATE SERVERS SET NAME='wal value' WHERE SERVER=1",emptyList())
            store.save(store.read(root,"Adventure"),mapOf("ACTIONTIMER" to "2"))
            val backup=File(root,"android-settings-backups").listFiles()!!.single()
            Db(backup).use { assertEquals("wal value",it.query("SELECT NAME FROM SERVERS WHERE SERVER=1").single()["NAME"]) }
            held.execute("UPDATE SERVERS SET LOCAL=1 WHERE SERVER=2",emptyList())
            assertTrue(runCatching { store.read(root,"Adventure") }.isFailure)
        }
    }
}
