package com.hiosdra.hreader.entrypoint.worker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWorkerTest {
    @Test
    fun continuesWhenForegroundPromotionIsRejected() = runBlocking {
        assertFalse(
            setForegroundIfAllowed {
                throw IllegalStateException("startForegroundService() not allowed")
            }
        )
    }

    @Test
    fun reportsSuccessfulForegroundPromotion() = runBlocking {
        assertTrue(setForegroundIfAllowed {})
    }

    @Test
    fun rethrowsFailuresThatAreNotForegroundStartRestrictions() = runBlocking {
        try {
            setForegroundIfAllowed { throw IllegalArgumentException("invalid notification") }
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message == "invalid notification")
            return@runBlocking
        }
        throw AssertionError("Expected the unrelated failure to be rethrown")
    }
}
