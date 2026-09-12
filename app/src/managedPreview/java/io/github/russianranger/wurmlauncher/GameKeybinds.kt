package io.github.russianranger.wurmlauncher

import java.util.Locale
import java.util.Properties

object GameKeybinds {
    data class Action(val index: Int,val command: String,val label: String,val category: String,val keys: String)
    data class Catalog(val revision: String,val editable: Boolean,val choices: List<String>,val actions: List<Action>,val notice: String)
    fun parse(p: Properties, request: String): Catalog {
        require(p.getProperty("request")==request) { "Waiting for this request's response" }
        check(p.getProperty("status")=="ok") { p.getProperty("notice","Keybindings unavailable") }
        val count=p.getProperty("count").toInt()
        require(count in 1..1024)
        val revision=p.getProperty("revision"); require(revision.matches(Regex("[0-9a-f]{64}")))
        val choices=p.getProperty("keyChoices").split(',').also { keys -> require(keys.isNotEmpty() && keys.all { it.matches(Regex("[A-Z0-9_]+")) }) }
        return Catalog(revision,p.getProperty("editable")=="true",choices,(0 until count).map { i ->
            Action(i,requireNotNull(p.getProperty("item.$i.action")),requireNotNull(p.getProperty("item.$i.label")),
                requireNotNull(p.getProperty("item.$i.category")),p.getProperty("item.$i.keys",""))
        },p.getProperty("notice",""))
    }
    fun command(request: String,catalog: Catalog,action: Action,text: String): String {
        require(request.matches(Regex("[a-f0-9-]{36}")) && catalog.actions.contains(action))
        check(catalog.editable) { "This binding file cannot be edited safely." }
        val keys=if (text.isBlank()) emptyList() else text.split(',').map { it.trim().uppercase(Locale.ROOT) }
        require(keys.size<=8) { "Use at most eight keys per action." }
        val normalized=keys.map { key ->
            val parts=key.split('+'); val mods=parts.dropLast(1)
            require(parts.last() in catalog.choices && mods.all { it in listOf("CTRL","ALT","SHIFT") } && mods.distinct().size==mods.size) { "Choose a listed key, optionally with CTRL+, ALT+ or SHIFT+." }
            (listOf("ALT","CTRL","SHIFT").filter { it in mods }+parts.last()).joinToString("+")
        }
        require(normalized.distinct().size==normalized.size) { "Remove duplicate keys." }
        return "BINDS $request SET ${catalog.revision} ${action.index} ${normalized.joinToString(",").ifEmpty { "-" }}".also {
            require(it.length<=520) { "Key list is too long." }
        }
    }
}
