package com.hiosdra.hreader.core.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun `a window that wraps midnight covers both sides of it`() {
        assertTrue(isWithinQuietHours(hour = 23, startHour = 23, endHour = 7))
        assertTrue(isWithinQuietHours(hour = 3, startHour = 23, endHour = 7))
        assertFalse(isWithinQuietHours(hour = 7, startHour = 23, endHour = 7))
        assertFalse(isWithinQuietHours(hour = 12, startHour = 23, endHour = 7))
    }

    @Test
    fun `a window inside one day covers only that stretch`() {
        assertTrue(isWithinQuietHours(hour = 10, startHour = 9, endHour = 17))
        assertFalse(isWithinQuietHours(hour = 17, startHour = 9, endHour = 17))
        assertFalse(isWithinQuietHours(hour = 8, startHour = 9, endHour = 17))
    }

    @Test
    fun `equal bounds mean no quiet hours rather than a silent day`() {
        (0..23).forEach { hour ->
            assertFalse(isWithinQuietHours(hour = hour, startHour = 4, endHour = 4))
        }
    }
}
