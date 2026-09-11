package io.github.russianranger.wurmlauncher

import android.os.Debug
import java.io.Closeable
import java.time.Instant

/** Observe the Android viewer separately from the two HotSpot children. */
class AppMemoryMeasurements(private val log: (String) -> Unit) : Closeable {
    @Volatile private var closed = false
    private val sampler = Thread({
        var warned = false
        while (!closed) {
            try {
                val info = Debug.MemoryInfo()
                Debug.getMemoryInfo(info)
                val runtime = Runtime.getRuntime()
                if (!closed) log("[runtime-memory] ${Instant.now()} role=android-app pid=${android.os.Process.myPid()} " +
                    "artUsedBytes=${runtime.totalMemory() - runtime.freeMemory()} artCommittedBytes=${runtime.totalMemory()} " +
                    "artMaxBytes=${runtime.maxMemory()} nativeAllocatedBytes=${Debug.getNativeHeapAllocatedSize()} " +
                    "PssKiB=${info.totalPss} privateDirtyKiB=${info.totalPrivateDirty}")
            } catch (failure: RuntimeException) {
                if (!warned && !closed) log("[runtime-memory] ${Instant.now()} role=android-app SAMPLE_UNAVAILABLE type=${failure.javaClass.simpleName}")
                warned = true
            }
            try { Thread.sleep(30_000) }
            catch (_: InterruptedException) { break }
        }
    }, "wurm-memory-android").apply { isDaemon = true; start() }

    override fun close() { closed = true; sampler.interrupt() }
}
