package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Managed import and future client entry point. Proven root UI remains MainActivity. */
class HomeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("managed-home", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var worlds: Spinner
    private lateinit var report: TextView
    private lateinit var exportButton: Button
    private var clientTab = false
    private var shown: ImportCoordinator.Snapshot? = null
    private var shownGeneration: String? = null
    private var pendingReport: String? = null
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientTab = intent.getBooleanExtra("clientOnly", false) || (savedInstanceState?.getBoolean("clientTab") ?: false)
        pendingReport = savedInstanceState?.getString("pendingReport")
        showTab()
    }

    private fun showTab() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        page.addView(TextView(this).apply { text = "Wurm Server"; textSize = 24f; setTypeface(null, Typeface.BOLD) })
        val tabs = LinearLayout(this)
        listOf("Server", "Client").forEachIndexed { index, name ->
            tabs.addView(Button(this).apply {
                text = if (clientTab == (index == 1)) "$name •" else name
                setOnClickListener {
                    if (index == 0 && intent.getBooleanExtra("clientOnly", false)) finish()
                    else { clientTab = index == 1; showTab() }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        page.addView(tabs)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        if (clientTab) clientContent() else serverContent()
    }

    private fun serverContent() {
        label("Import preview · Android ARM64", 18f)
        label("Import your legally obtained, prepared server runtime as a ZIP. No root or Termux is needed for import. Standalone server execution is the next runtime milestone.")
        label("Use a copy of a stopped runtime with the existing SQLite fixes. Keep the original ZIP as your backup. Re-import replaces the app's previous copy.", 13f)
        importButton = button("Import Server ZIP") {
            AlertDialog.Builder(this).setTitle("Import prepared runtime")
                .setMessage("Select a complete ZIP of your stopped POC runtime from Downloads. Include server.jar, common.jar, lib/, poc-lib/ and your world directories. Keep this app open during copying; a successful import replaces the previous app copy.")
                .setPositiveButton("Choose ZIP") { _, _ -> chooseZip(IMPORT_SERVER) }
                .setNegativeButton("Cancel", null).show()
        }
        status = label("", 15f)
        label("Managed world selection")
        worlds = Spinner(this)
        content.addView(worlds)
        label("World folders are candidates until Wurm validates them. Selection is saved for future managed startup; it does not change the rooted launcher's Adventure world.", 12f)
        val actions = LinearLayout(this)
        listOf("Start", "Stop", "Restart").forEach { name ->
            actions.addView(Button(this).apply { text = "$name Server"; isEnabled = false }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(actions)
        label("Stopped · No embedded Java runtime in this preview. Server controls above are unavailable.", 13f)
        button("Open rooted POC controls and live logs") { startActivity(Intent(this, MainActivity::class.java)) }
        exportButton = button("Export import report") {
            pendingReport = report.text.toString()
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "wurm-import-report.txt")
            }, EXPORT_REPORT)
        }
        report = label("", 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        shown = null
        shownGeneration = null
        render()
    }

    private fun render() {
        if (clientTab) return
        val state = ImportCoordinator.snapshot(this)
        if (state == shown) return
        shown = state
        status.text = state.message
        importButton.isEnabled = !state.busy
        worlds.isEnabled = !state.busy && state.installed != null
        exportButton.isEnabled = !state.busy && state.installed != null
        val installed = state.installed
        if (installed == null) {
            worlds.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Import a runtime first"))
            report.text = "No imported runtime. No server process launched."
            return
        }
        if (shownGeneration != installed.generation) {
            shownGeneration = installed.generation
            worlds.onItemSelectedListener = null
            worlds.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, installed.worlds)
            worlds.setSelection(installed.worlds.indexOf(selectedWorld(installed)).coerceAtLeast(0))
            worlds.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putString("worldGeneration", installed.generation)
                        .putString("world", installed.worlds[position]).apply()
                    updateReport(installed)
                }
            }
        }
        updateReport(installed)
    }

    private fun selectedWorld(installed: ManagedRuntimeStore.Installed): String {
        val saved = if (prefs.getString("worldGeneration", null) == installed.generation) prefs.getString("world", null) else null
        return saved?.takeIf { it in installed.worlds } ?: installed.worlds.firstOrNull { it == "Adventure" } ?: installed.worlds.first()
    }

    private fun updateReport(installed: ManagedRuntimeStore.Installed) {
        report.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n\n" +
            installed.report(selectedWorld(installed))
    }

    private fun clientContent() {
        label("Client groundwork", 18f)
        label("Choose your own client ZIP and save connection settings for later development. This preview keeps a document reference only; it does not extract or run the Wurm client.")
        label("Selected ZIP: ${prefs.getString("clientName", "None")}")
        button("Import Client ZIP") { chooseZip(IMPORT_CLIENT) }
        button("Start Client") {}.isEnabled = false
        button("Settings") { clientSettings() }
        label("Client runtime and graphics support are not implemented. Start remains unavailable.")
    }

    private fun clientSettings() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        form.addView(TextView(this).apply { text = "Saved for future client support; no connection is attempted." })
        form.addView(TextView(this).apply { text = "Server host" })
        val host = EditText(this).apply { isSingleLine = true; setText(prefs.getString("clientHost", "127.0.0.1")) }
        form.addView(host)
        form.addView(TextView(this).apply { text = "TCP port" })
        val port = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("clientPort", 3724).toString()) }
        form.addView(port)
        val dialog = AlertDialog.Builder(this).setTitle("Client settings").setView(form)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hostText = host.text.toString().trim()
                val portValue = port.text.toString().toIntOrNull()
                if (hostText.isEmpty() || hostText.length > 253 || hostText.any { it.isWhitespace() || it.isISOControl() } ||
                    portValue == null || portValue !in 1..65535) {
                    toast("Enter a host and a TCP port from 1 to 65535.")
                } else {
                    prefs.edit().putString("clientHost", hostText).putInt("clientPort", portValue).apply()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun chooseZip(request: Int) {
        // Some providers identify ZIPs as application/octet-stream. Validation is by contents.
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, request)
    }

    @Deprecated("Platform Activity API avoids adding an AndroidX dependency for this small app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) { if (requestCode == EXPORT_REPORT) pendingReport = null; return }
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                IMPORT_SERVER -> { ImportCoordinator.start(this, uri); shown = null; render() }
                IMPORT_CLIENT -> {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val oldUri = prefs.getString("clientUri", null)
                    var name = "Selected client ZIP"
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) name = it.getString(0).take(200)
                    }
                    prefs.edit().putString("clientUri", uri.toString()).putString("clientName", name).apply()
                    if (oldUri != null && oldUri != uri.toString()) runCatching {
                        contentResolver.releasePersistableUriPermission(Uri.parse(oldUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    showTab()
                }
                EXPORT_REPORT -> {
                    val text = pendingReport ?: return toast("Report was interrupted. Export it again.")
                    pendingReport = null
                    val app = applicationContext
                    Thread({
                        val error = runCatching {
                            requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
                        }.exceptionOrNull()
                        handler.post { toast(if (error == null) "Report saved." else "Export failed: ${error.message}") }
                    }, "wurm-report-export").start()
                }
            }
        } catch (e: Exception) { toast(e.message ?: "Document could not be opened.") }
    }

    private fun label(value: String, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setPadding(0, dp(6), 0, dp(6))
    }.also { content.addView(it) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; setOnClickListener { action() } }.also { content.addView(it) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("clientTab", clientTab)
        outState.putString("pendingReport", pendingReport)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val IMPORT_SERVER = 20
        private const val IMPORT_CLIENT = 21
        private const val EXPORT_REPORT = 22
    }
}
