package io.github.russianranger.wurmlauncher

import java.io.DataInputStream
import java.io.File

/** Reads the bounded atomic frame format produced by the OpenJDK child. */
data class GraphicsFrame(val width: Int, val height: Int, val sequence: Int, val argb: IntArray, val pointer: Pointer? = null) {
    data class Pointer(val x: Int, val y: Int, val visible: Boolean, val applied: Int)
    companion object {
        fun read(file: File): GraphicsFrame = DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == 0x57554746) { "Unknown graphics frame magic" }
            val version = input.readInt()
            require(version in 1..2) { "Unknown graphics frame format" }
            val width = input.readInt(); val height = input.readInt(); val sequence = input.readInt()
            require(width in 16..1024 && height in 16..1024 && sequence > 0) { "Invalid graphics frame bounds" }
            val pointer = if (version == 2) {
                val x = input.readInt(); val y = input.readInt(); val visible = input.readInt(); val applied = input.readInt()
                require(x in 0 until width && y in 0 until height && visible in 0..1 && applied >= 0) { "Invalid pointer metadata" }
                Pointer(x, y, visible == 1, applied)
            } else null
            val pixels = IntArray(width * height) { input.readInt() }
            require(input.read() == -1) { "Trailing graphics frame bytes" }
            GraphicsFrame(width, height, sequence, pixels, pointer)
        }
    }
}
