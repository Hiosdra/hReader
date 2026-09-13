package com.hiosdra.hreader.adapter.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NetworkRetriesTest {
    @Test
    fun `retry after seconds is used as the minimum delay`() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()

        runCatching {
            withNetworkRetries(
                maxAttempts = 2,
                initialDelayMillis = 1,
                delayMillis = { delays += it }
            ) {
                attempts++
                throw HttpStatusException(429, "3")
            }
        }

        assertEquals(2, attempts)
        assertEquals(1, delays.size)
        assertTrue(delays.single() in 3_000L..3_249L)
    }

    @Test
    fun `retry after http date is parsed relative to the current time`() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()

        assertEquals(
            60_000L,
            retryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT", now)
        )
    }

    @Test
    fun `invalid and negative retry after values are ignored`() {
        assertEquals(null, retryAfterMillis("not-a-delay", 0L))
        assertEquals(null, retryAfterMillis("-1", 0L))
        assertFalse(NonRetryableNetworkException("blocked").isNetworkRetryable())
    }
}
