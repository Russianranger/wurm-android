package io.github.russianranger.wurmlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.inputmethod.EditorInfo
import android.widget.*

/** On-device import, real client linkage attempts and report export. */
class ClientActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var imported: TextView
    private val actions = mutableListOf<Button>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val state = ClientSession.snapshot()
            status.updateText("${state.phase}: ${state.detail}")
            actions.forEach { it.isEnabled = !state.busy }
            logs.updateText(ClientSession.recent())
            imported.updateText(runCatching { ClientSession.store(this@ClientActivity).current()?.let { "Client import: ${it.id}\n${it.jars.size} JARs; retained in private storage" } ?: "No client imported" }.getOrElse { "Import metadata error: ${it.message}" })
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ClientSession.initialize(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        setContentView(ScrollView(this).apply { addView(column) })
        fun label(text: String) = TextView(this).apply { this.text = text; column.addView(this) }
        fun button(text: String, operation: Boolean = false, action: () -> Unit) = Button(this).apply {
            this.text = text; setOnClickListener { action() }; column.addView(this); if (operation) actions.add(this)
        }
        label("Wurm Client · 0.10.23").textSize = 24f
        label("Choose your player name, then Start Local Game. Complete any character setup shown in the game. This build tests a graphics crash fix.")
        button("Server tab") { finish() }
        imported = label(""); status = label("")
        button("Import Client ZIP", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 10)
        }
        val preferences = getSharedPreferences("client-settings", MODE_PRIVATE)
        val playerName = label("Local player: ${preferences.getString("player", "Thor")}")
        button("Change Player Name", true) { editPlayerName(playerName) }
        button("Graphics Settings") { GraphicsSettingsDialog.show(this) }
        button("Start Client", true) { launch("start") }
        button("Start Local Game", true) { launch("local") }
        button("Stop Client") { startService(Intent(this, ClientService::class.java).setAction("stop")) }
        button("Controller Settings") { startActivity(Intent(this, ControllerSettingsActivity::class.java)) }
        button("Start Controller Test") { startActivity(Intent(this, ControllerTestActivity::class.java)) }
        button("LWJGL Window / Input Test") { startActivity(Intent(this, GraphicsTestActivity::class.java).putExtra("mode", "window")) }
        button("JVM Memory Test", true) { startForegroundService(Intent(this, ClientService::class.java).setAction("memory")) }
        label("Client startup retains Serial GC. The optional Memory Test compares G1 and Serial without game files; no repeat is required for this character-creation test.")
        button("JVM Graphics Test") { startActivity(Intent(this, GraphicsTestActivity::class.java)) }
        button("Export Client Report") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "wurm-client-report.txt"), 11)
        }
        label("Client logs (latest 80 lines; export includes persisted history)")
        logs = label("").apply { textSize = 12f; setTextIsSelectable(true) }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
    }
    private fun TextView.updateText(value: String) {
        if (text.toString() != value) text = value
    }
    private fun editPlayerName(label: TextView) {
        val preferences = getSharedPreferences("client-settings", MODE_PRIVATE)
        // Keep the editor outside the periodically relaid-out status page.
        val player = EditText(this).apply {
            setSingleLine(); setText(preferences.getString("player", "Thor")); selectAll()
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val dialog = AlertDialog.Builder(this).setTitle("Local player name")
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
            Toast.makeText(this, "Player name saved", Toast.LENGTH_SHORT).show()
        }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() } }
        player.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        dialog.show()
    }
    private fun launch(mode: String) {
        startForegroundService(Intent(this, ClientService::class.java).setAction(mode))
        startActivity(Intent(this, GraphicsTestActivity::class.java).putExtra("mode", mode))
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 10) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startForegroundService(Intent(this, ClientService::class.java).setAction("import").setData(uri))
        } else if (requestCode == 11) Thread({
            val result = runCatching { requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(ClientSession.report(applicationContext)) } }
            runOnUiThread { Toast.makeText(this, result.fold({ "Client report exported" }, { "Export failed: ${it.message}" }), Toast.LENGTH_LONG).show() }
        }, "wurm-client-export").start()
    }
}
