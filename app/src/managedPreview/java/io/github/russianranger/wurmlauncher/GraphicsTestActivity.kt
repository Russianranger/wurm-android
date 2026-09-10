package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
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
    private var lastReadError = ""
    private var readRetryAt = 0L
    private var resumed = false
    private var epoch = -1L
    private var shownSequence = 0
    private var statusAt = 0L
    private var statsAt = 0L
    private var displayed = 0
    private var rawCopy = false
    private var focusLost = false
    private var restoreHudOnFocus = false
    private var settingsSeen = ClientSession.settingsRequests
    private var noticeSeen = ""
    private var graphicsDialog: AlertDialog? = null
    private fun showGraphicsSettings() {
        if (graphicsDialog?.isShowing == true) return
        frame.cancelTouch(); capture?.reset()
        graphicsDialog = GraphicsSettingsDialog.show(this,true)
    }
    private fun supportsRgbaCopy(): Boolean = runCatching {
        val probe=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888)
        try {
            probe.setHasAlpha(false)
            probe.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(byteArrayOf(-1,0,0,-1,0,0,-1,-1)))
            probe.getPixel(0,0) == android.graphics.Color.RED && probe.getPixel(1,0) == android.graphics.Color.BLUE
        } finally { probe.recycle() }
    }.getOrDefault(false)
    private val refresh = object : Runnable {
        override fun run() {
            val state = ClientSession.snapshot()
            val now=SystemClock.elapsedRealtime()
            if (now-statusAt >= 500) {
                if (status.isShown) {
                    val value="${state.phase}: ${state.detail}\n${ClientSession.recent().lines().takeLast(8).joinToString("\n")}"
                    if (status.text.toString() != value) status.text=value
                }
                statusAt=now
                if (noticeSeen != ClientSession.graphicsNotice) {
                    noticeSeen=ClientSession.graphicsNotice
                    if (noticeSeen.isNotEmpty()) Toast.makeText(this@GraphicsTestActivity,noticeSeen,Toast.LENGTH_SHORT).show()
                }
            }
            if (mode in listOf("start","local") && settingsSeen != ClientSession.settingsRequests) {
                settingsSeen=ClientSession.settingsRequests; showGraphicsSettings()
            }
            run.isEnabled = !state.busy
            if (restoreHudOnFocus && hasWindowFocus() && ClientSession.inputReady()) {
                if (ClientSession.send("HUD restore-focus")) restoreHudOnFocus=false
            }
            val file = ClientSession.graphicsFrame(this@GraphicsTestActivity)
            val currentEpoch=ClientSession.frameEpoch
            if (epoch != currentEpoch) {
                epoch=currentEpoch; shownSequence=0; lastReadError=""; readRetryAt=0; frame.clearFrame()
                statsAt=now; displayed=0
            }
            if (!file.isFile) { if (shownSequence != 0) frame.clearFrame() }
            else if (!reading && now >= readRetryAt) {
                reading = true
                val previousSequence=shownSequence
                reader.execute {
                    val readStart=SystemClock.elapsedRealtimeNanos()
                    val result = runCatching { GraphicsFrame.readNewer(file,previousSequence) }
                    val readMs=(SystemClock.elapsedRealtimeNanos()-readStart)/1e6
                    handler.post {
                        reading = false
                        // Atomic rename makes an opened frame complete even if a newer one arrives.
                        // Reject prior sessions/out-of-order frames, not a valid completed read.
                        if (!isDestroyed && resumed && ClientSession.frameEpoch == currentEpoch) result.fold({ data ->
                            lastReadError=""
                            if (data == null) return@fold
                            if (data.sequence <= shownSequence) return@fold
                            val bitmap = displayedBitmap?.takeIf { it.width == data.width && it.height == data.height }
                                ?: Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888).also { it.setHasAlpha(false); displayedBitmap = it }
                            val direct=rawCopy && data.rawRgba != null && bitmap.rowBytes == data.width*4
                            if (direct) bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(data.rawRgba!!))
                            else bitmap.setPixels(data.decodedArgb(), 0, data.width, 0, 0, data.width, data.height)
                            frame.setImageBitmap(bitmap)
                            frame.frame(data,direct)
                            shownSequence=data.sequence; displayed++
                            if (data.sequence <= 3 || data.sequence % 150 == 0) ClientSession.log("[graphics-ui] FRAME_DISPLAYED sequence=${data.sequence} size=${data.width}x${data.height} readMs=$readMs")
                        }, { failure ->
                            // Retry by time, not file identity: a later valid frame may share its mtime.
                            readRetryAt=SystemClock.elapsedRealtime()+250
                            val message="${failure.javaClass.simpleName}: ${failure.message}"
                            if (lastReadError != message) ClientSession.log("[graphics-ui] FRAME_READ_ERROR $message")
                            lastReadError=message
                        })
                    }
                }
            }
            if (now-statsAt >= 5000) {
                if (state.busy) ClientSession.log("[graphics-ui] UI_TIMING displayedFps=${java.lang.String.format(java.util.Locale.ROOT,"%.1f",displayed*1000.0/(now-statsAt))} lastSequence=$shownSequence rawCopy=$rawCopy")
                statsAt=now; displayed=0
            }
            handler.postDelayed(this, 16)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ClientSession.initialize(this)
        rawCopy=supportsRgbaCopy()
        mode = intent.getStringExtra("mode")?.takeIf { it in listOf("window", "start", "local") } ?: "render"
        if (mode != "render") capture = ControllerCapture(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12) }
        setContentView(column)
        column.addView(TextView(this).apply { text = if (mode == "render") "JVM Graphics Test · 0.10.21" else "LWJGL Window · 0.10.21"; textSize = 23f })
        column.addView(TextView(this).apply { text = if (mode == "window") "90-second LWJGL test: left stick moves triangle; right stick moves cyan cursor; mouse clicks change triangle color. Finish, then export Client Report." else if (mode != "render") "Touch the game to select and drag. Right stick: pointer; A or RT: click; LT: right click. Use Send in the character dialog to continue." else "Expected: orange triangle on blue. Tests LWJGL, GL4ES, shader drawing and resize. No client import needed. This is not a Wurm game window." })
        val controls = LinearLayout(this)
        column.addView(HorizontalScrollView(this).apply { addView(controls) })
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; controls.addView(this) }
        run = button(if (mode == "window") "Run Window / Input Test" else if (mode == "render") "Run Graphics Test" else "Retry Client") {
            if (!ClientSession.snapshot().busy) {
                lastReadError = ""; readRetryAt=0; frame.clearFrame()
                startForegroundService(Intent(this, ClientService::class.java).setAction(mode))
            }
        }
        if (mode == "window") button("Finish Window Test") { ClientSession.send("STOP") }
        button("Stop Client Test") { startService(Intent(this, ClientService::class.java).setAction("stop")) }
        button("Back to Client / Export") { finish() }
        if (mode in listOf("start","local")) button("Graphics Settings") { showGraphicsSettings() }
        if (mode in listOf("start","local")) button("Restore Game UI") {
            frame.cancelTouch(); capture?.reset()
            val sent=ClientSession.send("HUD restore-button")
            Toast.makeText(this,if (sent) "Game UI restore requested." else "Start the client first.",Toast.LENGTH_SHORT).show()
        }
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
    override fun onResume() {
        super.onResume(); capture?.resume(); resumed = true
        if (mode in listOf("start","local")) restoreHudOnFocus=true
        statsAt=SystemClock.elapsedRealtime(); displayed=0; handler.removeCallbacks(refresh); handler.post(refresh)
        ClientSession.log("[graphics-ui] VIEW_RESUMED sequence=$shownSequence")
    }
    override fun onPause() { frame.cancelTouch(); focusLost=true; resumed = false; capture?.pause(); handler.removeCallbacks(refresh); ClientSession.log("[graphics-ui] VIEW_PAUSED sequence=$shownSequence"); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ClientSession.log("[graphics-ui] VIEW_FOCUS $hasFocus sequence=$shownSequence")
        if (!hasFocus) {
            focusLost=true; if (::frame.isInitialized) frame.cancelTouch(); capture?.reset()
        } else if (focusLost) {
            focusLost=false
            if (mode in listOf("start","local")) restoreHudOnFocus=true
            // Force an actual redraw when Android returns from a recorder/system overlay.
            if (::frame.isInitialized) frame.invalidate()
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = capture?.key(event) == true || super.dispatchKeyEvent(event)
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = capture?.motion(event) == true || super.onGenericMotionEvent(event)
    override fun onDestroy() { graphicsDialog?.dismiss(); reader.shutdown(); super.onDestroy() }
}
