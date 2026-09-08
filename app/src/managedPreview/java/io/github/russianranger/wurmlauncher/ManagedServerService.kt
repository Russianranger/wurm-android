package io.github.russianranger.wurmlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class ManagedServerService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var owns = false
    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { ManagedSession.stop(); if (!owns) stopSelf(); return START_NOT_STICKY }
        if (intent?.action != START) { if (!owns) stopSelf(); return START_NOT_STICKY }
        if (owns) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Managed Wurm server", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, ManagedActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ManagedServerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(3725, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_server)
            .setContentTitle("Wurm Server").setContentText("Server session active · tap for status and logs")
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Stop Server", stop).build())
            .setOngoing(true).build())
        val prefs = getSharedPreferences("managed-settings", MODE_PRIVATE)
        val config = ManagedLaunch(prefs.getString("world", "Adventure")!!,
            prefs.getInt("heap", 4096), prefs.getInt("port", 3724))
        try {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:managed-server").apply { acquire() }
            owns = ManagedSession.start(this, config) { main.post { finish() } }
            if (!owns) finish()
        } catch (failure: Exception) {
            ManagedSession.log("[service] $failure")
            finish()
        }
        return START_NOT_STICKY // Never silently restart an interrupted world.
    }

    private fun finish() {
        owns = false
        wake?.let { if (it.isHeld) it.release() }; wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (owns) ManagedSession.stop()
        wake?.let { if (it.isHeld) it.release() }; wake = null
        super.onDestroy()
    }
    companion object {
        const val START = "wurm.managed.START"
        const val STOP = "wurm.managed.STOP"
        private const val CHANNEL = "wurm_managed_server"
    }
}
