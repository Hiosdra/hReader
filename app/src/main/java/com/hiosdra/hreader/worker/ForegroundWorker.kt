package com.hiosdra.hreader.worker

internal suspend fun setForegroundIfAllowed(
    setForeground: suspend () -> Unit
): Boolean = try {
    setForeground()
    true
} catch (_: IllegalStateException) {
    false
}
