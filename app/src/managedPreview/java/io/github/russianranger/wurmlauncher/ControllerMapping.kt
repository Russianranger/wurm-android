package io.github.russianranger.wurmlauncher

import java.io.File
import java.util.Properties
import kotlin.math.*

/** Renderer-independent Android-input -> LWJGL2 key / desktop mouse protocol. */
data class ControllerProfile(
    val bindings: Map<String, String> = DEFAULTS,
    val deadZone: Float = .18f,
    val moveThreshold: Float = .45f,
    val mouseSpeed: Float = 700f,
    val invertY: Boolean = false
) {
    fun validate() {
        require(deadZone.isFinite() && deadZone in .05f.. .8f && moveThreshold.isFinite() && moveThreshold in .1f.. .95f)
        require(mouseSpeed.isFinite() && mouseSpeed in 50f..2000f)
        require(bindings.keys.all { it in DEFAULTS } && bindings.values.all { it in ACTIONS.values })
    }
    fun save(file: File) {
        validate(); file.parentFile!!.mkdirs()
        val p = Properties().apply {
            bindings.forEach { (k,v) -> setProperty("bind.$k", v) }
            setProperty("deadZone", deadZone.toString()); setProperty("moveThreshold", moveThreshold.toString())
            setProperty("mouseSpeed", mouseSpeed.toString()); setProperty("invertY", invertY.toString())
        }
        val tmp = File(file.parentFile, file.name + ".pending")
        tmp.outputStream().use { p.store(it, "Wurm handheld controller profile v1") }
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    companion object {
        val ACTIONS = linkedMapOf("Unbound" to "NONE", "Left mouse" to "BUTTON:0", "Right mouse" to "BUTTON:1",
            "Middle mouse" to "BUTTON:2", "Wheel up" to "WHEEL:120", "Wheel down" to "WHEEL:-120").apply {
            val keys = linkedMapOf("Escape" to 1, "Space" to 57, "Enter" to 28, "Tab" to 15, "Shift" to 42,
                "Ctrl" to 29, "Alt" to 56, "Backspace" to 14, "Up" to 200, "Down" to 208, "Left" to 203, "Right" to 205)
            "1234567890".forEachIndexed { i,c -> keys[c.toString()] = i+2 }
            "QWERTYUIOP".forEachIndexed { i,c -> keys[c.toString()] = i+16 }
            "ASDFGHJKL".forEachIndexed { i,c -> keys[c.toString()] = i+30 }
            "ZXCVBNM".forEachIndexed { i,c -> keys[c.toString()] = i+44 }
            (1..10).forEach { keys["F$it"] = it+58 }; keys["F11"] = 87; keys["F12"] = 88
            keys.forEach { (k,v) -> put(k, "KEY:$v") }
        }
        val DEFAULTS = linkedMapOf("A" to "BUTTON:0", "B" to "KEY:1", "X" to "KEY:18", "Y" to "KEY:23",
            "LB" to "KEY:42", "RB" to "KEY:29", "LT" to "BUTTON:1", "RT" to "BUTTON:0",
            "L3" to "KEY:57", "R3" to "KEY:33", "DpadUp" to "KEY:2", "DpadRight" to "KEY:3",
            "DpadDown" to "KEY:4", "DpadLeft" to "KEY:5", "Start" to "KEY:1", "Select" to "KEY:15", "Mode" to "NONE",
            "StickUp" to "KEY:17", "StickLeft" to "KEY:30", "StickDown" to "KEY:31", "StickRight" to "KEY:32").apply {
                (1..16).forEach { put("Aux$it", "NONE") }
            }
        fun load(file: File): ControllerProfile {
            if (!file.isFile) return ControllerProfile()
            val p = Properties().apply { file.inputStream().use { load(it) } }
            return ControllerProfile(DEFAULTS.mapValues { (k,v) -> p.getProperty("bind.$k", v) },
                p.getProperty("deadZone", ".18").toFloat(), p.getProperty("moveThreshold", ".45").toFloat(),
                p.getProperty("mouseSpeed", "700").toFloat(), p.getProperty("invertY", "false").toBoolean()).also { it.validate() }
        }
    }
}

class ControllerMapping(val profile: ControllerProfile, private val emit: (String) -> Unit) {
    private val sources = mutableMapOf<String, String>()
    private var rightX = 0f; private var rightY = 0f
    private var activeDevice: Int? = null
    init { profile.validate() }
    fun button(device: Int, name: String, down: Boolean, origin: String = "button"): Boolean {
        val action = profile.bindings[name] ?: return false
        if (action == "NONE") return false
        val source = "$device:$origin:$name"
        val before = sources[source]
        if (down && before == null) {
            val shared = action in sources.values; sources[source] = action
            if (action.startsWith("WHEEL:")) { if (!shared) emit(action.replace(':', ' ')) }
            else if (!shared) emit(action.replace(':', ' ') + " 1")
        } else if (!down && before != null) {
            sources.remove(source)
            if (before !in sources.values && !before.startsWith("WHEEL:")) emit(before.replace(':', ' ') + " 0")
        }
        return true
    }
    fun axes(device: Int, x: Float, y: Float, rx: Float, ry: Float, hatX: Float, hatY: Float, lt: Float, rt: Float, hardwareFlat: Float = 0f) {
        if (activeDevice != null && activeDevice != device) releaseDevice(activeDevice!!)
        activeDevice = device
        val dead = max(profile.deadZone, if (hardwareFlat.isFinite()) hardwareFlat.coerceIn(0f, .8f) else 0f)
        fun clean(v: Float) = if (!v.isFinite() || abs(v) <= dead) 0f else sign(v) * ((abs(v).coerceAtMost(1f) - dead) / (1f - dead))
        val xx = clean(x); val yy = clean(y)
        fun direction(name: String, magnitude: Float) {
            val held = "$device:axis:$name" in sources
            button(device, name, magnitude > profile.moveThreshold * if (held) .8f else 1f, "axis")
        }
        direction("StickRight", xx); direction("StickLeft", -xx); direction("StickDown", yy); direction("StickUp", -yy)
        rightX = clean(rx); rightY = clean(ry)
        button(device, "DpadRight", hatX > .5f, "hat"); button(device, "DpadLeft", hatX < -.5f, "hat")
        button(device, "DpadDown", hatY > .5f, "hat"); button(device, "DpadUp", hatY < -.5f, "hat")
        button(device, "LT", lt > .5f, "trigger"); button(device, "RT", rt > .5f, "trigger")
    }
    fun tick(seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0 || (rightX == 0f && rightY == 0f)) return
        val scale = profile.mouseSpeed * seconds.coerceAtMost(.05f)
        // Desktop deltas: positive Y is up. Android stick positive Y is down.
        emit("MOVE ${rightX * scale} ${rightY * scale * if (profile.invertY) 1 else -1}")
    }
    fun releaseDevice(device: Int) {
        val values = sources.filterKeys { it.startsWith("$device:") }.values.toSet()
        sources.keys.removeAll { it.startsWith("$device:") }
        values.filter { it !in sources.values && !it.startsWith("WHEEL:") }.forEach { emit(it.replace(':',' ') + " 0") }
        if (activeDevice == device) { activeDevice = null; rightX = 0f; rightY = 0f }
    }
    fun reset() { sources.clear(); activeDevice = null; rightX = 0f; rightY = 0f; emit("RESET") }
}
