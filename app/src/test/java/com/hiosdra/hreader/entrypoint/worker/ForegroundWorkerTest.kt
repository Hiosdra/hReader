package com.hiosdra.hreader.entrypoint.worker

import androidx.work.ForegroundInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWorkerTest {
    @Test
    fun continuesWhenForegroundPromotionIsRejected() = runBlocking {
        assertFalse(
            setForegroundIfAllowed(
                getForegroundInfo = { foregroundInfo() },
                setForeground = { throw IllegalStateException("startForegroundService() not allowed") }
            )
        )
    }

    @Test
    fun reportsSuccessfulForegroundPromotion() = runBlocking {
        assertTrue(
            setForegroundIfAllowed(
                getForegroundInfo = { foregroundInfo() },
                setForeground = {}
            )
        )
    }

    @Test
    fun rethrowsFailureBuildingForegroundInfo() = runBlocking {
        try {
            setForegroundIfAllowed(
                getForegroundInfo = { throw IllegalStateException("invalid notification") },
                setForeground = {}
            )
        } catch (error: IllegalStateException) {
            assertTrue(error.message == "invalid notification")
            return@runBlocking
        }
        throw AssertionError("Expected the unrelated failure to be rethrown")
    }

    private fun foregroundInfo() = ForegroundInfo(1, android.app.Notification())
}
