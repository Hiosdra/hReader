package com.hiosdra.hreader.core.application.observability

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class NetworkMetricsCollector {
    private val windows = CopyOnWriteArraySet<MetricsWindow>()

    fun startWindow(): MetricsWindow = MetricsWindow().also(windows::add)

    fun finishWindow(window: MetricsWindow): NetworkMetricsSnapshot {
        windows.remove(window)
        return window.snapshot()
    }

    fun requestStarted(): RequestToken {
        val token = RequestToken(windows.toList(), System.nanoTime())
        token.windows.forEach { it.requestStarted(token.startedAtNanos) }
        return token
    }

    internal fun responseHeadersReceived(token: RequestToken, code: Int) {
        token.responseCode = code
    }

    internal fun responseBodyReceived(token: RequestToken, byteCount: Long) {
        token.byteCount = byteCount.coerceAtLeast(0L)
    }

    internal fun requestFailed(token: RequestToken) {
        finish(token, failed = true)
    }

    internal fun requestFinished(token: RequestToken) {
        finish(token, failed = false)
    }

    private fun finish(token: RequestToken, failed: Boolean) {
        if (!token.finished.compareAndSet(false, true)) return
        val finishedAtNanos = System.nanoTime()
        token.windows.forEach { window ->
            window.requestFinished(
                startedAtNanos = token.startedAtNanos,
                finishedAtNanos = finishedAtNanos,
                responseCode = token.responseCode,
                byteCount = token.byteCount,
                failed = failed
            )
        }
    }

    class MetricsWindow internal constructor() {
        private val activeRequests = AtomicInteger()
        private val maxConcurrentRequests = AtomicInteger()
        private val requestCount = AtomicInteger()
        private val errorCount = AtomicInteger()
        private val totalBytes = AtomicLong()
        private val totalResponseDurationNanos = AtomicLong()
        private val firstRequestStartedNanos = AtomicLong()
        private val lastRequestFinishedNanos = AtomicLong()

        internal fun requestStarted(startedAtNanos: Long) {
            firstRequestStartedNanos.compareAndSet(0L, startedAtNanos)
            val active = activeRequests.incrementAndGet()
            while (true) {
                val previous = maxConcurrentRequests.get()
                if (active <= previous || maxConcurrentRequests.compareAndSet(previous, active)) break
            }
        }

        internal fun requestFinished(
            startedAtNanos: Long,
            finishedAtNanos: Long,
            responseCode: Int?,
            byteCount: Long,
            failed: Boolean
        ) {
            activeRequests.decrementAndGet()
            requestCount.incrementAndGet()
            if (failed || responseCode?.let { it >= 400 } == true) errorCount.incrementAndGet()
            totalBytes.addAndGet(byteCount)
            totalResponseDurationNanos.addAndGet((finishedAtNanos - startedAtNanos).coerceAtLeast(0L))
            lastRequestFinishedNanos.accumulateAndGet(finishedAtNanos) { current, update ->
                maxOf(current, update)
            }
        }

        internal fun snapshot(): NetworkMetricsSnapshot {
            val first = firstRequestStartedNanos.get()
            val last = lastRequestFinishedNanos.get()
            return NetworkMetricsSnapshot(
                requestCount = requestCount.get(),
                errorCount = errorCount.get(),
                totalBytes = totalBytes.get(),
                totalResponseDurationMs = totalResponseDurationNanos.get() / 1_000_000L,
                wallDurationMs = if (first > 0L && last >= first) {
                    (last - first) / 1_000_000L
                } else {
                    0L
                },
                maxConcurrentRequests = maxConcurrentRequests.get()
            )
        }
    }

    class RequestToken internal constructor(
        internal val windows: List<MetricsWindow>,
        internal val startedAtNanos: Long
    ) {
        internal var responseCode: Int? = null
        internal var byteCount: Long = 0L
        internal val finished = AtomicBoolean()
    }
}

data class NetworkMetricsSnapshot(
    val requestCount: Int,
    val errorCount: Int,
    val totalBytes: Long,
    val totalResponseDurationMs: Long,
    val wallDurationMs: Long,
    val maxConcurrentRequests: Int
) {
    val averageResponseMs: Long
        get() = if (requestCount == 0) 0L else totalResponseDurationMs / requestCount

    val throughputBytesPerSecond: Long
        get() = if (wallDurationMs <= 0L) 0L else {
            val scaledBytes = if (totalBytes > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else totalBytes * 1_000
            scaledBytes / wallDurationMs
        }
}
