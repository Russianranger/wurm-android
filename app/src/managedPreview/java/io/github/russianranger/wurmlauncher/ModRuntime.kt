package io.github.russianranger.wurmlauncher

import android.content.Context
import java.io.File

object ModRuntime {
    const val JAVASSIST_SHA="eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf"
    fun prepareServer(context: Context, runtime: File, session: File): List<String> {
        val store=ModStore(runtime,"server")
        val entries=store.validate().filter { it.enabled }
        ManagedSession.log("[mods] SERVER_SELECTION ${entries.joinToString { "${it.name}@${it.version}" }.ifEmpty { "none; baseline startup" }}")
        if(entries.isEmpty()) return emptyList()
        val bytecode=File(session,"mod-javassist.jar")
        context.assets.open("mod-javassist.jar").use { input->bytecode.outputStream().use { input.copyTo(it) } }
        check(ProbeInputs.sha256(bytecode)==JAVASSIST_SHA) { "Mod bytecode dependency mismatch" }
        return listOf(store.loader.absolutePath,bytecode.absolutePath)
    }
}
