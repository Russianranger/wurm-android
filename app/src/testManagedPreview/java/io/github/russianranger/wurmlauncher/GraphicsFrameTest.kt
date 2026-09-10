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
    @Test fun reads720pRawFrameWithPointerAtLastPixel() {
        val file=fixture()
        try {
            DataOutputStream(file.outputStream()).use { out ->
                listOf(0x57554746,3,1280,720,7,1279,719,1,8).forEach(out::writeInt)
                val pixels=ByteArray(1280*720*4); pixels[0]=-1; pixels[pixels.lastIndex]=127
                out.write(pixels)
            }
            val data=GraphicsFrame.read(file)
            assertEquals(1280,data.width); assertEquals(720,data.height)
            assertEquals(GraphicsFrame.Pointer(1279,719,true,8),data.pointer)
            val raw=requireNotNull(data.rawRgba)
            assertEquals(1280*720*4,raw.size)
            assertEquals(255,raw[0].toInt() and 255); assertEquals(127,raw.last().toInt())
        } finally { file.delete() }
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
        listOf(fixture(width=Int.MAX_VALUE), fixture(width=1281), fixture(height=1025), fixture(height=0), fixture(sequence=0), fixture(sequence=-1), fixture(pixels=255), fixture(tail=true)).forEach { file ->
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
    @Test fun readsPointerAlongsidePixelsAndRejectsInvalidMetadata() {
        for (metadata in listOf(listOf(8,7,1,42), listOf(16,7,1,42), listOf(8,-1,1,42), listOf(8,7,2,42), listOf(8,7,1,-1))) {
            val file = fixture()
            try {
                DataOutputStream(file.outputStream()).use { out ->
                    (listOf(0x57554746,2,16,16,1) + metadata).forEach(out::writeInt)
                    repeat(256) { out.writeInt(0xff123456.toInt()) }
                }
                if (metadata == listOf(8,7,1,42)) {
                    val frame = GraphicsFrame.read(file)
                    assertEquals(GraphicsFrame.Pointer(8,7,true,42), frame.pointer)
                    assertEquals(0xff123456.toInt(), frame.argb[0])
                } else assertTrue(runCatching { GraphicsFrame.read(file) }.isFailure)
            } finally { file.delete() }
        }
    }

    @Test fun rawRgbaRetainsBottomOriginAndFallbackFlipsWithoutSwappingChannels() {
        val file=fixture()
        try {
            DataOutputStream(file.outputStream()).use { out ->
                listOf(0x57554746,3,16,16,8,3,4,1,99).forEach(out::writeInt)
                repeat(128) { out.write(byteArrayOf(-1,0,0,0)) }
                repeat(128) { out.write(byteArrayOf(0,0,-1,0)) }
            }
            val frame=GraphicsFrame.read(file)
            assertEquals(0,frame.argb.size)
            assertEquals(GraphicsFrame.Pointer(3,4,true,99),frame.pointer)
            assertEquals(255,frame.rawRgba!![0].toInt() and 255)
            val decoded=frame.decodedArgb()
            assertTrue(decoded.take(128).all { it == 0xff0000ff.toInt() })
            assertTrue(decoded.drop(128).all { it == 0xffff0000.toInt() })
            file.writeBytes(file.readBytes().dropLast(1).toByteArray())
            assertTrue(runCatching { GraphicsFrame.read(file) }.isFailure)
        } finally { file.delete() }
    }

    @Test fun consumesEverySequenceWhenAtomicFramesHaveIdenticalMtimeAndSize() {
        val target=fixture()
        val stamp=1_800_000_000_000L
        var sequence=0
        var oldIdentity=""
        var timestampSelections=0
        try {
            repeat(30) { i ->
                val replacement=fixture(sequence=i+1)
                try {
                    assertTrue(replacement.setLastModified(stamp))
                    Files.move(replacement.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    val identity="${target.lastModified()}:${target.length()}"
                    if (identity != oldIdentity) { timestampSelections++; oldIdentity=identity }
                    val frame=requireNotNull(GraphicsFrame.readNewer(target,sequence))
                    assertEquals(i+1,frame.sequence)
                    sequence=frame.sequence
                    assertNull(GraphicsFrame.readNewer(target,sequence))
                } finally { replacement.delete() }
            }
            assertEquals("Previous viewer gate drops 29 frames",1,timestampSelections)
            assertEquals(30,sequence)
        } finally { target.delete() }
    }

    @Test fun skipsOldFramesAndRecoversAfterCorruptionWithoutTimestampChange() {
        val target=fixture(sequence=2)
        val stamp=1_800_000_000_000L
        try {
            assertNull(GraphicsFrame.readNewer(target,3))
            target.writeBytes(byteArrayOf(1,2,3,4)); target.setLastModified(stamp)
            assertTrue(runCatching { GraphicsFrame.readNewer(target,1) }.isFailure)
            val replacement=fixture(sequence=2)
            try {
                replacement.setLastModified(stamp)
                Files.move(replacement.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                assertEquals(2,GraphicsFrame.readNewer(target,1)!!.sequence)
                // A new viewer/session starts at sequence zero, regardless of the old session's value.
                assertEquals(2,GraphicsFrame.readNewer(target,0)!!.sequence)
            } finally { replacement.delete() }
        } finally { target.delete() }
    }

}
