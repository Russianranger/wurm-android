package io.github.russianranger.wurmlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.content.Context
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

/** Four persistent pages; navigation never changes client/server service ownership. */
class ManagedActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("managed-settings", MODE_PRIVATE) }
    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private lateinit var clientPage: ClientPage
    private lateinit var modsPage: ModsPage
    private lateinit var diagnosticsPage: DiagnosticsPage
    private val pages = mutableListOf<ScrollView>()
    private val tabs = mutableListOf<Button>()
    private var selectedTab = SERVER
    private val scrollOffsets = IntArray(4)
    private var pageShown = false
    private lateinit var worlds: Spinner
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var restart: Button
    private lateinit var force: Button
    private val idleButtons = mutableListOf<Button>()
    private var generation: String? = null
    private var knownWorlds = emptyList<String>()
    private val refresh = object : Runnable {
        override fun run() { renderSelected(); main.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClientSession.initialize(this)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(TextView(this).apply { text="Wurm · 0.10.37"; textSize=22f; setPadding(20,12,20,8) })
        val navigation=LinearLayout(this)
        root.addView(navigation)
        val content=android.widget.FrameLayout(this)
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,12,20,12) }
        fun addPage(view: View) {
            val scroll=ScrollView(this).apply { isFillViewport=true; addView(view); visibility=View.GONE }
            pages.add(scroll); content.addView(scroll)
        }
        addPage(page)
        clientPage=ClientPage(this) { document(Intent.ACTION_OPEN_DOCUMENT,"*/*","",IMPORT_CLIENT) }
        addPage(clientPage.view)
        modsPage=ModsPage(this) { side ->
            document(Intent.ACTION_OPEN_DOCUMENT,"*/*","",if(side=="server") IMPORT_SERVER_MOD else IMPORT_CLIENT_MOD)
        }
        addPage(modsPage.view)
        diagnosticsPage=DiagnosticsPage(this, { audit(it) }, { request, name ->
            document(Intent.ACTION_CREATE_DOCUMENT,"text/plain",name,request)
        }, { worldReport() })
        addPage(diagnosticsPage.view)
        listOf("Server","Client","Mods","Diagnostics").forEachIndexed { index, title ->
            tabs += Button(this).apply {
                text=title; isAllCaps=false
                setOnClickListener { if (selectedTab!=index) selectTab(index) }
                navigation.addView(this,LinearLayout.LayoutParams(0,-2,1f))
            }
        }
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
        idleButtons += button("World gameplay settings") {
            if (saveWorld()) WorldSettingsDialog.show(this, worlds.selectedItem as String)
            else toast("Import a runtime and select a world first.")
        }
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
        render()
        val initial=savedInstanceState?.getInt("tab") ?: intent.getIntExtra("tab",SERVER)
        savedInstanceState?.let { state -> scrollOffsets.indices.forEach { index ->
            scrollOffsets[index]=state.getInt("scroll-$index")
        } }
        selectTab(initial)
    }

    private fun selectTab(tab: Int) {
        saveWorld()
        if (pageShown) scrollOffsets[selectedTab]=pages[selectedTab].scrollY
        selectedTab=tab.takeIf { it in SERVER..DIAGNOSTICS } ?: SERVER
        pages.forEachIndexed { index, view -> view.visibility=if (index==selectedTab) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { index, button ->
            button.isSelected=index==selectedTab
            button.setTextColor(getColor(if (index==selectedTab) R.color.wurm_accent else R.color.wurm_text_primary))
            button.setTypeface(null,if (index==selectedTab) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            button.contentDescription=button.text.toString()+if (index==selectedTab) ", selected tab" else ", tab"
        }
        renderSelected()
        val shown=selectedTab
        pages[shown].post { if (selectedTab==shown) pages[shown].scrollTo(0,scrollOffsets[shown]) }
        pageShown=true
    }
    private fun renderSelected() {
        when(selectedTab) { SERVER -> render(); CLIENT -> clientPage.render(); MODS -> modsPage.render(); DIAGNOSTICS -> diagnosticsPage.render() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); selectTab(intent.getIntExtra("tab",SERVER)) }
    override fun onSaveInstanceState(out: Bundle) {
        out.putInt("tab",selectedTab)
        scrollOffsets[selectedTab]=pages[selectedTab].scrollY
        scrollOffsets.indices.forEach { index -> out.putInt("scroll-$index",scrollOffsets[index]) }
        super.onSaveInstanceState(out)
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
        val state = ManagedSession.snapshot(includeLog=false)
        val recovery = ManagedSession.workspace(this).recoveryRequired.exists()
        val text = state.phase + (if (state.phase=="Error") ": ${state.detail}" else "") + if (!state.busy && recovery)
            "\nRecovery needed: export current files/logs, then restore the checkpoint or original before another Start." else ""
        if (status.text.toString()!=text) status.text=text
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
        if(requestCode==IMPORT_SERVER_MOD || requestCode==IMPORT_CLIENT_MOD) {
            modsPage.importZip(if(requestCode==IMPORT_SERVER_MOD) "server" else "client",uri)
            return
        }
        if (requestCode == IMPORT_CLIENT) {
            runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startForegroundService(Intent(this,ClientService::class.java).setAction("import").setData(uri))
            return
        }
        if (requestCode == EXPORT_CLIENT || requestCode == EXPORT_REPORT || requestCode == EXPORT_STORAGE_REPORT || requestCode == EXPORT_WORLD_REPORT) {
            val selectedWorld = worlds.selectedItem as? String ?: prefs.getString("world", "Adventure")
            Thread({
                val result = runCatching { app.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                    val workspace = ManagedSession.workspace(app)
                    it.write(when (requestCode) {
                        EXPORT_CLIENT -> ClientSession.report(app)
                        EXPORT_REPORT -> ManagedSession.report(app)
                        EXPORT_WORLD_REPORT -> ManagedWorldReport.read(workspace.worldReport, workspace.working(), selectedWorld)
                        else -> workspace.storageReport()
                    })
                } }
                main.post { toast(if (result.isSuccess) "Report saved." else "Export failed: ${result.exceptionOrNull()?.message}") }
            }, "wurm-export-report").start()
            return
        }
        if (requestCode !in listOf(IMPORT,EXPORT_WORKING,EXPORT_CHECKPOINT)) return
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
        const val SERVER=0
        const val CLIENT=1
        const val MODS=2
        const val DIAGNOSTICS=3
        private const val IMPORT_SERVER_MOD=12
        private const val IMPORT_CLIENT_MOD=13
        private const val IMPORT_CLIENT=10
        const val EXPORT_CLIENT=11
        fun tabIntent(context: Context,tab: Int)=Intent(context,ManagedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("tab",tab)
        private const val IMPORT = 50
        private const val EXPORT_WORKING = 51
        private const val EXPORT_CHECKPOINT = 52
        const val EXPORT_REPORT = 53
        const val EXPORT_STORAGE_REPORT = 54
        const val EXPORT_WORLD_REPORT = 55
    }
}
