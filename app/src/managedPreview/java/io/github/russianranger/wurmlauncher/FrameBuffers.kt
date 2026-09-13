package io.github.russianranger.wurmlauncher

import java.io.Closeable
import java.io.File

/** At most two payloads. A reader owns a lease until the UI has copied or discarded it.
 * Neither ImageView nor Bitmap retains the leased arrays. Do not retain a frame after close.
 * The viewer currently has one read/UI delivery in flight, so steady-size play needs one slot.
 */
class FrameBuffers : Closeable {
    internal class Storage {
        var busy = false
        private var rgba = ByteArray(0)
        private var argb = IntArray(0)
        @Volatile var allocations = 0L
            private set
        @Synchronized fun bytes(size: Int): ByteArray {
            require(size in 1..1280 * 1024 * 4)
            if (rgba.size < size) { rgba = ByteArray(size); allocations++ }
            return rgba
        }
        @Synchronized fun ints(size: Int): IntArray {
            require(size in 1..1280 * 1024)
            if (argb.size < size) { argb = IntArray(size); allocations++ }
            return argb
        }
        @Synchronized fun retainedBytes() = rgba.size.toLong() + argb.size.toLong() * 4
        @Synchronized fun clear() { rgba = ByteArray(0); argb = IntArray(0) }
    }
    private val slots = List(2) { Storage() }
    private var closed = false
    data class Stats(val allocations: Long, val retainedBytes: Long, val leased: Int)
    @Synchronized fun stats() = Stats(slots.sumOf { it.allocations }, slots.sumOf { it.retainedBytes() }, slots.count { it.busy })
    @Synchronized private fun acquire(): Storage {
        check(!closed) { "Frame reader is closed" }
        return requireNotNull(slots.firstOrNull { !it.busy }) { "All frame buffers are leased" }.also { it.busy = true }
    }
    @Synchronized private fun release(slot: Storage) {
        check(slot.busy)
        slot.busy = false
        if (closed) slot.clear()
    }
    fun readNewer(file: File, afterSequence: Int): Lease? {
        val slot = acquire()
        var handedOff = false
        try {
            val frame = GraphicsFrame.readNewer(file, afterSequence, slot) ?: return null
            return Lease(frame, slot) { release(slot) }.also { handedOff = true }
        } finally { if (!handedOff) release(slot) }
    }
    class Lease internal constructor(private val data: GraphicsFrame, private val slot: Storage,
                                     private val release: () -> Unit) : Closeable {
        private var closed = false
        val frame: GraphicsFrame get() { check(!closed) { "Frame lease is closed" }; return data }
        fun decodedArgb(): IntArray = frame.let { it.decodedArgb(if (it.rawRgba != null) slot.ints(it.width * it.height) else null) }
        @Synchronized override fun close() { if (!closed) { closed = true; release() } }
    }
    @Synchronized override fun close() {
        closed = true
        slots.filter { !it.busy }.forEach { it.clear() }
        // In-flight leases remain valid; their owners release them even after activity destruction.
    }
}
