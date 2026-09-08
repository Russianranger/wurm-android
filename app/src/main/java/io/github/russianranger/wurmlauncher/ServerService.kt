package io.github.russianranger.wurmlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class ServerService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var controller: RootServerController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Wurm server", NotificationManager.IMPORTANCE_LOW)
        )
    }

    @SuppressLint("WakelockTimeout") // User-started server; released on all normal exit/destroy paths.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller?.stop() ?: stopSelf()
            return START_NOT_STICKY
        }
        // Do not relaunch automatically after Android kills the app or reboots.
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (controller != null) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, notification("Starting · requesting root"))
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val config = LaunchConfig(
            prefs.getString("runtime", LaunchConfig.DEFAULT_RUNTIME)!!,
            prefs.getString("java", LaunchConfig.DEFAULT_JAVA)!!
        )
        val problem = config.validate() ?: if (!Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a"))
            "This milestone requires an ARM64 Android device." else null
        if (problem != null) {
            finishServer(true, problem)
            return START_NOT_STICKY
        }
        try {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:server")
                .apply { acquire() }
            ServerState.status(Phase.STARTING, "Requesting root; grant access in your root manager.")
            ServerState.append("[launcher] Starting Adventure in ${config.runtimePath}")
            ServerState.append("[launcher] Java runs as root. Use a backed-up world; root-created files may need ownership repair.")
            val script = assets.open("server-supervisor.sh").bufferedReader().use { it.readText() }
            controller = RootServerController(config, script,
                onStatus = { phase, detail -> main.post {
                    if (!destroyed && controller != null) {
                        // Ignore a queued RUNNING callback once Stop has been requested.
                        if (phase != Phase.RUNNING || ServerState.snapshot().phase != Phase.STOPPING) {
                            ServerState.status(phase, detail)
                            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIFICATION_ID, notification("${phase.name.lowercase()} · $detail"))
                            }
                        }
                    }
                } },
                onExit = { failed, detail -> main.post {
                    if (!destroyed) finishServer(failed, detail)
                } }
            ).also { it.start() }
        } catch (e: Exception) {
            finishServer(true, e.message ?: "Unable to start foreground server service.")
        }
        return START_NOT_STICKY
    }

    private fun finishServer(failed: Boolean, message: String) {
        controller = null
        ServerState.status(if (failed) Phase.ERROR else Phase.STOPPED, message)
        ServerState.append("[launcher] $message")
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_server)
            .setContentTitle("Wurm Server Launcher")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop Server", stop).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        destroyed = true
        controller?.stop()
        controller = null
        releaseWakeLock()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.russianranger.wurmlauncher.START"
        const val ACTION_STOP = "io.github.russianranger.wurmlauncher.STOP"
        const val PREFS = "launcher"
        private const val CHANNEL = "wurm_server"
        private const val NOTIFICATION_ID = 3724
    }
}
