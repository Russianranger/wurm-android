package io.github.russianranger.wurmlauncher

import android.app.*
import android.content.Intent
import android.os.*
import android.widget.*

class BackupActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val actions=mutableListOf<Button>()
    private lateinit var cancel: Button
    private val refresh=object: Runnable { override fun run() {
        status.text=OperationGate.recoveryError ?: BackupStatus.message
        actions.forEach { it.isEnabled=!BackupStatus.busy }
        cancel.isEnabled=BackupStatus.busy
        handler.postDelayed(this,500)
    } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val page=LauncherUi.column(this); setContentView(ScrollView(this).apply { addView(page) })
        LauncherUi.label(page,"Backups & migration",24f)
        LauncherUi.label(page,"A complete backup includes imported server/client files, worlds, the original server and checkpoint, both mod stores, client user files, game bindings and launcher/controller/graphics settings. Runtime caches and logs are omitted. Allow room for another full copy when restoring.")
        status=LauncherUi.label(page,BackupStatus.message)
        actions+=LauncherUi.button(page,"Export complete backup") { choose(false) }
        actions+=LauncherUi.button(page,"Restore complete backup") {
            AlertDialog.Builder(this).setTitle("Restore both server and client?")
                .setMessage("Save and stop both first. Restoring replaces this app's server, client and settings with the selected backup, including absent components. Export the current installation first if you need it. The archive is checked before replacement; interrupted replacement is recovered.")
                .setPositiveButton("Choose backup") { _,_->choose(true) }.setNegativeButton("Cancel",null).show()
        }
        actions+=LauncherUi.button(page,"Retry interrupted-restore recovery") { start("recover",null) }
        cancel=LauncherUi.button(page,"Cancel operation") { startService(Intent(this,BackupService::class.java).setAction("cancel")) }
        LauncherUi.button(page,"Back to launcher") {
            if(BackupStatus.busy) Toast.makeText(this,"Wait for the operation or cancel it first.",Toast.LENGTH_SHORT).show()
            else returnToLauncher()
        }
        LauncherUi.label(page,"Older previews: import your stopped working server export on Server, then your client ZIP on Client. A full backup from an older app is available only if that version includes this feature. Keep the older app installed until migration is verified.")
    }
    private fun returnToLauncher() {
        val next=if(BackupStatus.restored) Intent(this,ManagedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("tab",ManagedActivity.CLIENT)
            else ManagedActivity.tabIntent(this,ManagedActivity.CLIENT)
        BackupStatus.restored=false
        startActivity(next); finish()
    }
    private fun choose(restore: Boolean) {
        startActivityForResult(Intent(if(restore) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type=if(restore) "*/*" else "application/zip"
            if(!restore) putExtra(Intent.EXTRA_TITLE,"wurm-complete-${java.time.LocalDate.now()}.zip")
        },if(restore) 1 else 2)
    }
    private fun start(action: String,uri: android.net.Uri?) {
        startForegroundService(Intent(this,BackupService::class.java).setAction(action).setData(uri))
    }
    @Deprecated("Platform document picker")
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(result==RESULT_OK && request in 1..2) data?.data?.let { start(if(request==1) "restore" else "export",it) }
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed() { if(!BackupStatus.busy) returnToLauncher() else Toast.makeText(this,"Wait for the operation or cancel it first.",Toast.LENGTH_SHORT).show() }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
