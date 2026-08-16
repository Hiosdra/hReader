package com.hiosdra.hreader.core.domain.service

/**
 * Whether [hour] falls inside the window that starts at [startHour] and ends at [endHour].
 *
 * The window normally wraps midnight — 23 to 7 is the point of it — so a plain range check would
 * be empty exactly when it matters. A start equal to the end means no quiet hours rather than a
 * whole silent day: that reading would stop the app syncing altogether.
 */
fun isWithinQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean = when {
    startHour == endHour -> false
    startHour < endHour -> hour in startHour until endHour
    else -> hour >= startHour || hour < endHour
}
