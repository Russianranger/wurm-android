package io.github.russianranger.wurmlauncher

import android.app.*
import android.content.Intent
import android.os.*

class ClientService : Service() {
    private var owns = false
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { ClientSession.stop(); if (!owns) stopSelf(); return START_NOT_STICKY }
        if (owns || intent?.action !in listOf("import", "start", "local", "input", "render")) { if (!owns) stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("wurm_client", "Wurm client", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 10, Intent(this, ClientActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 11, Intent(this, ClientService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(3726, Notification.Builder(this, "wurm_client").setSmallIcon(R.drawable.ic_server).setContentTitle("Wurm client")
            .setContentText("Client operation active").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop Client", stop).build()).build())
        try {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:client").apply { acquire(15 * 60 * 1000L) }
            owns = ClientSession.start(applicationContext, intent!!.action!!, intent.data) { Handler(Looper.getMainLooper()).post { finish() } }
            if (!owns) finish()
        } catch (failure: Exception) { ClientSession.log("[service] $failure"); finish() }
        return START_NOT_STICKY
    }
    private fun finish() { owns = false; wake?.let { if (it.isHeld) it.release() }; wake = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { if (owns) ClientSession.stop(); wake?.let { if (it.isHeld) it.release() }; super.onDestroy() }
}
