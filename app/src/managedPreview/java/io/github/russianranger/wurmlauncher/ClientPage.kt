package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.*

/** Basic client controls. Services retain session ownership while tabs change. */
class ClientPage(private val activity: Activity, private val importClient: () -> Unit) {
    val view = LauncherUi.column(activity)
    private val status = label("")
    private val player = label("")
    private val play: Button
    private val connect: Button
    private val stop: Button
    private val rename: Button
    private val importButton: Button
    private var importStamp=""
    private var hasClient=false
    private fun label(text: String)=LauncherUi.label(view,text)
    private fun button(text: String, action: () -> Unit)=LauncherUi.button(view,text,action)
    init {
        status.textSize=20f
        play=button("Play") {
            if(ClientSession.gameActive()) activity.startActivity(Intent(activity,GraphicsTestActivity::class.java).putExtra("mode","start"))
            else launch("local")
        }
        stop=button("Stop client") { activity.startService(Intent(activity,ClientService::class.java).setAction("stop")) }
        label("Play starts the selected local server when needed, then connects your character. Returning to the launcher keeps the server running.")
        val settings=LauncherUi.section(view,"Character & settings")
        rename=LauncherUi.button(settings,"Change player name") { editPlayerName(player) }
        LauncherUi.button(settings,"Graphics") { GraphicsSettingsDialog.show(activity,ClientSession.gameActive() && ClientSession.inputReady()) }
        LauncherUi.button(settings,"Game keybindings") { activity.startActivity(Intent(activity,GameKeybindsActivity::class.java)) }
        LauncherUi.button(settings,"Controller mappings") { activity.startActivity(Intent(activity,ControllerSettingsActivity::class.java)) }
        val runtime=LauncherUi.section(view,"Setup & runtime")
        LauncherUi.label(runtime,"Import the complete Wurm client ZIP. Import a prepared server ZIP on Server for local play, or restore a complete app backup.")
        importButton=LauncherUi.button(runtime,"Import client ZIP",importClient)
        connect=LauncherUi.button(runtime,"Connect to running local server") { launch("start") }
        LauncherUi.button(view,"Backups & migration") { activity.startActivity(Intent(activity,BackupActivity::class.java)) }
        LauncherUi.button(view,"Diagnostics & reports") { activity.startActivity(ManagedActivity.tabIntent(activity,ManagedActivity.DIAGNOSTICS)) }
        // Setup is visible on a fresh install; Play becomes primary after imports.
        if(!java.io.File(ClientSession.store(activity).home,"current").isFile) runtime.visibility=android.view.View.VISIBLE
    }
    fun render() {
        val state=ClientSession.snapshot()
        val current=java.io.File(ClientSession.store(activity).home,"current")
        val stamp="${current.lastModified()}:${current.length()}"
        if(stamp!=importStamp) { importStamp=stamp; hasClient=runCatching { ClientSession.store(activity).current()!=null }.getOrDefault(false) }
        val server=ManagedSession.snapshot(false)
        val workspace=ManagedSession.workspace(activity)
        val hasServer=runCatching { workspace.imports.current()!=null }.getOrDefault(false)
        val recovery=workspace.recoveryRequired.exists()
        val active=ClientSession.gameActive()
        val blocked=OperationGate.maintenance || OperationGate.recoveryError!=null
        status.updateText(when {
            blocked -> OperationGate.recoveryError ?: "Backup operation in progress"
            state.busy -> "${state.phase} · ${state.detail}"
            !hasClient -> "Setup needed · Import your client ZIP below"
            !hasServer -> "Client ready · Import a server on Server for local play"
            recovery -> "Server recovery needed · Open Server → Backups"
            state.phase=="Error" -> "Client stopped · ${state.detail}"
            else -> "Ready to play · Server: ${server.phase}"
        })
        play.text=if(active) "Resume game" else if(state.busy) "Starting…" else "Play"
        play.isEnabled=!blocked && (active || (!state.busy && hasClient && hasServer && !recovery && (!server.busy || server.phase=="Running")))
        connect.isEnabled=!blocked && !state.busy && hasClient
        rename.isEnabled=!blocked && !state.busy
        importButton.isEnabled=!blocked && !state.busy
        stop.visibility=if(state.busy) android.view.View.VISIBLE else android.view.View.GONE
        val name=activity.getSharedPreferences("client-settings",Activity.MODE_PRIVATE).getString("player","Thor")
        val world=activity.getSharedPreferences("managed-settings",Activity.MODE_PRIVATE).getString("world","Adventure")
        player.updateText("Character: $name · World: $world")
    }
    private fun TextView.updateText(value: String) { if (text.toString()!=value) text=value }
    private fun launch(mode: String) {
        activity.startForegroundService(Intent(activity,ClientService::class.java).setAction(mode))
        activity.startActivity(Intent(activity,GraphicsTestActivity::class.java).putExtra("mode",mode))
    }
    private fun editPlayerName(label: TextView) {
        val preferences = activity.getSharedPreferences("client-settings", Activity.MODE_PRIVATE)
        // Keep the editor outside the periodically relaid-out status page.
        val player = EditText(activity).apply {
            setSingleLine(); setText(preferences.getString("player", "Thor")); selectAll()
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Local player name")
            .setMessage("Use 3–20 letters/digits, starting with a letter. Your local login identity is supplied automatically.")
            .setView(player).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        fun save() {
            val name = player.text.toString().trim()
            if (!name.matches(Regex("[A-Za-z][A-Za-z0-9]{2,19}"))) {
                player.error = "Use 3–20 letters/digits, starting with a letter"
                return
            }
            preferences.edit().putString("player", name).apply()
            label.text = "Local player: $name"
            player.clearFocus()
            dialog.dismiss()
            Toast.makeText(activity, "Player name saved", Toast.LENGTH_SHORT).show()
        }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() } }
        player.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        dialog.show()
    }
}
