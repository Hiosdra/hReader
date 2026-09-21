package com.hiosdra.hreader.core.domain.service

fun isWithinQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean = when {
    startHour == endHour -> false
    startHour < endHour -> hour in startHour until endHour
    else -> hour >= startHour || hour < endHour
}
