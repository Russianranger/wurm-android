package io.github.russianranger.wurmlauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Platform widgets keep this first APK small and the Termux build dependency-light. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runtime: EditText
    private lateinit var java: EditText
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var logScroll: ScrollView
    private var revision = -1L
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val prefs = getSharedPreferences(ServerService.PREFS, MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun label(text: String, size: Float = 14f) = TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(4), 0, dp(4))
        }.also { root.addView(it) }

        label("Rooted POC controls", 23f).setTypeface(null, Typeface.BOLD)
        label("Rooted ARM64 · Adventure · server only")
        label("Runs Java as root. Back up your world first; new files may be root-owned.", 12f)
        label("Runtime directory")
        runtime = pathField(1001, savedInstanceState?.getString("runtime")
            ?: prefs.getString("runtime", LaunchConfig.DEFAULT_RUNTIME)!!)
        root.addView(runtime)
        label("Termux Java executable")
        java = pathField(1002, savedInstanceState?.getString("java")
            ?: prefs.getString("java", LaunchConfig.DEFAULT_JAVA)!!)
        root.addView(java)
        val buttons = LinearLayout(this)
        start = Button(this).apply { text = "Start Server"; setOnClickListener { startServer() } }
        stop = Button(this).apply {
            text = "Stop Server"
            setOnClickListener {
                isEnabled = false
                startService(Intent(this@MainActivity, ServerService::class.java)
                    .setAction(ServerService.ACTION_STOP))
            }
        }
        buttons.addView(start, LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(buttons)
        status = label("Stopped", 15f)
        label("Live output · latest 500 lines", 12f)
        logs = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(getColor(R.color.wurm_surface))
        }
        logScroll = ScrollView(this).apply { addView(logs) }
        root.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("notificationAsked", false)) {
            prefs.edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun pathField(viewId: Int, value: String) = EditText(this).apply {
        id = viewId
        setText(value)
        textSize = 13f
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun startServer() {
        val config = LaunchConfig(runtime.text.toString().trim(), java.text.toString().trim())
        config.validate()?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(ServerService.PREFS, MODE_PRIVATE).edit()
            .putString("runtime", config.runtimePath).putString("java", config.javaPath).apply()
        start.isEnabled = false
        try {
            startForegroundService(Intent(this, ServerService::class.java).setAction(ServerService.ACTION_START))
        } catch (e: Exception) {
            ServerState.status(Phase.ERROR, e.message ?: "Unable to start service.")
        }
    }

    private fun render() {
        val state = ServerState.snapshot()
        if (state.revision == revision) return
        revision = state.revision
        status.text = "${state.phase.name.lowercase().replaceFirstChar { it.uppercase() }} · ${state.detail}"
        val idle = state.phase == Phase.STOPPED || state.phase == Phase.ERROR
        start.isEnabled = idle
        stop.isEnabled = state.phase == Phase.STARTING || state.phase == Phase.RUNNING
        runtime.isEnabled = idle
        java.isEnabled = idle
        if (logs.text.toString() != state.log) {
            val atBottom = !logScroll.canScrollVertically(1)
            logs.text = state.log
            if (atBottom) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("runtime", runtime.text.toString())
        outState.putString("java", java.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
