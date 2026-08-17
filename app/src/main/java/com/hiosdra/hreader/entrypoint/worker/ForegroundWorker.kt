package com.hiosdra.hreader.entrypoint.worker

import androidx.work.ForegroundInfo

internal suspend fun setForegroundIfAllowed(
    getForegroundInfo: suspend () -> ForegroundInfo,
    setForeground: suspend (ForegroundInfo) -> Unit
): Boolean {
    val foregroundInfo = getForegroundInfo()
    return try {
        setForeground(foregroundInfo)
        true
    } catch (_: IllegalStateException) {
        false
    }
}
