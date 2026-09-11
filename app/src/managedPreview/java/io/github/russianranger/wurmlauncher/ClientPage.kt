package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.*

/** Basic client controls. Services retain session ownership while tabs change. */
class ClientPage(private val activity: Activity, private val importClient: () -> Unit) {
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20,12,20,12) }
    private val actions = mutableListOf<Button>()
    private val imported = label("")
    private val status = label("")
    private val player = label("")
    private val resume: Button
    private val stop: Button
    private fun label(text: String) = TextView(activity).apply { this.text=text; setPadding(0,8,0,8); view.addView(this) }
    private fun button(text: String, idle: Boolean = false, action: () -> Unit) = Button(activity).apply {
        this.text=text; setOnClickListener { action() }; view.addView(this); if (idle) actions.add(this)
    }
    init {
        button("Import Client ZIP",true,importClient)
        button("Change Player Name",true) { editPlayerName(player) }
        button("Graphics Settings") { GraphicsSettingsDialog.show(activity,ClientSession.gameActive() && ClientSession.inputReady()) }
        button("Controller Settings") { activity.startActivity(Intent(activity,ControllerSettingsActivity::class.java)) }
        button("Start Local Game",true) { launch("local") }
        button("Start Client",true) { launch("start") }
        resume=button("Return to Game") {
            activity.startActivity(Intent(activity,GraphicsTestActivity::class.java).putExtra("mode","start"))
        }
        stop=button("Stop Client") { activity.startService(Intent(activity,ClientService::class.java).setAction("stop")) }
        label("Start Local Game starts your selected server and connects the client. Tests, reports and logs are in Diagnostics.")
    }
    fun render() {
        val state=ClientSession.snapshot()
        status.updateText(state.phase + if (state.phase=="Error") ": ${state.detail}" else "")
        actions.forEach { it.isEnabled=!state.busy }
        resume.isEnabled=ClientSession.gameActive()
        stop.isEnabled=state.busy
        player.updateText("Local player: ${activity.getSharedPreferences("client-settings",Activity.MODE_PRIVATE).getString("player","Thor")}")
        imported.updateText(runCatching { ClientSession.store(activity).current()?.let { "Client imported · ${it.jars.size} JARs" } ?: "Import your client ZIP to get started." }.getOrElse { "Import metadata error: ${it.message}" })
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
