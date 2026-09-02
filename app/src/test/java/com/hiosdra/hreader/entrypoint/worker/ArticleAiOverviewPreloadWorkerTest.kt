package com.hiosdra.hreader.entrypoint.worker

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleAiOverviewPreloadWorkerTest {
    @Test
    fun `battery must be strictly above eighty percent when not charging`() {
        assertFalse(isBatteryLevelAllowed(level = 80, scale = 100))
        assertTrue(isBatteryLevelAllowed(level = 81, scale = 100))
    }

    @Test
    fun `invalid battery scale is not eligible`() {
        assertFalse(isBatteryLevelAllowed(level = 100, scale = 0))
        assertFalse(isBatteryLevelAllowed(level = -1, scale = 100))
    }

    @Test
    fun `charging allows preloading even below the battery threshold`() {
        assertTrue(
            isBatteryEligible(
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                level = 20,
                scale = 100
            )
        )
    }

    @Test
    fun `being plugged in allows preloading at the threshold`() {
        assertTrue(
            isBatteryEligible(
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                level = 80,
                scale = 100,
                isPluggedIn = true
            )
        )
    }
}
