package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.widget.*
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.concurrent.Executors

/** Displays only actual readback frames from the JVM, never a stand-in GLES drawing. */
class GraphicsTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var frame: GameFrameView
    private lateinit var run: Button
    private var displayedBitmap: Bitmap? = null
    private var mode = "render"
    private var capture: ControllerCapture? = null
    private var reading = false
    private var shownIdentity = ""
    private var failureIdentity = ""
    private var resumed = false
    private val refresh = object : Runnable {
        override fun run() {
            val state = ClientSession.snapshot()
            status.text = "${state.phase}: ${state.detail}\n${ClientSession.recent().lines().takeLast(8).joinToString("\n")}"
            run.isEnabled = !state.busy
            val file = ClientSession.graphicsFrame(this@GraphicsTestActivity)
            // Session start deletes the previous frame; no stale image is called a new pass.
            val identity = "${file.lastModified()}:${file.length()}"
            if (!file.isFile) { frame.clearFrame(); shownIdentity = "" }
            else if (!reading && identity != shownIdentity && identity != failureIdentity) {
                reading = true
                reader.execute {
                    val result = runCatching { GraphicsFrame.read(file) }
                    handler.post {
                        reading = false
                        if (!isDestroyed && resumed && "${file.lastModified()}:${file.length()}" == identity) result.fold({ data ->
                            val bitmap = displayedBitmap?.takeIf { it.width == data.width && it.height == data.height }
                                ?: Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888).also { displayedBitmap = it }
                            bitmap.setPixels(data.argb, 0, data.width, 0, 0, data.width, data.height)
                            frame.setImageBitmap(bitmap)
                            frame.frame(data)
                            shownIdentity = identity
                            if (data.sequence <= 3 || data.sequence % 25 == 0) ClientSession.log("[graphics-ui] FRAME_DISPLAYED sequence=${data.sequence} size=${data.width}x${data.height}")
                        }, { failure ->
                            failureIdentity = identity
                            ClientSession.log("[graphics-ui] FRAME_READ_ERROR ${failure.javaClass.simpleName}: ${failure.message}")
                        })
                    }
                }
            }
            handler.postDelayed(this, 33)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ClientSession.initialize(this)
        mode = intent.getStringExtra("mode")?.takeIf { it in listOf("window", "start", "local") } ?: "render"
        if (mode != "render") capture = ControllerCapture(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12) }
        setContentView(column)
        column.addView(TextView(this).apply { text = if (mode == "render") "JVM Graphics Test · 0.10.19" else "LWJGL Window · 0.10.19"; textSize = 23f })
        column.addView(TextView(this).apply { text = if (mode == "window") "90-second LWJGL test: left stick moves triangle; right stick moves cyan cursor; mouse clicks change triangle color. Finish, then export Client Report." else if (mode != "render") "Touch the game to select and drag. Right stick: pointer; A or RT: click; LT: right click. Use Send in the character dialog to continue." else "Expected: orange triangle on blue. Tests LWJGL, GL4ES, shader drawing and resize. No client import needed. This is not a Wurm game window." })
        val controls = LinearLayout(this)
        column.addView(HorizontalScrollView(this).apply { addView(controls) })
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; controls.addView(this) }
        run = button(if (mode == "window") "Run Window / Input Test" else if (mode == "render") "Run Graphics Test" else "Retry Client") {
            if (!ClientSession.snapshot().busy) {
                shownIdentity = ""; failureIdentity = ""; frame.clearFrame()
                startForegroundService(Intent(this, ClientService::class.java).setAction(mode))
            }
        }
        if (mode == "window") button("Finish Window Test") { ClientSession.send("STOP") }
        button("Stop Client Test") { startService(Intent(this, ClientService::class.java).setAction("stop")) }
        button("Back to Client / Export") { finish() }
        status = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        val diagnostics = ScrollView(this).apply { addView(status); visibility = if (mode == "render") android.view.View.VISIBLE else android.view.View.GONE }
        column.addView(diagnostics, LinearLayout.LayoutParams(-1, 0, 1f))
        lateinit var toggle: Button
        if (mode != "render") toggle = button("Show diagnostics") {
            frame.cancelTouch()
            val show = diagnostics.visibility != android.view.View.VISIBLE
            diagnostics.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            toggle.text = if (show) "Expand game" else "Show diagnostics"
        }
        frame = GameFrameView(this, mode != "render")
        column.addView(frame, LinearLayout.LayoutParams(-1,0,2f))
    }
    override fun onResume() { super.onResume(); capture?.resume(); resumed = true; handler.post(refresh) }
    override fun onPause() { frame.cancelTouch(); resumed = false; capture?.pause(); handler.removeCallbacks(refresh); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (!hasFocus) { if (::frame.isInitialized) frame.cancelTouch(); capture?.reset() } }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = capture?.key(event) == true || super.dispatchKeyEvent(event)
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = capture?.motion(event) == true || super.onGenericMotionEvent(event)
    override fun onDestroy() { reader.shutdown(); super.onDestroy() }
}
