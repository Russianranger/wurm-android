package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.widget.*

/** All launcher diagnostics, refreshed only while this page is visible. */
class DiagnosticsPage(private val activity: Activity, private val audit: (String) -> Unit,
    private val export: (Int,String) -> Unit, private val worldReport: () -> String, private val modDiagnostics: (String)->Unit) {
    val view=LauncherUi.column(activity)
    private var target=view
    private var warningsOnly=false
    private fun filterLog(value: String)=if(!warningsOnly) value else value.lineSequence().filter { Regex("(?i)warn|error|exception|failed|fatal").containsMatchIn(it) }.joinToString("\n")
    private val clientTests=mutableListOf<Button>()
    private val serverTests=mutableListOf<Button>()
    private val status: TextView
    private val clientLogs: TextView
    private val serverLogs: TextView
    private val verbose: CheckBox
    private val periodicGc: CheckBox
    private val jobProfiling: CheckBox
    private val textBuffers: CheckBox
    private fun label(text: String)=LauncherUi.label(target,text)
    private fun button(text: String, action: () -> Unit)=LauncherUi.button(target,text,action)
    private fun showReport(title: String, value: String) {
        val text=TextView(activity).apply { this.text=value; setTextIsSelectable(true); setPadding(20,12,20,12) }
        AlertDialog.Builder(activity).setTitle(title).setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Close",null).show()
    }
    init {
        status=label("")
        button("Export support bundle") { export(ManagedActivity.EXPORT_SUPPORT,"wurm-support.zip") }
        val prefs=activity.getSharedPreferences("client-settings",Activity.MODE_PRIVATE)
        verbose=CheckBox(activity).apply {
            text="Verbose client diagnostics"; isChecked=prefs.getBoolean("verbose-diagnostics",false)
            target.addView(this)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("verbose-diagnostics",checked).apply() }
        }
        label("Normal mode keeps errors, startup checks and performance samples. Enable verbose output before starting the client when investigating a problem.")
        periodicGc=CheckBox(activity).apply {
            text="Skip periodic client cleanup (test)"; isChecked=prefs.getBoolean("skip-periodic-gc",false)
            target.addView(this)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("skip-periodic-gc",checked).apply() }
        }
        label("Compare brief pauses with this off, then on. Applies on the next client start. Normal memory collection and shutdown cleanup continue; watch memory use during longer play.")
        textBuffers=CheckBox(activity).apply {
            text="Reuse text buffers · next client start"; isChecked=prefs.getBoolean("reuse-text-buffers",true)
            target.addView(this)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("reuse-text-buffers",checked).apply() }
        }
        label("Reuses completed text draws within a fixed memory limit. Turn off for a comparison or if text looks incorrect.")
        jobProfiling=CheckBox(activity).apply {
            text="Enable job profiling · next client start"; isChecked=prefs.getBoolean("job-profiling",false)
            target.addView(this)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("job-profiling",checked).apply() }
        }
        label("Optional. During play, start a five-minute memory recording here or in the gear menu. Records allocation by job type; normal cleanup continues.")
        button("Client memory recording") { ClientMemoryDialog.show(activity) }
        target=LauncherUi.section(view,"Individual reports")
        button("Export Client Report") { export(ManagedActivity.EXPORT_CLIENT,"wurm-client-report.txt") }
        button("Export Server Report") { export(ManagedActivity.EXPORT_REPORT,"wurm-server-report.txt") }
        target=LauncherUi.section(view,"Client tests")
        label("Stop the client before running a test. Normal play does not require these checks.")
        clientTests += button("Native Memory Startup Test") {
            activity.startForegroundService(Intent(activity,ClientService::class.java).setAction("native-heap"))
        }
        clientTests += button("JVM Memory Test") {
            activity.startForegroundService(Intent(activity,ClientService::class.java).setAction("memory"))
        }
        clientTests += button("Start Controller Test") { activity.startActivity(Intent(activity,ControllerTestActivity::class.java)) }
        clientTests += button("LWJGL Window / Input Test") {
            activity.startActivity(Intent(activity,GraphicsTestActivity::class.java).putExtra("mode","window"))
        }
        clientTests += button("JVM Graphics Test") { activity.startActivity(Intent(activity,GraphicsTestActivity::class.java)) }
        button("Stop Client / Test") { activity.startService(Intent(activity,ClientService::class.java).setAction("stop")) }
        target=LauncherUi.section(view,"World & storage checks")
        button("View world/configuration report") { showReport("World/configuration report",worldReport()) }
        button("Export world/configuration report") { export(ManagedActivity.EXPORT_WORLD_REPORT,"wurm-world-report.txt") }
        label("Storage verification").textSize=20f
        label("Uses the world selected on Server. Capture while stopped, play and stop the server, then check changes. Database checks use disposable copies.")
        serverTests += button("Capture storage baseline") {
            AlertDialog.Builder(activity).setTitle("Capture storage baseline?")
                .setMessage("This replaces the previous comparison baseline. Export the current storage report first if you need it.")
                .setPositiveButton("Capture") { _, _ -> audit(ManagedServerService.BASELINE) }.setNegativeButton("Cancel",null).show()
        }
        serverTests += button("Check stored data") { audit(ManagedServerService.CHECK) }
        button("View storage report") { showReport("Storage report",ManagedSession.workspace(activity).storageReport()) }
        button("Export storage report") { export(ManagedActivity.EXPORT_STORAGE_REPORT,"wurm-storage-report.txt") }
        target=LauncherUi.section(view,"Mod diagnostics")
        button("Server mod files & manifest") { modDiagnostics("server") }
        button("Client mod files & manifest") { modDiagnostics("client") }
        target=LauncherUi.section(view,"Live logs")
        val filter=CheckBox(activity).apply { text="Warnings and errors only"; target.addView(this) }
        filter.setOnCheckedChangeListener { _,checked -> warningsOnly=checked; render() }
        label("Client output · latest 80 lines").textSize=20f
        clientLogs=label("").apply { textSize=12f; typeface=Typeface.MONOSPACE; setTextIsSelectable(true) }
        label("Server output · latest 500 lines").textSize=20f
        serverLogs=label("").apply { textSize=12f; typeface=Typeface.MONOSPACE; setTextIsSelectable(true) }
    }
    fun render() {
        val client=ClientSession.snapshot()
        val server=ManagedSession.snapshot()
        verbose.isEnabled=!client.busy
        periodicGc.isEnabled=!client.busy
        jobProfiling.isEnabled=!client.busy
        textBuffers.isEnabled=!client.busy
        status.updateText("Client: ${client.phase} · ${client.detail}\nServer: ${server.phase} · ${server.detail}")
        clientTests.forEach { it.isEnabled=!client.busy }
        serverTests.forEach { it.isEnabled=!server.busy }
        if(clientLogs.isShown) clientLogs.updateText(filterLog(ClientSession.recent()))
        if(serverLogs.isShown) serverLogs.updateText(filterLog(server.log))
    }
    private fun TextView.updateText(value: String) { if (text.toString()!=value) text=value }
}
