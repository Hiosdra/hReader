package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.adapter.network.isNetworkRetryable
import com.hiosdra.hreader.adapter.network.withNetworkRetries
import com.hiosdra.hreader.core.application.exception.CursorExpiredException
import retrofit2.HttpException

/**
 * Retries [block] on failures the backend can plausibly recover from: transport errors and
 * server-side 5xx/429 responses. A 4xx is a bug in the request or a bad token, so repeating it
 * only wastes time, and a cancellation must propagate untouched or the caller can never be
 * cancelled. Only wrap idempotent calls — a retried POST can create duplicates.
 */
internal suspend fun <T> withRetries(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 500,
    block: suspend () -> T
): T {
    return withNetworkRetries(
        maxAttempts = maxAttempts,
        initialDelayMillis = initialDelayMillis,
        block = block
    )
}

internal suspend fun <T> withCursorRetries(cursor: String?, block: suspend () -> T): T =
    try {
        withRetries(block = block)
    } catch (e: HttpException) {
        if (cursor != null && e.code() in setOf(400, 404, 410, 422)) {
            throw CursorExpiredException(e)
        }
        throw e
    }

internal fun Throwable.isRetryable(): Boolean = isNetworkRetryable()
