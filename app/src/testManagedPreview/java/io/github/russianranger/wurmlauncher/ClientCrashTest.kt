package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class ClientCrashTest {
    private fun integer(number: Long): ByteArray {
        var remaining = number
        val out = arrayListOf<Byte>()
        do {
            val part = (remaining and 127).toInt(); remaining = remaining ushr 7
            out += (part or if (remaining != 0L) 128 else 0).toByte()
        } while (remaining != 0L)
        return out.toByteArray()
    }
    private fun num(id: Int, number: Long) = integer((id * 8).toLong()) + integer(number)
    private fun bytes(id: Int, value: ByteArray) = integer((id * 8 + 2).toLong()) + integer(value.size.toLong()) + value
    private fun str(id: Int, text: String) = bytes(id, text.toByteArray())
    private fun identity() = num(5, 123) + num(6, 124) + num(7, 10001)
    private fun reject(data: ByteArray) {
        try { ClientTombstone.summarize(data, 123, 10001); fail("Malformed/mismatched tombstone accepted") }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }

    @Test fun tracksOnlyCurrentIdentityAndUnfinishedCalls() {
        val evidence = ClientCrashEvidence(10001, 500)
        evidence.observe("[native] uid=10002 euid=10002 pid=99; starting")
        assertEquals(0, evidence.pid)
        evidence.observe("[native] uid=10001 euid=0 pid=99; starting")
        assertEquals(0, evidence.pid)
        evidence.observe("[native] uid=10001 euid=10001 pid=123; starting")
        assertEquals(123, evidence.pid)
        evidence.observe("[graphics-trace] BEGIN seq=1 op=compile object=9 detail=0 thread=3")
        evidence.observe("[graphics-trace] BEGIN seq=2 op=link object=10 detail=0 thread=4")
        evidence.observe("[graphics-trace] END seq=2")
        assertTrue(evidence.summary(134).contains("unfinished graphics call: [graphics-trace] BEGIN seq=1"))
        assertTrue(evidence.summary(134).contains("possible SIGABRT"))
        evidence.observe("[graphics-trace] THREW seq=1 type=fixture.Error")
        assertFalse(evidence.summary(42).contains("unfinished"))
        assertFalse(evidence.summary(42).contains("SIGABRT"))
        assertEquals(0, ClientCrashEvidence(10001, 600).pid)
        assertTrue(ClientCrashEvidence(10001, 600).summary(134).contains("No graphics trace received"))
    }

    @Test fun boundsUntrustedTraceLinesAndPendingCalls() {
        val evidence = ClientCrashEvidence(10001, 500)
        repeat(50) { evidence.observe("[graphics-trace] BEGIN seq=$it op=" + "x".repeat(4000)) }
        assertTrue(evidence.summary(134).length < 600)
        for (i in 34..49) evidence.observe("[graphics-trace] END seq=$i")
        assertFalse(evidence.summary(134).contains("unfinished"))
    }

    @Test fun extractsOnlyCrashingThreadAndWhitelistedEvidence() {
        val signal = num(1, 6) + str(2, "SIGABRT") + num(3, -6) + str(4, "SI_TKILL")
        val frame = num(1, 4096) + str(4, "abort") + num(5, 20) + str(6, "libc.so") + str(8, "build-id")
        val thread = num(1, 124) + str(2, "Wurm\nthread") + bytes(4, frame) + str(5, "private memory")
        val other = num(1, 200) + str(2, "other thread") + bytes(4, frame)
        val data = identity() + bytes(10, signal) + str(14, "fixture abort") + bytes(15, str(1, "fixture cause")) +
            bytes(16, num(1, 200) + bytes(2, other)) + bytes(16, num(1, 124) + bytes(2, thread)) +
            str(17, "private mappings") + str(18, "private logs") + str(19, "private descriptors")
        val text = ClientTombstone.summarize(data, 123, 10001).joinToString("\n")
        assertTrue(text.contains("number=6 name=SIGABRT code=-6 codeName=SI_TKILL"))
        assertTrue(text.contains("TOMBSTONE_ABORT fixture abort"))
        assertTrue(text.contains("TOMBSTONE_CAUSE fixture cause"))
        assertTrue(text.contains("tid=124 name=Wurm thread"))
        assertTrue(text.contains("#0 pc=0x1000 libc.so abort+20 buildId=build-id"))
        assertFalse(text.contains("private")); assertFalse(text.contains("other thread"))
    }

    @Test fun rejectsWrongIdentityMalformedAndOversizedData() {
        reject(num(5, 124) + num(6, 124) + num(7, 10001))
        reject(num(5, 123) + num(6, 124) + num(7, 10002))
        reject(byteArrayOf(0)); reject(byteArrayOf(1)); reject(byteArrayOf(8, 0x80.toByte()))
        reject(byteArrayOf(8) + ByteArray(10) { 0xff.toByte() })
        reject(byteArrayOf(10, 127, 1)); reject(byteArrayOf(9, 1)); reject(byteArrayOf(13, 1))
        reject(ByteArray(ClientTombstone.LIMIT + 1))
        reject(identity() + bytes(16, num(1, 124) + bytes(2, num(1, 200))))
    }

    @Test fun boundsFramesAndTextAndSkipsUnknownFields() {
        var thread = num(1, 124) + str(2, "x".repeat(2000))
        repeat(100) { thread += bytes(4, num(1, it.toLong()) + str(6, "libfixture.so")) }
        val data = identity() + bytes(16, num(1, 124) + bytes(2, thread)) + bytes(100, ByteArray(80000))
        val lines = ClientTombstone.summarize(data, 123, 10001)
        assertEquals(32, lines.count { it.startsWith("TOMBSTONE_FRAME") })
        assertTrue(lines.all { it.length < 700 })
    }
}
