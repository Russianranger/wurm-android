package io.github.russianranger.wurmlauncher

/** Narrow SQLite operations, allowing the same storage logic to be tested against real SQLite on the host. */
interface WorldSettingsDatabase : java.io.Closeable {
    fun query(sql: String): List<Map<String,String?>>
    fun execute(sql: String,args: List<String>)
    fun update(sql: String,args: List<String>): Int
    fun transaction(action: ()->Unit)
}
