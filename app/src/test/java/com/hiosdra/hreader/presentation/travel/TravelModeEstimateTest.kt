package com.hiosdra.hreader.presentation.travel

import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelModeEstimateTest {
    @Test
    fun `estimate counts only missing content images and requested pages`() {
        val readiness = OfflineReadiness(
            offlineTargetCount = 10,
            storedContentCount = 6,
            storedFullContentCount = 5,
            expectedImageCount = 8,
            storedExpectedImageCount = 3,
            storedFullPageCount = 2
        )

        assertEquals(
            TravelModeEstimate(
                networkBytes = 4 * 64 * 1024L + 5 * 160 * 1024L,
                storageBytes = 4 * 64 * 1024L + 5 * 160 * 1024L
            ),
            estimateTravelMode(readiness, includeImages = true, includeFullPages = false)
        )
        assertEquals(
            TravelModeEstimate(
                networkBytes = 5 * 64 * 1024L + 5 * 160 * 1024L + 8 * 512 * 1024L,
                storageBytes = 5 * 64 * 1024L + 5 * 160 * 1024L + 8 * 512 * 1024L
            ),
            estimateTravelMode(readiness, includeImages = true, includeFullPages = true)
        )
    }

    @Test
    fun `size formatter rounds rough estimates up to readable units`() {
        assertEquals("0 B", formatTravelModeSize(0))
        assertEquals("2 KB", formatTravelModeSize(1025))
        assertEquals("2 MB", formatTravelModeSize(1_048_577))
    }
}
