package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.widget.*
import java.io.File

/** Mod-specific tools remain here during the test; general runtime reports stay in Diagnostics. */
class ModsPage(private val activity: Activity, private val chooseZip: (String) -> Unit) {
    val view=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(20,12,20,12) }
    private val app=activity.applicationContext
    private val panels=listOf(Panel("server"),Panel("client"))
    init {
        view.addView(TextView(activity).apply {
            text="Mod launcher test\nImport your runtime first, then its loader and mod ZIPs. Imported mods start off. Stop the affected runtime before changing its mods."
            textSize=18f
        },0)
    }
    private inner class Panel(val side: String) {
        val status=TextView(activity)
        val mods=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val controls=mutableListOf<Button>()
        val switches=mutableListOf<Switch>()
        var revision=""
        var loading=false
        init {
            view.addView(TextView(activity).apply { text=if(side=="server") "Server mods" else "Client mods"; textSize=22f; setPadding(0,28,0,8) })
            view.addView(TextView(activity).apply { text=if(side=="server")
                "Install Ago server-modlauncher-0.47.zip once, then import individual mod ZIPs. Loader installation does not enable bundled mods."
                else "Install Ago client-modlauncher-0.15.zip, then livemap-1.8.zip. The client loader can run with all mods off for an initial check. Start the game from Client; mod changes apply on its next start." })
            fun button(title: String, action: ()->Unit) {
                controls += Button(activity).apply { text=title; setOnClickListener { action() }; view.addView(this) }
            }
            button("Import $side loader / mod ZIP") { chooseZip(side) }
            button("Check ${side} mod files / dependencies") { operate(side) { store ->
                val entries=store.validate()
                "${side.replaceFirstChar { it.uppercase() }} mod check passed: ${entries.size} imported, ${entries.count { it.enabled }} enabled.".also { showReport(it+"\n\n"+store.report()) }
            } }
            button("View ${side} mod manifest") { operate(side) { store -> showReport(store.report()); "Mod manifest opened." } }
            view.addView(status); view.addView(mods)
        }
        fun render() {
            val server=ManagedSession.snapshot(false); val client=ClientSession.snapshot()
            val busy=if(side=="server") server.busy else client.busy
            val detail=if(side=="server") "${server.phase}: ${server.detail}" else "${client.phase}: ${client.detail}"
            if(status.text.toString()!=detail) status.text=detail
            controls.forEach { it.isEnabled=!busy }; switches.forEach { it.isEnabled=!busy }
            if(busy || loading) return
            val root=runCatching { if(side=="server") ManagedSession.workspace(app).working() else ClientSession.store(app).current()?.root }.getOrNull()
            val key="${root?.absolutePath}:${root?.let { File(it,"android-mods/manifest.properties").lastModified() }}:$detail"
            if(key==revision) return
            revision=key; loading=true
            Thread({
                val result=runCatching { root?.let { ModStore(it,side) }?.let { Triple(it.entries(),it.loaderInstalled(),it.clientLoaderEnabled()) }
                    ?: Triple(emptyList(),false,false) }
                activity.runOnUiThread {
                    loading=false; mods.removeAllViews(); switches.clear()
                    result.fold({ (entries,loaderInstalled,loaderEnabled) ->
                        if(side=="client" && loaderInstalled) {
                            switches += Switch(activity).apply {
                                text="Use client mod loader"; isChecked=loaderEnabled
                                setOnCheckedChangeListener { _, enabled ->
                                    setOnCheckedChangeListener(null); isChecked=loaderEnabled; isEnabled=false
                                    operate(side) { store -> store.setClientLoaderEnabled(enabled)
                                        "Client loader ${if(enabled) "enabled" else "disabled"} for next client start." }
                                    revision=""
                                }
                                mods.addView(this)
                            }
                        }
                        if(entries.isEmpty()) mods.addView(TextView(activity).apply { text="No mods imported." })
                        entries.forEach { e ->
                            switches += Switch(activity).apply {
                                text="${e.name} (${e.version})"
                                isChecked=e.enabled
                                setOnCheckedChangeListener { _, enabled ->
                                    // The asynchronous operation owns the authoritative manifest state.
                                    setOnCheckedChangeListener(null); isChecked=e.enabled; isEnabled=false
                                    operate(side) { store -> store.toggle(e.name,enabled); "${e.name}: ${if(enabled) "enabled" else "disabled"} for next $side start." }
                                    revision=""
                                }
                                mods.addView(this)
                            }
                        }
                    },{ error -> mods.addView(TextView(activity).apply { text="Mod list unavailable: ${error.message}" }) })
                    // Reset listeners through a fresh render after each operation, including failures.
                    switches.forEach { it.isEnabled=!(if(side=="server") ManagedSession.snapshot(false).busy else ClientSession.snapshot().busy) }
                }
            },"wurm-mod-list").start()
        }
    }
    private fun showReport(text: String) { activity.runOnUiThread {
        val body=TextView(activity).apply { this.text=text; setTextIsSelectable(true); setPadding(24,12,24,12) }
        AlertDialog.Builder(activity).setTitle("Mods").setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Close",null).show()
    } }
    private fun operate(side: String, action: (ModStore)->String) {
        val accepted=if(side=="client") ClientSession.mutateMods(app,action) else ManagedSession.mutate(app,"Server mods") { workspace ->
            check(!workspace.recoveryRequired.exists()) { "Restore the server checkpoint before changing mods." }
            val root=requireNotNull(workspace.working()) { "Import the server runtime first" }
            val message=action(ModStore(root,"server")); ManagedSession.log("[mods] $message")
            activity.runOnUiThread { Toast.makeText(activity,message,Toast.LENGTH_LONG).show() }
        }
        if(!accepted) Toast.makeText(activity,"Stop the affected runtime before changing mods.",Toast.LENGTH_LONG).show()
        panels.forEach { it.revision="" }
    }
    fun importZip(side: String, uri: Uri) { operate(side) { store ->
        requireNotNull(app.contentResolver.openInputStream(uri)) { "Could not open mod ZIP" }.use { store.importZip(it) }
    } }
    fun render() { panels.forEach { it.render() } }
}
