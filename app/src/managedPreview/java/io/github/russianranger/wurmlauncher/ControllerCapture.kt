package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.hardware.input.InputManager
import android.os.*
import android.view.*
import kotlin.math.max

/** Focus-scoped controller capture reusable by the JVM frame viewer and game window. */
class ControllerCapture(private val activity: Activity) : InputManager.InputDeviceListener {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = activity.getSystemService(InputManager::class.java)
    private var previous = 0L
    private var moves = 0
    private val mapper = ControllerMapping(runCatching { ControllerProfile.load(ClientSession.profileFile(activity)) }.getOrElse {
        ClientSession.log("[controller] PROFILE_INVALID ${it.message}; using defaults"); ControllerProfile()
    }) { event ->
        val queued = ClientSession.send(event)
        if (!event.startsWith("MOVE ") || ++moves % 30 == 0)
            ClientSession.log("[controller] TRANSLATE $event queued=$queued sink=lwjgl2-queues")
    }
    private val ticker = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (activity.hasWindowFocus() && previous != 0L) mapper.tick((now-previous)/1_000_000_000f)
            previous=now; handler.postDelayed(this,16)
        }
    }
    fun resume() {
        manager.registerInputDeviceListener(this,handler); previous=0; handler.post(ticker); detect()
    }
    fun pause() { reset(); handler.removeCallbacks(ticker); manager.unregisterInputDeviceListener(this) }
    fun reset() { mapper.reset(); previous=0 }
    private fun detect() { ClientSession.log("[controller] DETECTED ${ControllerSettingsActivity.controllerNames()}") }
    override fun onInputDeviceAdded(deviceId: Int) { detect() }
    override fun onInputDeviceChanged(deviceId: Int) { mapper.releaseDevice(deviceId); detect() }
    override fun onInputDeviceRemoved(deviceId: Int) { mapper.releaseDevice(deviceId); detect() }
    fun key(event: KeyEvent): Boolean {
        val device = event.device
        val physical = device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))
        val name = ControllerTestActivity.BUTTONS[event.keyCode]
        if (physical && name != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            mapper.button(event.deviceId, name, event.action == KeyEvent.ACTION_DOWN && !event.isCanceled)
            return true // Do not let mapped D-pad/back presses operate Android widgets as well.
        }
        return false
    }
    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device ?: return false
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
}
