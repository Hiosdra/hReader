package com.hiosdra.hreader.entrypoint.worker

internal suspend fun setForegroundIfAllowed(
    setForeground: suspend () -> Unit
): Boolean = try {
    setForeground()
    true
} catch (_: IllegalStateException) {
    false
}
