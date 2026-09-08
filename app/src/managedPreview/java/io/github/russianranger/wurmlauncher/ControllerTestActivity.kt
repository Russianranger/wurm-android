package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.hardware.input.InputManager
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.max

/** Captures physical gamepad input only while this owned test surface has focus. */
class ControllerTestActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var mapper: ControllerMapping
    private lateinit var status: TextView
    private lateinit var canvas: CursorView
    private lateinit var surface: ClientSurfaceProbe
    private val handler = Handler(Looper.getMainLooper())
    private var previous = 0L
    private var moves = 0
    private var ticks = 0
    private var names = ""
    private val ticker = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (hasWindowFocus() && previous != 0L) mapper.tick((now - previous) / 1_000_000_000f)
            previous = now; handler.postDelayed(this, 16)
            if (++ticks % 30 == 0) status.text = "$names\nJVM receiver: ${if (ClientSession.inputReady()) "READY" else ClientSession.snapshot().phase + " (not receiving)"}"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ClientSession.initialize(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12) }
        setContentView(column)
        status = TextView(this).apply { column.addView(this) }
        column.addView(TextView(this).apply { text = "Input diagnostic only: desktop key/mouse events cross into the JVM receiver. No Wurm renderer is attached. Start the receiver, then move both sticks and press controls. Touch Exit to leave." })
        fun button(text: String, action: () -> Unit) { column.addView(Button(this).apply { this.text = text; isFocusable = false; setOnClickListener { action() } }) }
        button("Start JVM Input Receiver") {
            if (!ClientSession.snapshot().busy) startForegroundService(Intent(this, ClientService::class.java).setAction("input"))
            else Toast.makeText(this, "Stop the current client operation first", Toast.LENGTH_SHORT).show()
        }
        button("Stop Client Receiver") { startService(Intent(this, ClientService::class.java).setAction("stop")) }
        button("Exit Input Test") { finish() }
        val frame = FrameLayout(this).apply { column.addView(this, LinearLayout.LayoutParams(-1, 0, 1f)) }
        surface = ClientSurfaceProbe(this).apply { frame.addView(this) }
        canvas = CursorView().apply { frame.addView(this); isFocusableInTouchMode = true; requestFocus() }
        mapper = ControllerMapping(runCatching { ControllerProfile.load(ClientSession.profileFile(this)) }.getOrElse {
            ClientSession.log("[input] PROFILE_INVALID ${it.message}; diagnostic using defaults"); ControllerProfile()
        }) { event ->
            val queued = ClientSession.send(event); canvas.accept(event)
            if (!event.startsWith("MOVE ") || ++moves % 30 == 0) ClientSession.log("[controller] TRANSLATE $event; queued=$queued; sink=diagnostic")
        }
    }
    override fun onResume() {
        super.onResume(); getSystemService(InputManager::class.java).registerInputDeviceListener(this, handler)
        surface.onResume(); detect(); previous = 0; handler.post(ticker)
    }
    override fun onPause() {
        mapper.reset(); surface.onPause(); ClientSession.log("[graphics] ANDROID_SURFACE_PAUSED")
        handler.removeCallbacks(ticker); getSystemService(InputManager::class.java).unregisterInputDeviceListener(this); super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::mapper.isInitialized) mapper.reset()
    }
    private fun detect() {
        names = ControllerSettingsActivity.controllerNames(); status.text = names; ClientSession.log("[controller] DETECTED $names")
        InputDevice.getDeviceIds().forEach { id -> InputDevice.getDevice(id)?.let { device ->
            if (device.supportsSource(InputDevice.SOURCE_JOYSTICK)) device.motionRanges.forEach { range ->
                ClientSession.log("[controller] AXIS device=$id ${MotionEvent.axisToString(range.axis)} min=${range.min} max=${range.max} flat=${range.flat}")
            }
        } }
    }
    override fun onInputDeviceAdded(deviceId: Int) { detect() }
    override fun onInputDeviceChanged(deviceId: Int) { mapper.releaseDevice(deviceId); detect() }
    override fun onInputDeviceRemoved(deviceId: Int) { mapper.releaseDevice(deviceId); detect() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val physical = device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))
        val name = BUTTONS[event.keyCode]
        if (physical && name != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            mapper.button(event.deviceId, name, event.action == KeyEvent.ACTION_DOWN && !event.isCanceled)
            return true // Do not let mapped D-pad/back presses operate Android widgets as well.
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event)
        val device = event.device ?: return super.onGenericMotionEvent(event)
        fun value(axis: Int) = event.getAxisValue(axis)
        fun has(axis: Int) = device.getMotionRange(axis, event.source) != null
        val rx = if (has(MotionEvent.AXIS_Z) && has(MotionEvent.AXIS_RZ)) MotionEvent.AXIS_Z else MotionEvent.AXIS_RX
        val ry = if (rx == MotionEvent.AXIS_Z) MotionEvent.AXIS_RZ else MotionEvent.AXIS_RY
        val flat = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, rx, ry).maxOf { device.getMotionRange(it, event.source)?.flat ?: 0f }
        mapper.axes(event.deviceId, value(MotionEvent.AXIS_X), value(MotionEvent.AXIS_Y), value(rx), value(ry),
            value(MotionEvent.AXIS_HAT_X), value(MotionEvent.AXIS_HAT_Y), max(value(MotionEvent.AXIS_LTRIGGER), value(MotionEvent.AXIS_BRAKE)),
            max(value(MotionEvent.AXIS_RTRIGGER), value(MotionEvent.AXIS_GAS)), flat)
        return true
    }
    private inner class CursorView : View(this@ControllerTestActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var x = 100f; private var y = 100f; private var event = "Waiting for controller input"
        fun accept(line: String) {
            if (line.startsWith("MOVE ")) {
                val p = line.split(' '); x = (x + p[1].toFloat()).coerceIn(0f, width.toFloat()); y = (y - p[2].toFloat()).coerceIn(0f, height.toFloat())
            } else event = line
            invalidate()
        }
        override fun onDraw(c: Canvas) {
            paint.color = Color.CYAN; c.drawCircle(x,y,10f,paint)
            paint.color = Color.WHITE; paint.textSize = 22f; c.drawText(event, 12f, 28f, paint)
        }
    }
    companion object {
        val BUTTONS = mapOf(KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_B to "B", KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y",
            KeyEvent.KEYCODE_BUTTON_L1 to "LB", KeyEvent.KEYCODE_BUTTON_R1 to "RB", KeyEvent.KEYCODE_BUTTON_L2 to "LT", KeyEvent.KEYCODE_BUTTON_R2 to "RT",
            KeyEvent.KEYCODE_BUTTON_THUMBL to "L3", KeyEvent.KEYCODE_BUTTON_THUMBR to "R3", KeyEvent.KEYCODE_BUTTON_START to "Start", KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
            KeyEvent.KEYCODE_BUTTON_MODE to "Mode", KeyEvent.KEYCODE_DPAD_UP to "DpadUp", KeyEvent.KEYCODE_DPAD_DOWN to "DpadDown", KeyEvent.KEYCODE_DPAD_LEFT to "DpadLeft", KeyEvent.KEYCODE_DPAD_RIGHT to "DpadRight",
            KeyEvent.KEYCODE_BACK to "B") + (1..16).associate { (KeyEvent.KEYCODE_BUTTON_1 + it - 1) to "Aux$it" }
    }
}
