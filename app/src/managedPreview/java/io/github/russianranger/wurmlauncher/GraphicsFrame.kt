package io.github.russianranger.wurmlauncher

import java.io.DataInputStream
import java.io.File

/** Reads the bounded atomic frame format produced by the OpenJDK child. */
data class GraphicsFrame(val width: Int, val height: Int, val sequence: Int, val argb: IntArray, val pointer: Pointer? = null,
                         val rawRgba: ByteArray? = null) {
    data class Pointer(val x: Int, val y: Int, val visible: Boolean, val applied: Int)
    /** Opaque, top-left pixels for platforms that cannot directly copy RGBA bytes. */
    fun decodedArgb(): IntArray {
        val raw = rawRgba ?: return argb
        return IntArray(width * height) { i ->
            val k = ((height - 1 - i / width) * width + i % width) * 4
            (0xff shl 24) or ((raw[k].toInt() and 255) shl 16) or
                ((raw[k+1].toInt() and 255) shl 8) or (raw[k+2].toInt() and 255)
        }
    }
    companion object {
        fun read(file: File): GraphicsFrame = requireNotNull(readNewer(file, 0))
        /** Sequence is authoritative. Android 13 File.lastModified() drops sub-second precision. */
        fun readNewer(file: File, afterSequence: Int): GraphicsFrame? = DataInputStream(file.inputStream().buffered()).use { input ->
            require(afterSequence >= 0) { "Invalid prior frame sequence" }
            require(input.readInt() == 0x57554746) { "Unknown graphics frame magic" }
            val version = input.readInt()
            require(version in 1..3) { "Unknown graphics frame format" }
            val width = input.readInt(); val height = input.readInt(); val sequence = input.readInt()
            require(width in 16..1024 && height in 16..1024 && sequence > 0) { "Invalid graphics frame bounds" }
            // Read header and pixels from the same open file, even across atomic replacement.
            // Skip duplicate payload allocation/copy without consulting mtime or file size.
            if (sequence <= afterSequence) return@use null
            val pointer = if (version >= 2) {
                val x = input.readInt(); val y = input.readInt(); val visible = input.readInt(); val applied = input.readInt()
                require(x in 0 until width && y in 0 until height && visible in 0..1 && applied >= 0) { "Invalid pointer metadata" }
                Pointer(x, y, visible == 1, applied)
            } else null
            val bytes = ByteArray(width * height * 4)
            input.readFully(bytes)
            val pixels = if (version == 3) IntArray(0) else IntArray(width * height).also {
                java.nio.ByteBuffer.wrap(bytes).asIntBuffer().get(it)
            }
            require(input.read() == -1) { "Trailing graphics frame bytes" }
            GraphicsFrame(width, height, sequence, pixels, pointer, if (version == 3) bytes else null)
        }
    }
}
