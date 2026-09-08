package io.github.russianranger.wurmlauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
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
            status.text = "${state.phase}: ${state.detail}"
            actions.forEach { it.isEnabled = !state.busy }
            logs.text = ClientSession.recent()
            imported.text = runCatching { ClientSession.store(this@ClientActivity).current()?.let { "Client import: ${it.id}\n${it.jars.size} JARs; retained in private storage" } ?: "No client imported" }.getOrElse { "Import metadata error: ${it.message}" }
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
        label("Wurm Client · 0.7.0").textSize = 24f
        label("This preview imports your client and attempts its bootstrap and LWJGL initialization. It does not yet render Wurm or complete login. Target: 127.0.0.1:3724.")
        button("Server tab") { finish() }
        imported = label(""); status = label("")
        button("Import Client ZIP", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 10)
        }
        button("Start Client", true) { launch("start") }
        button("Start Local Game", true) { launch("local") }
        button("Stop Client") { startService(Intent(this, ClientService::class.java).setAction("stop")) }
        button("Controller Settings") { startActivity(Intent(this, ControllerSettingsActivity::class.java)) }
        button("Controller / JVM Input Test") { startActivity(Intent(this, ControllerTestActivity::class.java)) }
        button("Export Client Report") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "wurm-client-report.txt"), 11)
        }
        label("Client logs (latest 80 lines; export includes persisted history)")
        logs = label("").apply { textSize = 12f; setTextIsSelectable(true) }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
    }
    private fun launch(mode: String) { startForegroundService(Intent(this, ClientService::class.java).setAction(mode)) }
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
