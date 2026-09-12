package io.github.russianranger.wurmlauncher

import java.io.File
import java.math.BigDecimal
import java.util.Properties

/** Original catalog from the inspected server property sheet; no game code or defaults copied. */
object WorldSettings {
    data class Option(val column: String, val label: String, val help: String,
                      val kind: String = "float", val min: String = "0", val max: String? = null,
                      val scale: Long = 1, val appMinimum: Boolean = false) {
        val boolean get() = kind == "boolean"
        val range get() = if (boolean) "On / Off" else
            (if (max == null) "$min or higher" else "$min–$max") +
                (if (kind in listOf("int", "long")) "; whole numbers" else "") +
                if (appMinimum) " (app safety minimum)" else ""
        fun display(raw: String): String = if (scale == 1L) raw else
            BigDecimal(raw).divide(BigDecimal(scale),java.math.MathContext.DECIMAL64).stripTrailingZeros().toPlainString()
        fun value(text: String): String {
            require(text.length in 1..80) { "$label: enter $range." }
            val n = text.trim().toBigDecimalOrNull() ?: error("$label: enter a finite number.")
            require(n >= BigDecimal(min) && (max == null || n <= BigDecimal(max))) { "$label: enter $range." }
            if (boolean) require(n == BigDecimal.ZERO || n == BigDecimal.ONE) { "$label: choose On or Off." }
            if (kind in listOf("int", "long", "boolean")) require(n.stripTrailingZeros().scale() <= 0) { "$label: enter a whole number." }
            val product = n.multiply(BigDecimal(scale))
            val stored = if (kind == "hours") product.setScale(0,java.math.RoundingMode.HALF_UP) else product
            when (kind) {
                "int", "boolean" -> stored.intValueExact()
                "long", "hours" -> stored.longValueExact()
                else -> require(stored.toFloat().isFinite()) { "$label: value exceeds the server's number format." }
            }
            return stored.stripTrailingZeros().toPlainString()
        }
    }
    val options = listOf(
        Option("SKILLGAINRATE", "Skill gain multiplier", "Higher values increase skill gain.", min="0.01"),
        Option("ACTIONTIMER", "Action speed multiplier", "Higher values shorten standard action times.", min="0.01"),
        Option("SKILLBASICSTART", "Starting characteristics", "New characters only; existing skills are unchanged.", min="1", max="100"),
        Option("SKILLMINDLOGICSTART", "Starting Mind Logic", "New characters only.", min="1", max="100"),
        Option("SKILLBODYCONTROLSTART", "Starting Body Control", "New characters only.", min="1", max="100"),
        Option("SKILLFIGHTINGSTART", "Starting fighting skill", "New characters only.", min="1", max="100"),
        Option("SKILLOVERALLSTART", "Starting other skills", "New characters only.", min="1", max="100"),
        Option("FIELDGROWTH", "Field growth interval (hours)", "Time between field growth checks; lower is faster. Exact milliseconds are retained.", "hours", "0.01", scale=3600000),
        Option("TREEGROWTH", "Tree spread odds", "A 1 in N chance of new trees or mushrooms. Zero disables spread.", "int"),
        Option("BREEDING", "Breeding speed modifier", "Higher is faster. Positive whole numbers avoid a zero divisor.", "long", "1", appMinimum=true),
        Option("MAXCREATURES", "Maximum creatures", "Higher populations can increase server load.", "int", "0", appMinimum=true),
        Option("PERCENT_AGG_CREATURES", "Aggressive creatures (%)", "Server target percentage. Its editor defines a minimum of zero.", min="0"),
        Option("TUNNELING", "Minimum mining hits", "Hits before a mine wall opens.", "int", "1", appMinimum=true),
        Option("UPKEEP", "Settlement upkeep", "Require upkeep payments for settlements.", "boolean", max="1"),
        Option("FREEDEEDS", "Free settlement founding", "Remove settlement founding costs.", "boolean", max="1"),
        Option("RANDOMSPAWNS", "Random spawn locations", "Use the server's random spawning option.", "boolean", max="1")
    )
    /** Same selected-world INI and DB_HOST-relative path used by ServerDirInfo/SqliteConnectionFactory. */
    fun database(runtime: File, world: String): File {
        require(world.isNotBlank() && world !in listOf(".", "..") && world.none { it == '/' || it == '\\' || it.isISOControl() })
        val root=runtime.canonicalFile
        val ini=File(root,"$world/wurm.ini").canonicalFile
        require(ini.toPath().startsWith(root.toPath()) && ini.isFile && ini.length() <= 65536) { "Selected world's wurm.ini is unavailable." }
        val p=Properties().apply { ini.inputStream().use { load(it) } }
        val host=p.getProperty("DB_HOST")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Selected world's DB_HOST is missing; no database was guessed.")
        val directory=File(host).let { if (it.isAbsolute) it else File(root,host) }
        val database=File(directory,"sqlite/wurmlogin.db").canonicalFile
        require(database.toPath().startsWith(root.toPath()) && database.isFile) { "Selected world's login database is not inside this working runtime." }
        return database
    }
}
