package io.github.russianranger.wurmlauncher

import android.database.sqlite.SQLiteDatabase
import java.io.File

class AndroidWorldSettingsDatabase(file: File,readOnly: Boolean) : WorldSettingsDatabase {
    private val db=SQLiteDatabase.openDatabase(file.path,null,if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE)
    override fun query(sql: String): List<Map<String,String?>> = db.rawQuery(sql,null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.columnNames.mapIndexed { i,name -> name to if (cursor.isNull(i)) null else cursor.getString(i) }.toMap()) }
    }
    override fun execute(sql: String,args: List<String>) { db.execSQL(sql,args.toTypedArray()) }
    override fun update(sql: String,args: List<String>): Int = db.compileStatement(sql).use { statement ->
        args.forEachIndexed { i,value -> statement.bindString(i+1,value) }
        statement.executeUpdateDelete()
    }
    override fun transaction(action: ()->Unit) {
        db.beginTransaction()
        try { action(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    override fun close() { db.close() }
}
