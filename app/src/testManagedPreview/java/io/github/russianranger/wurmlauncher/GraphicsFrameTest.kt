package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.nio.file.Files

class GraphicsFrameTest {
    private fun fixture(width: Int = 16, height: Int = 16, sequence: Int = 1, pixels: Int = 256, tail: Boolean = false) =
        Files.createTempFile("graphics-frame-test", ".bin").toFile().apply {
            DataOutputStream(outputStream()).use { out ->
                listOf(0x57554746,1,width,height,sequence).forEach(out::writeInt)
                repeat(pixels) { out.writeInt(0xff123456.toInt()) }
                if (tail) out.writeByte(1)
            }
        }
    @Test fun readsExactArgbPixelsAndDimensions() {
        val file=fixture()
        try {
            val frame=GraphicsFrame.read(file)
            assertEquals(16,frame.width); assertEquals(16,frame.height); assertEquals(1,frame.sequence)
            assertEquals(256,frame.argb.size); assertEquals(0xff123456.toInt(),frame.argb[255])
        } finally { file.delete() }
    }
    @Test fun rejectsUnboundedInvalidAndTruncatedFrames() {
        listOf(fixture(width=Int.MAX_VALUE), fixture(height=0), fixture(sequence=0), fixture(sequence=-1), fixture(pixels=255), fixture(tail=true)).forEach { file ->
            try { assertTrue("Invalid frame accepted",runCatching { GraphicsFrame.read(file) }.isFailure) }
            finally { file.delete() }
        }
    }
    @Test fun acceptsContinuingWindowFrames() {
        val file=fixture(sequence=450)
        try { assertEquals(450, GraphicsFrame.read(file).sequence) } finally { file.delete() }
    }
    @Test fun rejectsUnknownFormatAndHeader() {
        val file=fixture()
        try { file.writeBytes(byteArrayOf(1,2,3,4)); assertTrue(runCatching { GraphicsFrame.read(file) }.isFailure) }
        finally { file.delete() }
    }
}
