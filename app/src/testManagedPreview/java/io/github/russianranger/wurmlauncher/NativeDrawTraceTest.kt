package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class NativeDrawTraceTest {
    @Test fun readsUnsignedPointersAndRejectsCorruptOrIncompleteRecord() {
        val file = Files.createTempFile("native-draw", ".bin").toFile()
        try {
            val bytes = ByteArray(1024)
            val words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            listOf(0x57444754,1,1,42,2,4,0,306,0x1403,0x12340000,0x7a,29,0,1).forEachIndexed { i,v -> words.put(i,v) }
            listOf(1,3,0x1406,12,0,0x87654321.toInt(),0x12345678,0).forEachIndexed { i,v -> words.put(16+i,v) }
            file.writeBytes(bytes)
            val report = NativeDrawTrace.read(file)
            assertTrue(report.contains("stage=entered-driver"))
            assertTrue(report.contains("count=306"))
            assertTrue(report.contains("indices=0x7a12340000"))
            assertTrue(report.contains("pointer=0x1234567887654321"))
            words.put(2,2); file.writeBytes(bytes)
            assertTrue(NativeDrawTrace.read(file).contains("stage=returned"))
            words.put(13,17); file.writeBytes(bytes)
            assertTrue(runCatching { NativeDrawTrace.read(file) }.isFailure)
            words.put(13,1); words.put(0,0); file.writeBytes(bytes)
            assertTrue(runCatching { NativeDrawTrace.read(file) }.isFailure)
            file.writeBytes(ByteArray(1023))
            assertTrue(runCatching { NativeDrawTrace.read(file) }.isFailure)
        } finally { file.delete() }
    }
}
