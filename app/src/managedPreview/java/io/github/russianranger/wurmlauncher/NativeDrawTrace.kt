package io.github.russianranger.wurmlauncher

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read only after child exit: the owned ARM64 draw thread writes a fixed 1 KiB breadcrumb. */
object NativeDrawTrace {
    fun read(file: File): String {
        if (!file.isFile) return "No native draw record."
        require(file.length() == 1024L) { "Invalid native draw record size" }
        val words = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        require(words[0] == 0x57444754 && words[1] == 1 && words[2] in 1..2 && words[4] in 1..2 &&
            words[13] in 0..16) { "Invalid or incomplete native draw record" }
        fun hex(n: Int) = "0x" + Integer.toUnsignedString(n, 16)
        fun address(lo: Int, hi: Int) = "0x" + java.lang.Long.toUnsignedString(
            (lo.toLong() and 0xffffffffL) or (hi.toLong() shl 32), 16)
        return buildString {
            appendLine("NATIVE_DRAW sequence=${Integer.toUnsignedString(words[3])} stage=${if (words[2] == 1) "entered-driver" else "returned"} kind=${if (words[4] == 1) "arrays" else "elements"} mode=${hex(words[5])} first=${words[6]} count=${words[7]} indexType=${hex(words[8])} indices=${address(words[9], words[10])} program=${words[11]} elementBuffer=${words[12]}")
            repeat(words[13]) { i ->
                val k = 16 + i * 8
                if (words[k] != 0) appendLine("ATTR index=$i size=${words[k+1]} type=${hex(words[k+2])} stride=${words[k+3]} buffer=${words[k+4]} pointer=${address(words[k+5],words[k+6])} normalized=${words[k+7]}")
            }
        }.trimEnd()
    }
}
