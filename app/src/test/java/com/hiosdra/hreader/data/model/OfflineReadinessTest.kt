package com.hiosdra.hreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReadinessTest {

    @Test
    fun `articles without stored content are what is still missing`() {
        val readiness = OfflineReadiness(articleCount = 120, storedContentCount = 95)

        assertEquals(25, readiness.missingContentCount)
        assertFalse(readiness.isComplete)
    }

    @Test
    fun `content stored for every article reads as ready`() {
        val readiness = OfflineReadiness(articleCount = 42, storedContentCount = 42)

        assertEquals(0, readiness.missingContentCount)
        assertTrue(readiness.isComplete)
    }

    @Test
    fun `an empty cache is not ready however complete it looks`() {
        assertFalse(OfflineReadiness().isComplete)
    }

    @Test
    fun `more content rows than articles never reports a negative shortfall`() {
        val readiness = OfflineReadiness(articleCount = 3, storedContentCount = 5)

        assertEquals(0, readiness.missingContentCount)
    }

    @Test
    fun `offline scope can be smaller than the complete article cache`() {
        val readiness = OfflineReadiness(
            articleCount = 100,
            offlineTargetCount = 12,
            storedContentCount = 12,
            storedFullContentCount = 8
        )

        assertTrue(readiness.isComplete)
        assertEquals(4, readiness.missingFullContentCount)
    }

    @Test
    fun `missing manifest images remain visible in readiness`() {
        val readiness = OfflineReadiness(
            offlineTargetCount = 2,
            storedContentCount = 2,
            expectedImageCount = 5,
            storedExpectedImageCount = 3
        )

        assertEquals(2, readiness.missingImageCount)
    }
}
