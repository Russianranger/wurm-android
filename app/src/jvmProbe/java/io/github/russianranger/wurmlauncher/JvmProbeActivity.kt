package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Install-alongside diagnostic entry point, only included in the jvmProbe APK. */
class JvmProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var run: Button
    private lateinit var cancel: Button
    private lateinit var export: Button
    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var scroll: ScrollView
    private val refresh = object : Runnable {
        override fun run() {
            val state = JvmProbeCoordinator.snapshot(this@JvmProbeActivity)
            run.isEnabled = !state.busy
            cancel.isEnabled = state.busy
            export.isEnabled = !state.busy && state.log.isNotEmpty()
            status.text = state.detail
            if (logs.text.toString() != state.log) {
                val bottom = !scroll.canScrollVertically(1)
                logs.text = state.log
                if (bottom) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setPadding(0, dp(5), 0, dp(5))
        }.also { page.addView(it) }
        label("Wurm Server JVM Test", 23f).setTypeface(null, Typeface.BOLD)
        label("Android ARM64 Java 17 + SQLite diagnostic", 15f)
        label("Select your existing runtime ZIP. This copies only its two SQLite JARs and tests a disposable database. Adventure stays in your separate Wurm Server app. Keep this test app open until it finishes.", 13f)
        label("Candidate: Android OpenJDK 17.0.10. Working Termux baseline: 17.0.20. This test does not start Wurm.", 12f)
        run = Button(this).apply {
            text = "Select Runtime ZIP and Run Test"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, PICK_ZIP)
            }
        }
        page.addView(run)
        val actions = LinearLayout(this)
        cancel = Button(this).apply { text = "Cancel Test"; setOnClickListener { JvmProbeCoordinator.cancel() } }
        export = Button(this).apply {
            text = "Export Report"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "wurm-jvm-probe-report.txt")
                }, EXPORT)
            }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(export, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(actions)
        status = label("", 14f)
        logs = TextView(this).apply { textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(logs) }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    @Deprecated("Uses the existing platform Activity API.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == PICK_ZIP) {
            JvmProbeCoordinator.start(this, uri)
        } else if (requestCode == EXPORT) {
            val report = JvmProbeCoordinator.snapshot(this).log + "\n"
            val app = applicationContext
            Thread({
                val failure = runCatching {
                    requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(report) }
                }.exceptionOrNull()
                handler.post {
                    Toast.makeText(app, if (failure == null) "Report saved." else "Export failed: ${failure.message}", Toast.LENGTH_LONG).show()
                }
            }, "probe-report-export").start()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    companion object { private const val PICK_ZIP = 40; private const val EXPORT = 41 }
}
