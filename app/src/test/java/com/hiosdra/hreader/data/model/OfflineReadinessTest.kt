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
}
