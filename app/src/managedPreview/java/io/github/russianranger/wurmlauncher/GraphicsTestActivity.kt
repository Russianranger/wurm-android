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
    private lateinit var frame: GameFrameView
    private lateinit var run: Button
    private lateinit var panel: ScrollView
    private lateinit var gear: Button
    private lateinit var sessionLabel: TextView
    private lateinit var keyboardBar: LinearLayout
    private lateinit var keyboardText: EditText
    private lateinit var keyboardButton: Button
    private var keyboardOpen=false
    private val hardware=PhysicalKeyboard(ClientSession::send)
    private var resetOpacity: ()->Unit={}
    private var layoutControls: ()->Unit={}
    private var panelOpen = false
    private var fullscreen = true
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun updateInput() {
        val enabled = !panelOpen && graphicsDialog?.isShowing != true
        if (!enabled) { frame.cancelTouch(); hardware.reset() }
        frame.isEnabled=enabled; capture?.enabled=enabled && !keyboardOpen
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
    private fun applyPanelOpacity() {
        val opacity=getSharedPreferences("client-settings",MODE_PRIVATE).getInt("overlay-opacity",85).coerceIn(0,100)
        // Fade only the surface. Text and controls stay readable, including at zero percent.
        val color=getColor(R.color.wurm_surface)
        (panel.getChildAt(0) as LinearLayout).setBackgroundColor(Color.argb(opacity*255/100,Color.red(color),Color.green(color),Color.blue(color)))
        panel.alpha=1f
    }
    private fun displaySettings() {
        val prefs=getSharedPreferences("client-settings",MODE_PRIVATE)
        val body=LauncherUi.column(this)
        body.addView(CheckBox(this).apply {
            text="Fullscreen game"; isChecked=fullscreen
            setOnCheckedChangeListener { _,value -> fullscreen=value; prefs.edit().putBoolean("viewer-fullscreen",value).apply(); applyFullscreen() }
        })
        body.addView(CheckBox(this).apply {
            text="Gear on the left"; isChecked=prefs.getBoolean("gear-left",false)
            setOnCheckedChangeListener { _,value -> prefs.edit().putBoolean("gear-left",value).apply(); layoutControls() }
        })
        val opacityLabel=LauncherUi.label(body,"")
        body.addView(SeekBar(this).apply {
            max=100; progress=prefs.getInt("overlay-opacity",85).coerceIn(0,100)
            opacityLabel.text="Panel background opacity: $progress%"
            contentDescription="Panel background opacity"
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean) {
                    opacityLabel.text="Panel background opacity: $value%"
                    if(fromUser) { prefs.edit().putInt("overlay-opacity",value).apply(); applyPanelOpacity() }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        },LinearLayout.LayoutParams(-1,dp(48)))
        LauncherUi.label(body,"Text remains readable. Hold the gear to reset opacity. Render resolution is under Graphics and defaults to 1280 × 720.")
        AlertDialog.Builder(this).setTitle("Display & overlay").setView(body).setPositiveButton("Done",null).show()
    }
    private fun tapGameKey(code: Int) {
        if(!ClientSession.send("KEY $code 1")) Toast.makeText(this,"Game input is not ready.",Toast.LENGTH_SHORT).show()
        else ClientSession.send("KEY $code 0")
    }
    private fun insertDraft(): Boolean {
        val value=keyboardText.text.toString()
        if(!ClientSession.sendText(value)) {
            Toast.makeText(this,"Text was not sent. Keep the draft and retry when game input is ready.",Toast.LENGTH_SHORT).show()
            return false
        }
        keyboardText.text.clear(); return true
    }
    private fun setKeyboard(show: Boolean) {
        if(!::keyboardBar.isInitialized) return
        if(show && !ClientSession.inputReady()) { Toast.makeText(this,"Wait for game input to be ready.",Toast.LENGTH_SHORT).show(); return }
        hardware.reset(); frame.cancelTouch(); capture?.reset()
        keyboardOpen=show; keyboardBar.visibility=if(show) View.VISIBLE else View.GONE
        if(::keyboardButton.isInitialized) keyboardButton.text=if(show) "Hide keyboard" else "Show keyboard"
        val ime=getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        if(show) {
            showPanel(false); keyboardText.requestFocus()
            keyboardText.post { if(keyboardOpen && hasWindowFocus()) ime.showSoftInput(keyboardText,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
        } else { ime.hideSoftInputFromWindow(keyboardText.windowToken,0); keyboardText.clearFocus() }
        updateInput(); layoutControls()
    }
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
            run.visibility=if(state.busy && mode in listOf("start","local")) View.GONE else View.VISIBLE
            sessionLabel.visibility=if (!state.busy || shownSequence == 0) View.VISIBLE else View.GONE
            if (sessionLabel.isShown) {
                val message=if (!state.busy) "${state.phase} · Tap ⚙ for session controls" else "${state.phase} · Tap ⚙ for progress"
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rawCopy=supportsRgbaCopy()
        mode = intent.getStringExtra("mode")?.takeIf { it in listOf("window", "start", "local") } ?: "render"
        if (mode != "render") capture = ControllerCapture(this,hardware::releaseDevice)
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
            setBackgroundColor(getColor(R.color.wurm_surface)); isClickable=true
        }
        panel=ScrollView(this).apply { addView(column); isClickable=true; elevation=dp(8).toFloat() }
        root.addView(panel,FrameLayout.LayoutParams(dp(360),-2,Gravity.TOP or Gravity.END))
        fun label(value: String, size: Float = 14f) = TextView(this).apply {
            text=value; textSize=size; setTextColor(Color.WHITE); column.addView(this)
        }
        label(if (mode == "render") "JVM Graphics Test · 0.10.39" else "Game controls · 0.10.39",20f)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text=label; setOnClickListener { action() }; column.addView(this,LinearLayout.LayoutParams(-1,-2))
        }
        button("Resume game") { showPanel(false) }
        if (mode in listOf("start","local")) button("Graphics") { showGraphicsSettings() }
        if(mode!="render") {
            keyboardButton=button("Show keyboard") { setKeyboard(!keyboardOpen) }
            button("Controls") {
                AlertDialog.Builder(this).setTitle("Controls").setItems(arrayOf("Game keybindings","Controller mappings","Touch & keyboard help")) { _,which ->
                    frame.cancelTouch(); capture?.reset(); hardware.reset()
                    when(which) {
                        0 -> startActivity(Intent(this,GameKeybindsActivity::class.java))
                        1 -> startActivity(Intent(this,ControllerSettingsActivity::class.java))
                        else -> AlertDialog.Builder(this).setTitle("Touch & keyboard")
                            .setMessage("Touch selects and drags. Right stick moves the pointer; A/RT clicks, LT right-clicks.\n\nSelect a text field in the game, then use Show keyboard. Compose text in the Android bar and tap Insert; Enter submits in the game. Backspace edits the selected game field when the draft is empty. Hide keyboard keeps an unsent draft. Hardware keyboards type directly when the text bar is closed; mouse hover, buttons and wheel are supported.")
                            .setPositiveButton("Close",null).show()
                    }
                }.show()
            }
        }
        button("Display & overlay") { displaySettings() }
        resetOpacity={ prefs.edit().putInt("overlay-opacity",85).apply(); applyPanelOpacity() }
        applyPanelOpacity()
        button("Back to launcher") { setKeyboard(false); startActivity(ManagedActivity.tabIntent(this,ManagedActivity.CLIENT)); finish() }
        run=button(if(mode=="window") "Run window / input test" else if(mode=="render") "Run graphics test" else "Retry client") {
            if(!ClientSession.snapshot().busy) {
                lastReadError=""; readRetryAt=0; frame.clearFrame()
                startForegroundService(Intent(this,ClientService::class.java).setAction(mode))
                if(mode!="render") showPanel(false)
            }
        }
        if (mode == "window") button("Finish Window Test") { ClientSession.send("STOP") }
        button("Leave game") {
            AlertDialog.Builder(this).setTitle("Leave game")
                .setMessage("Stop the client, or stop it and request a normal server save and stop. Returning to the launcher keeps both running.")
                .setPositiveButton("Stop client") { _,_->setKeyboard(false); startService(Intent(this,ClientService::class.java).setAction("stop")); showPanel(true) }
                .setNeutralButton("Stop both") { _,_->setKeyboard(false); startService(Intent(this,ClientService::class.java).setAction("stop")); ManagedSession.stop(); showPanel(true) }
                .setNegativeButton("Cancel",null).show()
        }
        button("Support") {
            AlertDialog.Builder(this).setTitle("Game support").setItems(arrayOf("Restore game UI","Diagnostics & reports")) { _,which ->
                if(which==0) { frame.cancelTouch(); capture?.reset(); hardware.reset(); ClientSession.send("HUD restore-button") }
                else { setKeyboard(false); startActivity(ManagedActivity.tabIntent(this,ManagedActivity.DIAGNOSTICS)); finish() }
            }.show()
        }
        keyboardBar=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),0,dp(8),0); setBackgroundColor(getColor(R.color.wurm_surface)); visibility=View.GONE }
        keyboardText=EditText(this).apply {
            hint="Select a game text field, then compose here"; setSingleLine()
            filters=arrayOf(android.text.InputFilter.LengthFilter(240))
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(savedInstanceState?.getString("keyboard-draft").orEmpty())
            setOnEditorActionListener { _,action,_ -> if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { insertDraft(); true } else false }
            keyboardBar.addView(this,LinearLayout.LayoutParams(-1,dp(48)))
        }
        val typing=LinearLayout(this); keyboardBar.addView(typing)
        fun typingButton(title: String,action: ()->Unit) { typing.addView(Button(this).apply {
            text=title; isAllCaps=false; setOnClickListener { action() }
        },LinearLayout.LayoutParams(0,dp(48),1f)) }
        typingButton("Insert") { insertDraft() }
        typingButton("Enter") { if(keyboardText.text.isEmpty() || insertDraft()) tapGameKey(28) }
        typingButton("Backspace") {
            val text=keyboardText.text
            if(text.isEmpty()) tapGameKey(14) else {
                val end=keyboardText.selectionEnd.coerceAtLeast(0); val start=keyboardText.selectionStart.coerceAtLeast(0)
                if(start!=end) text.delete(minOf(start,end),maxOf(start,end))
                else if(end>0) text.delete(Character.offsetByCodePoints(text,end,-1),end)
            }
        }
        typingButton("Hide") { setKeyboard(false) }
        root.addView(keyboardBar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        gear=Button(this).apply {
            textSize=26f; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0; setPadding(0,0,0,0)
            elevation=dp(10).toFloat()
            setOnClickListener { showPanel(!panelOpen) }
            setOnLongClickListener {
                resetOpacity(); showPanel(true); true
            }
        }
        root.addView(gear,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP or Gravity.END))
        var safeTop=0; var safeRight=0; var safeLeft=0; var safeBottom=0
        fun layoutOverlay() {
            if (root.width == 0 || root.height == 0) return
            gear.layoutParams=(gear.layoutParams as FrameLayout.LayoutParams).apply {
                gravity=Gravity.TOP or if(prefs.getBoolean("gear-left",false)) Gravity.START else Gravity.END
                topMargin=safeTop+dp(8); rightMargin=safeRight+dp(8); leftMargin=safeLeft+dp(8)
            }
            panel.layoutParams=(panel.layoutParams as FrameLayout.LayoutParams).apply {
                width=minOf(dp(360),root.width-safeLeft-safeRight-dp(16)).coerceAtLeast(1)
                height=minOf(dp(560),root.height-root.paddingBottom-safeTop-safeBottom-dp(if(keyboardOpen) 180 else 76)).coerceAtLeast(1)
                gravity=Gravity.TOP or if(prefs.getBoolean("gear-left",false)) Gravity.START else Gravity.END
                topMargin=safeTop+dp(68); rightMargin=safeRight+dp(8); leftMargin=safeLeft+dp(8)
            }
            sessionLabel.layoutParams=(sessionLabel.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin=safeLeft+dp(8); bottomMargin=safeBottom+dp(8)
            }
        }
        layoutControls={ layoutOverlay() }
        root.setOnApplyWindowInsetsListener { _, insets ->
            // Root already fits system bars when fullscreen is off; only cutouts need extra space.
            val safe=insets.getInsets(WindowInsets.Type.displayCutout())
            safeTop=safe.top; safeRight=safe.right; safeLeft=safe.left; safeBottom=safe.bottom
            val ime=insets.getInsets(WindowInsets.Type.ime())
            val system=insets.getInsets(WindowInsets.Type.systemBars())
            if(fullscreen) {
                root.setPadding(0,0,0,maxOf(ime.bottom,system.bottom))
            } else root.setPadding(0,0,0,0)
            layoutOverlay(); insets
        }
        root.addOnLayoutChangeListener { _, l,t,r,b,ol,ot,or,ob ->
            if (r-l != or-ol || b-t != ob-ot) layoutOverlay()
        }
        showPanel(savedInstanceState?.getBoolean("panel-open") ?: (mode == "render" || mode == "window"))
        applyFullscreen()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("panel-open",panelOpen); outState.putString("keyboard-draft",keyboardText.text.toString()); super.onSaveInstanceState(outState) }
    @Deprecated("Platform back callback retained for this API 33 preview")
    override fun onBackPressed() { if(keyboardOpen) setKeyboard(false) else showPanel(!panelOpen) }

    override fun onResume() {
        super.onResume(); capture?.resume(); resumed = true
        if (mode in listOf("start","local")) restoreHudOnFocus=true
        statsAt=SystemClock.elapsedRealtime(); displayed=0; handler.removeCallbacks(refresh); handler.post(refresh)
        ClientSession.log("[graphics-ui] VIEW_RESUMED sequence=$shownSequence")
    }
    override fun onPause() { setKeyboard(false); hardware.reset(); frame.cancelTouch(); focusLost=true; resumed = false; capture?.pause(); handler.removeCallbacks(refresh); ClientSession.log("[graphics-ui] VIEW_PAUSED sequence=$shownSequence"); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ClientSession.log("[graphics-ui] VIEW_FOCUS $hasFocus sequence=$shownSequence")
        if (!hasFocus) {
            focusLost=true; hardware.reset(); if (::frame.isInitialized) frame.cancelTouch(); capture?.reset()
        } else if (focusLost) {
            focusLost=false
            if (mode in listOf("start","local")) restoreHudOnFocus=true
            // Force an actual redraw when Android returns from a recorder/system overlay.
            if (::frame.isInitialized) frame.invalidate()
        }
        if (hasFocus && ::gear.isInitialized) applyFullscreen()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(capture?.key(event)==true) return true
        if(!panelOpen && !keyboardOpen && graphicsDialog?.isShowing!=true && hasWindowFocus() && ClientSession.inputReady() && hardware.key(event)) return true
        return super.dispatchKeyEvent(event)
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = capture?.motion(event) == true || super.onGenericMotionEvent(event)
    override fun onDestroy() { graphicsDialog?.dismiss(); reader.shutdown(); super.onDestroy() }
}
