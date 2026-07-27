package com.hiosdra.hreader.data.remote

import kotlinx.coroutines.delay

internal suspend fun <T> withRetries(
    maxAttempts: Int = 5,
    delayMillis: Long = 500,
    block: suspend () -> T
): T {
    require(maxAttempts >= 1)
    var attempts = 0
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            attempts++
            if (attempts >= maxAttempts) throw e
            delay(delayMillis)
        }
    }
}
