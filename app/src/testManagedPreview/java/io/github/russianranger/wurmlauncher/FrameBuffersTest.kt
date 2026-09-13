package io.github.russianranger.wurmlauncher

import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FrameBuffersTest {
    private fun write(file: File, sequence: Int, width: Int = 16, height: Int = 16, version: Int = 3, red: Int = 255) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            listOf(0x57554746,version,width,height,sequence).forEach(out::writeInt)
            if (version >= 2) listOf(width-1,height-1,1,sequence).forEach(out::writeInt)
            repeat(width*height) { i ->
                if (version == 3) out.write(if (i < width*height/2) byteArrayOf(red.toByte(),0,0,0) else byteArrayOf(0,0,-1,0))
                else out.writeInt(0xff123456.toInt())
            }
        }
    }
    private fun checkFrames(action: (FrameBuffers, File) -> Unit) {
        val file=Files.createTempFile("pooled-frame", ".bin").toFile()
        try { FrameBuffers().use { action(it,file) } } finally { file.delete() }
    }
    @Test fun sequentialReadsReuseBothRawAndFallbackStorageWithoutChangingColorsOrOrigin() = checkFrames { pool,file ->
        var raw: ByteArray? = null; var decoded: IntArray? = null
        repeat(60) { i ->
            write(file,i+1,1280,720,red=128+i)
            pool.readNewer(file,i)!!.use { lease ->
                val data=lease.frame; val pixels=lease.decodedArgb()
                if (i==0) { raw=data.rawRgba; decoded=pixels }
                assertSame(raw,data.rawRgba); assertSame(decoded,pixels)
                assertEquals(128+i,data.rawRgba!![0].toInt() and 255)
                assertEquals(0xff0000ff.toInt(),pixels[0])
                assertEquals((0xff000000L or ((128+i).toLong() shl 16)).toInt(),pixels[1280*720-1])
                assertEquals(GraphicsFrame.Pointer(1279,719,true,i+1),data.pointer)
            }
        }
        assertEquals(2L,pool.stats().allocations)
        assertEquals(1280L*720*8,pool.stats().retainedBytes)
        assertEquals(0,pool.stats().leased)
    }
    @Test fun rawDisplayNeedsOnlyOneLargeAllocationAndDuplicateHeadersAllocateNothing() = checkFrames { pool,file ->
        write(file,1,1280,720)
        assertNull(pool.readNewer(file,1))
        assertEquals(0L,pool.stats().allocations)
        repeat(30) { i ->
            write(file,i+1,1280,720)
            pool.readNewer(file,i)!!.use { assertEquals(1280*720*4,it.frame.rawRgba!!.size) }
        }
        assertEquals(1L,pool.stats().allocations)
        assertEquals(1280L*720*4,pool.stats().retainedBytes)
    }
    @Test fun uiLeaseCannotBeOverwrittenByAnotherReadAndPoolCannotGrowPastTwoSlots() = checkFrames { pool,file ->
        write(file,1,red=17)
        val first=pool.readNewer(file,0)!!
        write(file,2,red=29)
        val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
        val second=try { worker.submit<FrameBuffers.Lease> { pool.readNewer(file,1)!! }.get(5,java.util.concurrent.TimeUnit.SECONDS) }
            finally { worker.shutdown() }
        assertNotSame(first.frame.rawRgba,second.frame.rawRgba)
        assertEquals(17,first.frame.rawRgba!![0].toInt())
        assertEquals(29,second.frame.rawRgba!![0].toInt())
        assertTrue(runCatching { pool.readNewer(file,0) }.isFailure)
        assertEquals(2,pool.stats().leased)
        first.close(); first.close()
        assertTrue(runCatching { first.frame }.isFailure)
        pool.readNewer(file,0)!!.use { assertEquals(2,it.frame.sequence) }
        assertEquals(29,second.frame.rawRgba!![0].toInt())
        second.close()
        assertEquals(2L,pool.stats().allocations)
    }
    @Test fun corruptTruncatedAndTrailingFramesReleaseLeaseAndLaterValidFrameWorks() = checkFrames { pool,file ->
        write(file,1)
        val good=file.readBytes()
        for (bad in listOf(byteArrayOf(1,2),good.dropLast(1).toByteArray(),good+byteArrayOf(9))) {
            file.writeBytes(bad)
            assertTrue(runCatching { pool.readNewer(file,0) }.isFailure)
            assertEquals(0,pool.stats().leased)
        }
        file.writeBytes(good)
        pool.readNewer(file,0)!!.use { assertEquals(1,it.frame.sequence) }
    }
    @Test fun resolutionAndFormatChangesUseOnlyValidPayloadLength() = checkFrames { pool,file ->
        write(file,1,1280,720)
        pool.readNewer(file,0)!!.close()
        write(file,2,16,16,version=1)
        pool.readNewer(file,1)!!.use { assertEquals(0xff123456.toInt(),it.frame.argb[255]) }
        write(file,3,16,16,red=42)
        pool.readNewer(file,2)!!.use {
            assertEquals(0xff0000ff.toInt(),it.decodedArgb()[0])
            assertEquals(0xff2a0000.toInt(),it.decodedArgb()[255])
        }
        assertEquals(2L,pool.stats().allocations)
    }
    @Test fun activityDestructionAndDiscardedOrFailedUiCopyReleaseStorage() = checkFrames { pool,file ->
        write(file,1)
        val lease=pool.readNewer(file,0)!!
        pool.close()
        assertEquals(1,lease.frame.sequence)
        assertTrue(runCatching { pool.readNewer(file,0) }.isFailure)
        assertTrue(runCatching { lease.use { error("simulated stale or failed UI delivery") } }.isFailure)
        assertEquals(0,pool.stats().leased)
        assertEquals(0L,pool.stats().retainedBytes)
    }
}
