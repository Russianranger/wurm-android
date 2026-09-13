package io.github.russianranger.wurmlauncher

import java.util.Locale
import kotlin.math.ceil

/** Bounded viewer-copy measurements, not GPU frame times or physical display scanout.
 * Owned by the UI thread; reset across pauses/session changes, drain every five seconds.
 */
class FrameTimingStats {
    private class Samples {
        private val values = LongArray(512)
        private var count = 0
        var total = 0L
            private set
        var max = 0L
            private set
        fun add(value: Long) { values[(total % values.size).toInt()] = value; total++; count = minOf(count + 1, values.size); max = maxOf(max, value) }
        fun percentile(percent: Int): Double {
            if (count == 0) return 0.0
            val sorted = values.copyOf(count).apply { sort() }
            return sorted[(ceil(count * percent / 100.0).toInt() - 1).coerceAtLeast(0)] / 1e6
        }
        fun clear() { count = 0; total = 0; max = 0 }
    }
    private val intervals = Samples()
    private val reads = Samples()
    private val copies = Samples()
    private var previousAt = 0L
    private var previousSequence = 0
    private var skipped = 0L
    fun record(sequence: Int, atNanos: Long, readNanos: Long, copyNanos: Long) {
        require(sequence > previousSequence && atNanos > 0 && (previousAt == 0L || atNanos >= previousAt))
        require(readNanos >= 0 && copyNanos >= 0)
        if (previousAt != 0L) intervals.add(atNanos - previousAt)
        if (previousSequence != 0) skipped += sequence.toLong() - previousSequence - 1
        previousAt = atNanos; previousSequence = sequence
        reads.add(readNanos); copies.add(copyNanos)
    }
    fun drain(): String {
        val result = String.format(Locale.ROOT,
            "samples=%d intervals=%d intervalP50Ms=%.2f intervalP95Ms=%.2f intervalP99Ms=%.2f intervalMaxMs=%.2f readP95Ms=%.2f copyP95Ms=%.2f skippedPublished=%d percentileWindow=last512",
            reads.total, intervals.total, intervals.percentile(50), intervals.percentile(95), intervals.percentile(99),
            intervals.max / 1e6, reads.percentile(95), copies.percentile(95), skipped)
        intervals.clear(); reads.clear(); copies.clear(); skipped = 0
        return result
    }
    fun reset() { drain(); previousAt = 0; previousSequence = 0 }
}
