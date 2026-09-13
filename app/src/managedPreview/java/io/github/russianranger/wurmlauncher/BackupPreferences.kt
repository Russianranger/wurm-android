package io.github.russianranger.wurmlauncher

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.Properties

/** Preferences are portable typed values, never cached/shared_prefs XML copied underneath Android. */
class BackupPreferences(private val context: Context) : AppBackup.Preferences {
    private val groups=listOf("managed-settings","client-settings","launcher-settings")
    override fun read(): ByteArray {
        val p=Properties(); var index=0
        groups.forEach { group -> context.getSharedPreferences(group,Context.MODE_PRIVATE).all.toSortedMap().forEach { (key,value) ->
            p.setProperty("$index.group",group); p.setProperty("$index.key",key)
            p.setProperty("$index.type",when(value) { is String -> "s"; is Int -> "i"; is Long -> "l"; is Boolean -> "b"; is Float -> "f"; else -> error("Unsupported preference: $key") })
            p.setProperty("$index.value",value.toString()); index++
        } }
        p.setProperty("count",index.toString()); p.setProperty("format","1")
        return ByteArrayOutputStream().also { p.store(it,"Portable Wurm preferences") }.toByteArray()
    }
    private fun parse(bytes: ByteArray): List<Triple<String,String,Any>> {
        require(bytes.size<=1024*1024)
        val p=Properties().apply { bytes.inputStream().use { load(it) } }
        require(p.getProperty("format")=="1") { "Unsupported preferences format" }
        val count=p.getProperty("count").toInt(); require(count in 0..4096)
        val keys=mutableSetOf<Pair<String,String>>()
        return (0 until count).map { i ->
            val group=p.getProperty("$i.group"); val key=p.getProperty("$i.key")
            require(group in groups && !key.isNullOrBlank() && key.length<=256 && keys.add(group to key))
            val value=p.getProperty("$i.value") ?: error("Missing preference value")
            val typed: Any=when(p.getProperty("$i.type")) {
                "s" -> value; "i" -> value.toInt(); "l" -> value.toLong()
                "b" -> value.toBooleanStrict(); "f" -> value.toFloat().also { require(it.isFinite()) }
                else -> error("Unknown preference type")
            }
            Triple(group,key,typed)
        }
    }
    override fun validate(bytes: ByteArray) { parse(bytes) }
    override fun replace(bytes: ByteArray) {
        val values=parse(bytes)
        groups.forEach { group ->
            val edit=context.getSharedPreferences(group,Context.MODE_PRIVATE).edit().clear()
            values.filter { it.first==group }.forEach { (_,key,value) -> when(value) {
                is String -> edit.putString(key,value); is Int -> edit.putInt(key,value); is Long -> edit.putLong(key,value)
                is Boolean -> edit.putBoolean(key,value); is Float -> edit.putFloat(key,value)
            } }
            check(edit.commit()) { "Could not save restored preferences" }
        }
    }
}
