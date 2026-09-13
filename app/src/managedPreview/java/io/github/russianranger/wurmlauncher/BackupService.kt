package io.github.russianranger.wurmlauncher

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.*

object BackupStatus {
    @Volatile var busy=false
    @Volatile var message="Stop the server and client before exporting or restoring a complete backup."
    @Volatile var restored=false
    @Volatile var worker: Thread?=null
    fun <T> locked(context: Context,action: ()->T): T {
        val paths=listOf("managed-preview/server.lock","managed-client/client.lock","managed-client/process.lock")
        fun acquire(index: Int): T {
            if(index==paths.size) return action()
            val file=File(context.filesDir,paths[index]); file.parentFile!!.mkdirs()
            return RandomAccessFile(file,"rw").channel.use { channel ->
                (channel.tryLock() ?: error("A previous runtime is still exiting. Wait and retry.")).use { acquire(index+1) }
            }
        }
        return acquire(0)
    }
    fun recover(context: Context) {
        if(!File(context.filesDir,"backup-restore").exists() && !File(context.filesDir,"backup-cleanup").exists()) return
        try {
            locked(context) { AppBackup(context.filesDir).recover(BackupPreferences(context)) }
            OperationGate.recoveryError=null
            message="Interrupted restore recovered. Previous installation retained."
        } catch(failure: Exception) {
            OperationGate.recoveryError="Backup recovery needs attention: ${failure.message}"
            message=OperationGate.recoveryError!!
        }
    }
}

class ManagedApplication : Application() {
    override fun onCreate() { super.onCreate(); BackupStatus.recover(this) }
}

/** Foreground ownership prevents Activity recreation from abandoning a long ZIP operation. */
class BackupService : Service() {
    private var owns=false
    private var wake: PowerManager.WakeLock?=null
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="cancel") { BackupStatus.worker?.interrupt(); if(!owns) stopSelf(); return START_NOT_STICKY }
        if(owns) return START_NOT_STICKY
        val action=intent?.action
        if(action !in listOf("export","restore","support","recover")) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("wurm_backup","Wurm backup and restore",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,80,Intent(this,BackupActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(3727,Notification.Builder(this,"wurm_backup").setSmallIcon(R.drawable.ic_server)
            .setContentTitle("Wurm backup and reports").setContentText("Operation in progress").setContentIntent(open).setOngoing(true).build())
        val maintenance=action!="support"
        if(maintenance && !OperationGate.beginMaintenance()) {
            BackupStatus.message="Save and stop both runtimes and wait for file operations to finish, then retry."
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        owns=true; BackupStatus.busy=true; BackupStatus.restored=false; BackupStatus.message="Preparing…"
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"$packageName:backup").apply { acquire(60*60*1000L) }
        BackupStatus.worker=Thread({
            try {
                if(action=="support") exportSupport(requireNotNull(intent?.data)) else BackupStatus.locked(this) {
                    val backup=AppBackup(filesDir); val prefs=BackupPreferences(this)
                    when(action) {
                        "export" -> requireNotNull(contentResolver.openOutputStream(requireNotNull(intent?.data),"wt")).use {
                            backup.export(it,prefs,packageManager.getPackageInfo(packageName,0).versionName ?: "unknown") { message -> BackupStatus.message=message }
                        }
                        "restore" -> requireNotNull(contentResolver.openInputStream(requireNotNull(intent?.data))).use {
                            backup.restore(it,prefs) { message -> BackupStatus.message=message }; BackupStatus.restored=true
                        }
                        "recover" -> { backup.recover(prefs); OperationGate.recoveryError=null; BackupStatus.message="Recovery completed." }
                    }
                }
            } catch(failure: Exception) {
                BackupStatus.message="${if(Thread.currentThread().isInterrupted) "Cancelled" else "Operation failed"}: ${failure.message}. Incomplete export files must not be used."
                if(File(filesDir,"backup-restore").exists()) OperationGate.recoveryError="Pending backup recovery. Open Backups and choose Retry recovery."
            } finally {
                if(maintenance) OperationGate.endMaintenance()
                BackupStatus.worker=null; BackupStatus.busy=false
                Handler(Looper.getMainLooper()).post { owns=false; wake?.let { if(it.isHeld) it.release() }; wake=null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        },"wurm-backup").apply { start() }
        return START_NOT_STICKY
    }
    private fun exportSupport(uri: android.net.Uri) {
        requireNotNull(contentResolver.openOutputStream(uri,"wt")).use { out -> ZipOutputStream(out.buffered()).use { zip ->
            fun add(name: String,value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            add("session.txt","Exported together: ${java.time.Instant.now()}\nPackage: $packageName\nReports include retained history; correlate timestamps and PIDs.\n")
            add("client-report.txt",ClientSession.report(this,false)); add("server-report.txt",ManagedSession.report(this))
            add("storage-report.txt",ManagedSession.workspace(this).storageReport())
        } }
        BackupStatus.message="Support bundle saved."
    }
    override fun onDestroy() { if(owns) BackupStatus.worker?.interrupt(); wake?.let { if(it.isHeld) it.release() }; super.onDestroy() }
}
