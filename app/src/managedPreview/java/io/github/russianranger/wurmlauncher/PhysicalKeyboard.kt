package io.github.russianranger.wurmlauncher

import android.view.InputDevice
import android.view.KeyEvent

/** Android hardware key codes -> the existing LWJGL 2 queue, with source-owned releases. */
class PhysicalKeyboard(private val send: (String) -> Boolean) {
    private val held=mutableMapOf<Pair<Int,Int>,Int>()
    fun reset() { held.values.toSet().forEach { send("KEY $it 0") }; held.clear() }
    fun releaseDevice(id: Int) {
        val keys=held.filterKeys { it.first==id }; keys.keys.forEach(held::remove)
        keys.values.filter { it !in held.values }.toSet().forEach { send("KEY $it 0") }
    }
    fun key(event: KeyEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_KEYBOARD) || event.keyCode==KeyEvent.KEYCODE_BACK) return false
        if(event.action!=KeyEvent.ACTION_DOWN && event.action!=KeyEvent.ACTION_UP) return false
        val code=codes[event.keyCode] ?: return false
        val id=event.deviceId to event.keyCode
        if(event.action==KeyEvent.ACTION_UP || event.isCanceled) {
            held.remove(id)?.let { if(it !in held.values) send("KEY $it 0") }
        } else {
            // Keyboard repeat is meaningful for text/navigation, but never duplicates a down owner.
            val repeat=held.containsKey(id)
            held[id]=code
            val unicode=if(event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) 0 else event.unicodeChar
            val char=unicode.takeIf { it in 1..0xffff && it !in 0xd800..0xdfff } ?: 0
            send("KEYCHAR $code $char ${if(repeat) 1 else 0}")
        }
        return true
    }
    companion object {
        // Android constants are stable key codes; explicit mapping avoids assuming contiguous LWJGL letters.
        val codes=mapOf(111 to 1, 7 to 11, 8 to 2, 9 to 3, 10 to 4, 11 to 5, 12 to 6, 13 to 7, 14 to 8, 15 to 9, 16 to 10,
            29 to 30,30 to 48,31 to 46,32 to 32,33 to 18,34 to 33,35 to 34,36 to 35,37 to 23,38 to 36,39 to 37,
            40 to 38,41 to 50,42 to 49,43 to 24,44 to 25,45 to 16,46 to 19,47 to 31,48 to 20,49 to 22,50 to 47,
            51 to 17,52 to 45,53 to 21,54 to 44, 55 to 51,56 to 52,57 to 56,58 to 184,59 to 42,60 to 54,
            61 to 15,62 to 57,66 to 28,67 to 14,68 to 41,69 to 12,70 to 13,71 to 26,72 to 27,73 to 43,74 to 39,
            75 to 40,76 to 53,92 to 201,93 to 209,112 to 211,113 to 29,114 to 157,115 to 58,116 to 70,
            117 to 219,118 to 220,120 to 183,121 to 197,122 to 199,123 to 207,124 to 210,
            19 to 200,20 to 208,21 to 203,22 to 205,143 to 69,144 to 82,145 to 79,146 to 80,147 to 81,
            148 to 75,149 to 76,150 to 77,151 to 71,152 to 72,153 to 73,154 to 181,155 to 55,156 to 74,
            157 to 78,158 to 83,160 to 156,161 to 141) + (131..140).associateWith { it-72 } + mapOf(141 to 87,142 to 88)
    }
}
