package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class FrameTimingStatsTest {
    @Test fun calculatesNearestRankPercentilesAndSequenceGaps() {
        val stats=FrameTimingStats(); var now=1L
        stats.record(1,now,2_000_000,1_000_000)
        listOf(10,20,30,40,100).forEachIndexed { i,ms -> now+=ms*1_000_000L; stats.record(i+3,now,2_000_000,1_000_000) }
        val result=stats.drain()
        listOf("samples=6", "intervals=5", "intervalP50Ms=30.00", "intervalP95Ms=100.00", "intervalP99Ms=100.00",
            "intervalMaxMs=100.00", "readP95Ms=2.00", "copyP95Ms=1.00", "skippedPublished=1").forEach { assertTrue(result,result.contains(it)) }
        stats.record(8,now+20_000_000,0,0)
        assertTrue(stats.drain().contains("intervalP50Ms=20.00"))
    }
    @Test fun pauseAndEpochResetExcludeBackgroundTimeAndAllowNewSequences() {
        val stats=FrameTimingStats()
        stats.record(100,1,0,0); stats.reset()
        stats.record(1,999_999_999,0,0)
        val result=stats.drain()
        assertTrue(result.contains("samples=1 intervals=0")); assertTrue(result.contains("skippedPublished=0"))
        assertTrue(stats.drain().contains("samples=0 intervals=0"))
    }
    @Test fun storageIsBoundedAndWindowMaxStillIncludesExpiredOutlier() {
        val stats=FrameTimingStats(); var now=1L
        stats.record(1,now,0,0)
        repeat(600) { i -> now+=(if (i==0) 999 else i+1)*1_000_000L; stats.record(i+2,now,0,0) }
        val result=stats.drain()
        assertTrue(result.contains("samples=601 intervals=600"))
        assertTrue(result.contains("intervalP50Ms=344.00"))
        assertTrue(result.contains("intervalMaxMs=999.00"))
        assertTrue(result.contains("percentileWindow=last512"))
    }
}
