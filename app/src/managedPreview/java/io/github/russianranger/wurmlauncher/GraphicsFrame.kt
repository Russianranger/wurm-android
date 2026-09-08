package io.github.russianranger.wurmlauncher

import java.io.DataInputStream
import java.io.File

/** Reads the bounded atomic frame format produced by the OpenJDK child. */
data class GraphicsFrame(val width: Int, val height: Int, val sequence: Int, val argb: IntArray) {
    companion object {
        fun read(file: File): GraphicsFrame = DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == 0x57554746 && input.readInt() == 1) { "Unknown graphics frame format" }
            val width = input.readInt(); val height = input.readInt(); val sequence = input.readInt()
            require(width in 16..1024 && height in 16..1024 && sequence in 1..3) { "Invalid graphics frame bounds" }
            val pixels = IntArray(width * height) { input.readInt() }
            require(input.read() == -1) { "Trailing graphics frame bytes" }
            GraphicsFrame(width, height, sequence, pixels)
        }
    }
}
