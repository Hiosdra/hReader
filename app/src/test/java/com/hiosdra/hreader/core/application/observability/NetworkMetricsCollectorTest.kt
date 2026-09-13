package com.hiosdra.hreader.core.application.observability

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMetricsCollectorTest {
    @Test
    fun `collects bytes response time errors and peak concurrency`() {
        val collector = NetworkMetricsCollector()
        val window = collector.startWindow()
        val first = collector.requestStarted()
        val second = collector.requestStarted()

        collector.responseHeadersReceived(first, 200)
        collector.responseBodyReceived(first, 100)
        collector.requestFinished(first)
        collector.responseHeadersReceived(second, 503)
        collector.responseBodyReceived(second, 20)
        collector.requestFinished(second)

        val snapshot = collector.finishWindow(window)

        assertEquals(2, snapshot.requestCount)
        assertEquals(1, snapshot.errorCount)
        assertEquals(120L, snapshot.totalBytes)
        assertEquals(2, snapshot.maxConcurrentRequests)
    }
}
