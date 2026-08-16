package com.hiosdra.hreader.entrypoint.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullPageSyncWorkerTest {
    @Test
    fun `keeps retrying after the attempt cap when a batch made progress`() {
        assertTrue(
            shouldRetryFullPageSync(
                remaining = 101,
                previousOutstanding = 200,
                runAttemptCount = 5
            )
        )
    }

    @Test
    fun `stops retrying after the attempt cap when no page was archived`() {
        assertFalse(
            shouldRetryFullPageSync(
                remaining = 200,
                previousOutstanding = 200,
                runAttemptCount = 5
            )
        )
    }

    @Test
    fun `retries an unchanged batch before the attempt cap`() {
        assertTrue(
            shouldRetryFullPageSync(
                remaining = 200,
                previousOutstanding = 200,
                runAttemptCount = 4
            )
        )
    }
}
