package io.github.russianranger.wurmlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Minimal no-root server UI; existing client document/settings scaffold is reused. */
class ManagedActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("managed-settings", MODE_PRIVATE) }
    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var worlds: Spinner
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var restart: Button
    private lateinit var force: Button
    private val idleButtons = mutableListOf<Button>()
    private var generation: String? = null
    private var knownWorlds = emptyList<String>()
    private val refresh = object : Runnable {
        override fun run() { render(); main.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12) }
        setContentView(ScrollView(this).apply { addView(page) })
        label("Wurm Server", 25f)
        label("0.10.16 · Existing runtime import fix · no root or Termux required", 13f)
        button("Client tab") { startActivity(Intent(this, ClientActivity::class.java)) }
        label("Each Start saves a before-start checkpoint and records world paths and ports. File persistence and short background operation passed on the Thor; gameplay saves still need verification.")
        idleButtons += button("Import Server ZIP") {
            AlertDialog.Builder(this).setTitle("Import prepared runtime")
                .setMessage("Choose the working runtime ZIP exported while stopped from your previous Wurm Server app, or your prepared Termux POC ZIP. Include its existing SQLite fixes. This preview keeps one original import plus a separate working copy. Allow at least 4 GiB free space for your current runtime and recovery files.")
                .setPositiveButton("Choose ZIP") { _, _ -> document(Intent.ACTION_OPEN_DOCUMENT, "*/*", "", IMPORT) }
                .setNegativeButton("Cancel", null).show()
        }
        status = label("Stopped")
        label("World")
        worlds = Spinner(this).also { page.addView(it) }
        idleButtons += button("Server Settings") { settings() }
        start = button("Start Server") {
            if (!saveWorld()) return@button
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 70)
            startForegroundService(Intent(this, ManagedServerService::class.java).setAction(ManagedServerService.START))
        }
        stop = button("Stop Server") { ManagedSession.stop() }
        restart = button("Restart Server") { ManagedSession.stop(restart = true) }
        force = button("Force Stop") {
            AlertDialog.Builder(this).setTitle("Force Stop the server?")
                .setMessage("This kills only this app's Java process. Unsaved world changes can be lost. The original import and before-start checkpoint remain available.")
                .setPositiveButton("Force Stop") { _, _ -> ManagedSession.forceStop() }.setNegativeButton("Cancel", null).show()
        }
        idleButtons += button("Export working runtime ZIP") { document(Intent.ACTION_CREATE_DOCUMENT, "application/zip", "wurm-working-runtime.zip", EXPORT_WORKING) }
        idleButtons += button("Export before-start checkpoint ZIP") { document(Intent.ACTION_CREATE_DOCUMENT, "application/zip", "wurm-before-start.zip", EXPORT_CHECKPOINT) }
        idleButtons += button("Restore before-start checkpoint") { restore(false) }
        idleButtons += button("Restore original import") { restore(true) }
        label("World and configuration", 20f)
        label("Start captures the selected GameFolder, observed database/map paths and port evidence. View or export the saved report while running or stopped. Imported settings are read-only.")
        button("View world/configuration report") {
            val text = TextView(this).apply {
                setTextIsSelectable(true); setPadding(20, 12, 20, 12)
                text = worldReport()
            }
            AlertDialog.Builder(this).setTitle("World/configuration report").setView(ScrollView(this).apply { addView(text) })
                .setPositiveButton("Close", null).show()
        }
        button("Export world/configuration report") { document(Intent.ACTION_CREATE_DOCUMENT, "text/plain", "wurm-world-report.txt", EXPORT_WORLD_REPORT) }
        label("Storage verification", 20f)
        label("Capture a baseline while stopped, run and stop the server, then check what changed. Database checks use disposable copies. These checks do not prove a particular gameplay change was saved.")
        idleButtons += button("Capture storage baseline") {
            AlertDialog.Builder(this).setTitle("Capture storage baseline?")
                .setMessage("This replaces the previous comparison baseline for this working copy and world. Export the current storage report first if you need it.")
                .setPositiveButton("Capture") { _, _ -> audit(ManagedServerService.BASELINE) }.setNegativeButton("Cancel", null).show()
        }
        idleButtons += button("Check stored data") { audit(ManagedServerService.CHECK) }
        button("View storage report") {
            val text = TextView(this).apply {
                setTextIsSelectable(true); setPadding(20, 12, 20, 12)
                text = ManagedSession.workspace(this@ManagedActivity).storageReport()
            }
            AlertDialog.Builder(this).setTitle("Storage report").setView(ScrollView(this).apply { addView(text) })
                .setPositiveButton("Close", null).show()
        }
        button("Export storage report") { document(Intent.ACTION_CREATE_DOCUMENT, "text/plain", "wurm-storage-report.txt", EXPORT_STORAGE_REPORT) }
        button("Export session report") { document(Intent.ACTION_CREATE_DOCUMENT, "text/plain", "wurm-server-report.txt", EXPORT_REPORT) }
        label("Live output (last 500 lines)")
        logs = label("", 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        render()
    }

    private fun audit(action: String) {
        if (!saveWorld()) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 70)
        startForegroundService(Intent(this, ManagedServerService::class.java).setAction(action))
    }

    private fun worldReport(): String {
        val workspace = ManagedSession.workspace(this)
        return ManagedWorldReport.read(workspace.worldReport, workspace.working(),
            worlds.selectedItem as? String ?: prefs.getString("world", "Adventure"))
    }

    private fun render() {
        val state = ManagedSession.snapshot()
        val recovery = ManagedSession.workspace(this).recoveryRequired.exists()
        status.text = "${state.phase} · ${state.detail}" + if (!state.busy && recovery)
            "\nRecovery needed: export current files/logs, then restore the checkpoint or original before another Start." else ""
        if (logs.text.toString() != state.log) logs.text = state.log
        val installed = runCatching { ManagedSession.workspace(this).imports.current() }.getOrNull()
        if (installed?.generation != generation) {
            saveWorld()
            generation = installed?.generation
            knownWorlds = installed?.worlds.orEmpty()
            worlds.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, knownWorlds)
            val selected = prefs.getString("world", "Adventure")
            worlds.setSelection(knownWorlds.indexOf(selected).coerceAtLeast(0))
        }
        idleButtons.forEach { it.isEnabled = !state.busy }
        worlds.isEnabled = !state.busy && installed != null
        start.isEnabled = !state.busy && installed != null && !recovery
        stop.isEnabled = ManagedSession.ownsServer() && state.phase != "Stopping"
        restart.isEnabled = ManagedSession.ownsServer() && state.phase == "Running"
        force.isEnabled = ManagedSession.ownsServer() && state.phase == "Stopping"
    }

    private fun saveWorld(): Boolean {
        val world = worlds.selectedItem as? String ?: return false
        if (world !in knownWorlds) return false
        prefs.edit().putString("world", world).apply()
        return true
    }
    private fun settings() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 0, 30, 0) }
        fun field(title: String, value: Int): EditText {
            form.addView(TextView(this).apply { text = title })
            return EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString()); form.addView(this) }
        }
        val heap = field("Maximum Java heap (MiB, 512–8192)", prefs.getInt("heap", 4096))
        val port = field("Expected existing Wurm TCP port (1–65535)", prefs.getInt("port", 3724))
        form.addView(TextView(this).apply { text = "Port is a readiness check. It does not edit wurm.ini or change Wurm's listening port. Imported server configuration is preserved." })
        val dialog = AlertDialog.Builder(this).setTitle("Server settings").setView(form)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val h = heap.text.toString().toIntOrNull(); val p = port.text.toString().toIntOrNull()
            if (h == null || h !in 512..8192 || p == null || p !in 1..65535) toast("Enter values in the displayed ranges.")
            else { prefs.edit().putInt("heap", h).putInt("port", p).apply(); dialog.dismiss() }
        } }
        dialog.show()
    }
    private fun restore(original: Boolean) {
        AlertDialog.Builder(this).setTitle(if (original) "Restore original import?" else "Restore before-start checkpoint?")
            .setMessage("This replaces the stopped working copy. Export it first if you need its current changes. The original import and saved checkpoint remain available.")
            .setPositiveButton("Restore") { _, _ ->
                val app = applicationContext
                if (!ManagedSession.mutate(app, "Restoring") { workspace ->
                    if (original) workspace.restoreOriginal { ManagedSession.status("Restoring", it) }
                    else workspace.restoreCheckpoint(app.assets.open("wurm-arm64-poc.jar").use { it.readBytes() }) { ManagedSession.status("Restoring", it) }
                }) toast("Stop the active operation first.")
            }.setNegativeButton("Cancel", null).show()
    }
    private fun document(action: String, mime: String, name: String, request: Int) {
        startActivityForResult(Intent(action).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            if (name.isNotEmpty()) putExtra(Intent.EXTRA_TITLE, name)
        }, request)
    }
    @Deprecated("Small platform-only Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val app = applicationContext
        if (requestCode == EXPORT_REPORT || requestCode == EXPORT_STORAGE_REPORT || requestCode == EXPORT_WORLD_REPORT) {
            val selectedWorld = worlds.selectedItem as? String ?: prefs.getString("world", "Adventure")
            Thread({
                val result = runCatching { app.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                    val workspace = ManagedSession.workspace(app)
                    it.write(when (requestCode) {
                        EXPORT_REPORT -> ManagedSession.report(app)
                        EXPORT_WORLD_REPORT -> ManagedWorldReport.read(workspace.worldReport, workspace.working(), selectedWorld)
                        else -> workspace.storageReport()
                    })
                } }
                main.post { toast(if (result.isSuccess) "Report saved." else "Export failed: ${result.exceptionOrNull()?.message}") }
            }, "wurm-export-report").start()
            return
        }
        val accepted = ManagedSession.mutate(app, if (requestCode == IMPORT) "Importing" else "Exporting") { workspace ->
            when (requestCode) {
                IMPORT -> {
                    check(workspace.imports.current() == null) { "This preview retains one original import. Export/restore your working copy; replacing the original is not enabled." }
                    val poc = app.assets.open("wurm-arm64-poc.jar").use { it.readBytes() }
                    requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                        workspace.imports.importZip(input, poc) { ManagedSession.status("Importing", it) }
                    }
                    workspace.ensureWorking { ManagedSession.status("Importing", it) }
                }
                EXPORT_WORKING -> {
                    val root = requireNotNull(workspace.working()) { "No working copy yet." }
                    requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { out ->
                        workspace.export(root, out) { ManagedSession.status("Exporting", it) }
                    }
                }
                EXPORT_CHECKPOINT -> {
                    check(workspace.checkpoint.isFile) { "No checkpoint yet. Start creates it after Java/SQLite preflight passes." }
                    workspace.checkpoint.inputStream().use { input ->
                        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { input.copyTo(it) }
                    }
                }
            }
        }
        if (!accepted) toast("Stop the active operation before importing or exporting runtime files.")
    }
    private fun label(value: String, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setPadding(0, 8, 0, 8)
    }.also { page.addView(it) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; setOnClickListener { action() } }.also { page.addView(it) }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    override fun onResume() { super.onResume(); main.post(refresh) }
    override fun onPause() { saveWorld(); main.removeCallbacks(refresh); super.onPause() }
    companion object {
        private const val IMPORT = 50
        private const val EXPORT_WORKING = 51
        private const val EXPORT_CHECKPOINT = 52
        private const val EXPORT_REPORT = 53
        private const val EXPORT_STORAGE_REPORT = 54
        private const val EXPORT_WORLD_REPORT = 55
    }
}
