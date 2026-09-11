package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.widget.*
import android.view.*
import android.graphics.Color
import java.util.concurrent.Executors

/** Displays only actual readback frames from the JVM, never a stand-in GLES drawing. */
class GraphicsTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val reader = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var frame: GameFrameView
    private lateinit var run: Button
    private lateinit var panel: ScrollView
    private lateinit var gear: Button
    private lateinit var sessionLabel: TextView
    private var panelOpen = false
    private var fullscreen = true
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun updateInput() {
        val enabled = !panelOpen && graphicsDialog?.isShowing != true
        if (!enabled) frame.cancelTouch()
        frame.isEnabled=enabled; capture?.enabled=enabled
    }
    private fun showPanel(show: Boolean) {
        panelOpen=show
        panel.visibility=if (show) View.VISIBLE else View.GONE
        gear.text=if (show) "×" else "⚙"
        gear.contentDescription=if (show) "Close game controls" else "Open game controls. Hold to reset panel opacity."
        updateInput()
        ClientSession.log("[graphics-ui] CONTROLS_OVERLAY open=$show fullscreen=$fullscreen")
    }
    private fun applyFullscreen() {
        window.setDecorFitsSystemWindows(!fullscreen)
        window.insetsController?.apply {
            systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
        }
    }
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
        updateInput()
        graphicsDialog?.setOnDismissListener { graphicsDialog=null; updateInput(); applyFullscreen() }
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
            sessionLabel.visibility=if (!state.busy || shownSequence == 0) View.VISIBLE else View.GONE
            if (sessionLabel.isShown) {
                val message=if (!state.busy) "${state.phase} · Tap ⚙ to start or retry" else "${state.phase} · Tap ⚙ for progress"
                if (sessionLabel.text.toString() != message) sessionLabel.text=message
            }
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
        val prefs=getSharedPreferences("client-settings",MODE_PRIVATE)
        fullscreen=prefs.getBoolean("viewer-fullscreen",mode != "render")
        val root=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        frame=GameFrameView(this,mode != "render")
        root.addView(frame,FrameLayout.LayoutParams(-1,-1))
        sessionLabel=TextView(this).apply {
            textSize=14f; setTextColor(Color.WHITE); setBackgroundColor(0xb0000000.toInt()); setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        root.addView(sessionLabel,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START))
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(12))
            setBackgroundColor(0xff202124.toInt()); isClickable=true
        }
        panel=ScrollView(this).apply { addView(column); isClickable=true; elevation=dp(8).toFloat() }
        root.addView(panel,FrameLayout.LayoutParams(dp(360),-2,Gravity.TOP or Gravity.END))
        fun label(value: String, size: Float = 14f) = TextView(this).apply {
            text=value; textSize=size; setTextColor(Color.WHITE); column.addView(this)
        }
        label(if (mode == "render") "JVM Graphics Test · 0.10.30" else "Game controls · 0.10.30",20f)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text=label; setOnClickListener { action() }; column.addView(this,LinearLayout.LayoutParams(-1,-2))
        }
        if (mode in listOf("start","local")) button("Graphics settings · resolution") { showGraphicsSettings() }
        column.addView(CheckBox(this).apply {
            text="Fullscreen game"; setTextColor(Color.WHITE); isChecked=fullscreen
            setOnCheckedChangeListener { _, value ->
                fullscreen=value; prefs.edit().putBoolean("viewer-fullscreen",value).apply(); applyFullscreen()
            }
        })
        val opacityLabel=label("")
        val opacity=SeekBar(this).apply {
            max=100; progress=prefs.getInt("overlay-opacity",85).coerceIn(0,100)
            contentDescription="Control panel opacity"
            column.addView(this,LinearLayout.LayoutParams(-1,dp(48)))
        }
        fun applyOpacity(value: Int) { panel.alpha=value/100f; opacityLabel.text="Panel opacity: $value% · Hold gear to reset" }
        applyOpacity(opacity.progress)
        opacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { applyOpacity(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { prefs.edit().putInt("overlay-opacity",opacity.progress).apply() }
        })
        button("Resume game / close controls") { showPanel(false) }
        run=button(if (mode == "window") "Run Window / Input Test" else if (mode == "render") "Run Graphics Test" else "Retry Client") {
            if (!ClientSession.snapshot().busy) {
                lastReadError=""; readRetryAt=0; frame.clearFrame()
                startForegroundService(Intent(this,ClientService::class.java).setAction(mode))
                if (mode != "render") showPanel(false)
            }
        }
        if (mode == "window") button("Finish Window Test") { ClientSession.send("STOP") }
        button("Stop client") { startService(Intent(this,ClientService::class.java).setAction("stop")) }
        button("Back to client / export reports") { finish() }
        if (mode in listOf("start","local")) button("Restore game UI") {
            frame.cancelTouch(); capture?.reset()
            val sent=ClientSession.send("HUD restore-button")
            Toast.makeText(this,if (sent) "Game UI restore requested." else "Start the client first.",Toast.LENGTH_SHORT).show()
        }
        label(if (mode == "window") "90-second test: left stick moves triangle; right stick moves cursor; clicks change color."
            else if (mode == "render") "Expected: orange triangle on blue. No client import needed."
            else "Close these controls to play. Touch selects and drags; right stick moves pointer; A or RT clicks; LT right-clicks. Swipe from an edge for Android system bars.")
        status=TextView(this).apply { textSize=12f; setTextColor(Color.WHITE); setTextIsSelectable(true); visibility=View.GONE }
        lateinit var diagnostics: Button
        diagnostics=button("Show diagnostics") {
            val show=status.visibility != View.VISIBLE
            status.visibility=if (show) View.VISIBLE else View.GONE
            diagnostics.text=if (show) "Hide diagnostics" else "Show diagnostics"
        }
        column.addView(status)
        gear=Button(this).apply {
            textSize=26f; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0; setPadding(0,0,0,0)
            elevation=dp(10).toFloat()
            setOnClickListener { showPanel(!panelOpen) }
            setOnLongClickListener {
                opacity.progress=85; prefs.edit().putInt("overlay-opacity",85).apply(); showPanel(true); true
            }
        }
        root.addView(gear,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP or Gravity.END))
        var safeTop=0; var safeRight=0; var safeLeft=0; var safeBottom=0
        fun layoutOverlay() {
            if (root.width == 0 || root.height == 0) return
            gear.layoutParams=(gear.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin=safeTop+dp(8); rightMargin=safeRight+dp(8)
            }
            panel.layoutParams=(panel.layoutParams as FrameLayout.LayoutParams).apply {
                width=minOf(dp(360),root.width-safeLeft-safeRight-dp(16)).coerceAtLeast(1)
                height=minOf(dp(560),root.height-safeTop-safeBottom-dp(76)).coerceAtLeast(1)
                topMargin=safeTop+dp(68); rightMargin=safeRight+dp(8)
            }
            sessionLabel.layoutParams=(sessionLabel.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin=safeLeft+dp(8); bottomMargin=safeBottom+dp(8)
            }
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            // Root already fits system bars when fullscreen is off; only cutouts need extra space.
            val safe=insets.getInsets(WindowInsets.Type.displayCutout())
            safeTop=safe.top; safeRight=safe.right; safeLeft=safe.left; safeBottom=safe.bottom
            layoutOverlay(); insets
        }
        root.addOnLayoutChangeListener { _, l,t,r,b,ol,ot,or,ob ->
            if (r-l != or-ol || b-t != ob-ot) layoutOverlay()
        }
        showPanel(savedInstanceState?.getBoolean("panel-open") ?: (mode == "render" || mode == "window"))
        applyFullscreen()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("panel-open",panelOpen); super.onSaveInstanceState(outState) }
    @Deprecated("Platform back callback retained for this API 33 preview")
    override fun onBackPressed() { showPanel(!panelOpen) }

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
        if (hasFocus && ::gear.isInitialized) applyFullscreen()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = capture?.key(event) == true || super.dispatchKeyEvent(event)
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = capture?.motion(event) == true || super.onGenericMotionEvent(event)
    override fun onDestroy() { graphicsDialog?.dismiss(); reader.shutdown(); super.onDestroy() }
}
