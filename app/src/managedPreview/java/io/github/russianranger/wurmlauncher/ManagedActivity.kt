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
    private val navigationPrefs by lazy { getSharedPreferences("launcher-settings", MODE_PRIVATE) }
    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private lateinit var clientPage: ClientPage
    private lateinit var modsPage: ModsPage
    private lateinit var diagnosticsPage: DiagnosticsPage
    private val pages = mutableListOf<ScrollView>()
    private val tabs = mutableListOf<Button>()
    private lateinit var backdrop: OakTheme.Backdrop
    private lateinit var navigationScroll: android.widget.HorizontalScrollView
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
        root.addView(OakTheme.header(this))
        val navigation=LinearLayout(this)
        navigationScroll=android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            isFillViewport=true
            addView(navigation)
        }
        root.addView(navigationScroll)
        val content=android.widget.FrameLayout(this)
        root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        backdrop=OakTheme.Backdrop(this)
        content.addView(backdrop,android.widget.FrameLayout.LayoutParams(-1,-1))
        content.addView(View(this).apply { setBackgroundColor(0x300A120D) },android.widget.FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        page = LauncherUi.column(this)
        fun addPage(view: View) {
            val scroll=ScrollView(this).apply {
                isFillViewport=true; addView(OakTheme.page(view,pages.size)); visibility=View.GONE
            }
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
            document(Intent.ACTION_CREATE_DOCUMENT,if(request==EXPORT_SUPPORT) "application/zip" else "text/plain",name,request)
        }, { worldReport() }, { side -> modsPage.diagnostics(side) })
        addPage(diagnosticsPage.view)
        listOf("Server","Client","Mods","Diagnostics").forEachIndexed { index, title ->
            tabs += Button(this).apply {
                text=title; isAllCaps=false
                textSize=16f
                minimumWidth=LauncherUi.dp(context,80)
                setOnClickListener { if (selectedTab!=index) selectTab(index) }
                navigation.addView(this,LinearLayout.LayoutParams(-2,-1,1f).apply {
                    val gap=LauncherUi.dp(context,3)
                    setMargins(gap,gap,gap,gap)
                })
            }
        }
        status = label("Server",22f)
        label("Selected world")
        worlds = Spinner(this).also { page.addView(it) }
        start = button("Start server") {
            if (!saveWorld()) return@button
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 70)
            startForegroundService(Intent(this, ManagedServerService::class.java).setAction(ManagedServerService.START))
        }
        stop = button("Save & stop server") { ManagedSession.stop() }
        val primary=page
        page=LauncherUi.section(primary,"World & server settings")
        idleButtons += button("World gameplay settings") {
            if (saveWorld()) WorldSettingsDialog.show(this, worlds.selectedItem as String)
            else toast("Import a runtime and select a world first.")
        }
        idleButtons += button("Server memory & connection check") { settings() }
        page=LauncherUi.section(primary,"Setup & runtime",!java.io.File(filesDir,"managed-preview/original/current").isFile)
        label("Prepare the supported original WurmServerLauncher ZIP or import a stopped Thor server export. Android dependencies and compatibility are supplied automatically, offline. For a complete app backup, use Backups & migration below.")
        idleButtons += button("Prepare / import server ZIP") {
            AlertDialog.Builder(this).setTitle("Prepare supported server runtime")
                .setMessage("Choose the supported original WurmServerLauncher ZIP or a stopped Thor export, with server.jar, common.jar, lib/ and a world. The original desktop dist/ layout is supported. The app supplies Android dependencies and prepares compatibility before the first launch. Game JARs, maps and databases are preserved in a private copy. Missing desktop database paths are adjusted to the included world. Unknown game versions are rejected. Free storage: ${filesDir.usableSpace/1024/1024} MiB; the working copy and checkpoint need additional space.")
                .setPositiveButton("Choose ZIP") { _, _ -> document(Intent.ACTION_OPEN_DOCUMENT,"*/*","",IMPORT) }
                .setNegativeButton("Cancel",null).show()
        }
        page=primary
        button("Backups & migration") { saveWorld(); startActivity(Intent(this,BackupActivity::class.java)) }
        page=LauncherUi.section(primary,"Server exports & recovery")
        idleButtons += button("Export working server ZIP") { document(Intent.ACTION_CREATE_DOCUMENT,"application/zip","wurm-working-runtime.zip",EXPORT_WORKING) }
        idleButtons += button("Export before-start checkpoint ZIP") { document(Intent.ACTION_CREATE_DOCUMENT,"application/zip","wurm-before-start.zip",EXPORT_CHECKPOINT) }
        idleButtons += button("Restore before-start checkpoint") { restore(false) }
        idleButtons += button("Restore original import") { restore(true) }
        page=LauncherUi.section(primary,"Advanced session controls")
        restart=button("Save & restart server") { ManagedSession.stop(restart=true) }
        force=button("Force stop server") {
            AlertDialog.Builder(this).setTitle("Force stop the server?")
                .setMessage("Unsaved world changes can be lost. The original import and before-start checkpoint remain available.")
                .setPositiveButton("Force stop") { _,_->ManagedSession.forceStop() }.setNegativeButton("Cancel",null).show()
        }
        page=primary
        render()
        val initial=savedInstanceState?.getInt("tab") ?: intent.getIntExtra("tab",navigationPrefs.getInt("tab",CLIENT))
        savedInstanceState?.let { state -> scrollOffsets.indices.forEach { index ->
            scrollOffsets[index]=state.getInt("scroll-$index")
        } }
        selectTab(initial)
    }

    private fun selectTab(tab: Int) {
        saveWorld()
        if (pageShown) scrollOffsets[selectedTab]=pages[selectedTab].scrollY
        selectedTab=tab.takeIf { it in SERVER..DIAGNOSTICS } ?: CLIENT
        if(navigationPrefs.getInt("tab",-1)!=selectedTab) navigationPrefs.edit().putInt("tab",selectedTab).apply()
        pages.forEachIndexed { index, view -> view.visibility=if (index==selectedTab) View.VISIBLE else View.GONE }
        backdrop.show(selectedTab)
        tabs.forEachIndexed { index, button ->
            button.isSelected=index==selectedTab
            button.setTextColor(getColor(if (index==selectedTab) R.color.wurm_accent else R.color.wurm_text_primary))
            button.contentDescription=button.text.toString()+if (index==selectedTab) ", selected tab" else ", tab"
        }
        navigationScroll.post { tabs[selectedTab].requestRectangleOnScreen(android.graphics.Rect(0,0,tabs[selectedTab].width,tabs[selectedTab].height),true) }
        renderSelected()
        val shown=selectedTab
        pages[shown].post { if (selectedTab==shown) pages[shown].scrollTo(0,scrollOffsets[shown]) }
        pageShown=true
    }
    private fun renderSelected() {
        when(selectedTab) { SERVER -> render(); CLIENT -> clientPage.render(); MODS -> modsPage.render(); DIAGNOSTICS -> diagnosticsPage.render() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); selectTab(intent.getIntExtra("tab",navigationPrefs.getInt("tab",CLIENT))) }
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
        val text = (OperationGate.recoveryError ?: state.phase) + (if (state.phase=="Error") ": ${state.detail}" else "") + if (!state.busy && recovery)
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
        idleButtons.forEach { it.isEnabled = !state.busy && !OperationGate.maintenance && OperationGate.recoveryError==null }
        worlds.isEnabled = !state.busy && installed != null
        start.isEnabled = !state.busy && installed != null && !recovery && !OperationGate.maintenance && OperationGate.recoveryError==null
        start.visibility=if(ManagedSession.ownsServer()) View.GONE else View.VISIBLE
        stop.visibility=if(ManagedSession.ownsServer()) View.VISIBLE else View.GONE
        stop.isEnabled = ManagedSession.ownsServer() && state.phase != "Stopping"
        restart.isEnabled = ManagedSession.ownsServer() && state.phase == "Running"
        force.isEnabled = ManagedSession.ownsServer() && state.phase == "Stopping"
    }

    private fun saveWorld(): Boolean {
        val world = worlds.selectedItem as? String ?: return false
        if (world !in knownWorlds) return false
        if(prefs.getString("world",null)!=world) prefs.edit().putString("world", world).apply()
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
        if(requestCode==EXPORT_SUPPORT) {
            startForegroundService(Intent(this,BackupService::class.java).setAction("support").setData(uri))
            startActivity(Intent(this,BackupActivity::class.java))
            return
        }
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
                        val preparation=ServerRuntimePreparation(openAsset = { name -> app.assets.open(name) })
                        workspace.imports.importPreparedZip(input, poc,
                            { root -> preparation.prepare(root) { ManagedSession.status("Importing",it) } }) {
                            ManagedSession.status("Importing", it)
                        }
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
    private fun label(value: String, size: Float = 14f) = LauncherUi.label(page,value,size)
    private fun button(value: String, action: () -> Unit) = LauncherUi.button(page,value,action)
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    override fun onResume() {
        super.onResume()
        if(BackupStatus.busy) startActivity(Intent(this,BackupActivity::class.java))
        main.removeCallbacks(refresh); main.post(refresh)
    }
    override fun onPause() { saveWorld(); main.removeCallbacks(refresh); super.onPause() }
    override fun onStart() { super.onStart(); backdrop.show(selectedTab) }
    override fun onStop() { backdrop.release(); super.onStop() }
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
        const val EXPORT_SUPPORT = 56
    }
}
