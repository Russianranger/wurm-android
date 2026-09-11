package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.widget.*

/** All launcher diagnostics, refreshed only while this page is visible. */
class DiagnosticsPage(private val activity: Activity, private val audit: (String) -> Unit,
    private val export: (Int,String) -> Unit, private val worldReport: () -> String) {
    val view=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(20,12,20,12) }
    private val clientTests=mutableListOf<Button>()
    private val serverTests=mutableListOf<Button>()
    private val status: TextView
    private val clientLogs: TextView
    private val serverLogs: TextView
    private fun label(text: String)=TextView(activity).apply { this.text=text; setPadding(0,8,0,8); view.addView(this) }
    private fun button(text: String, action: () -> Unit)=Button(activity).apply {
        this.text=text; setOnClickListener { action() }; view.addView(this)
    }
    private fun showReport(title: String, value: String) {
        val text=TextView(activity).apply { this.text=value; setTextIsSelectable(true); setPadding(20,12,20,12) }
        AlertDialog.Builder(activity).setTitle(title).setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Close",null).show()
    }
    init {
        status=label("")
        button("Export Client Report") { export(ManagedActivity.EXPORT_CLIENT,"wurm-client-report.txt") }
        button("Export Server Report") { export(ManagedActivity.EXPORT_REPORT,"wurm-server-report.txt") }
        label("Client tests").textSize=20f
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
        label("World and configuration").textSize=20f
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
        label("Client output · latest 80 lines").textSize=20f
        clientLogs=label("").apply { textSize=12f; typeface=Typeface.MONOSPACE; setTextIsSelectable(true) }
        label("Server output · latest 500 lines").textSize=20f
        serverLogs=label("").apply { textSize=12f; typeface=Typeface.MONOSPACE; setTextIsSelectable(true) }
    }
    fun render() {
        val client=ClientSession.snapshot()
        val server=ManagedSession.snapshot()
        status.updateText("Client: ${client.phase} · ${client.detail}\nServer: ${server.phase} · ${server.detail}")
        clientTests.forEach { it.isEnabled=!client.busy }
        serverTests.forEach { it.isEnabled=!server.busy }
        clientLogs.updateText(ClientSession.recent())
        serverLogs.updateText(server.log)
    }
    private fun TextView.updateText(value: String) { if (text.toString()!=value) text=value }
}
