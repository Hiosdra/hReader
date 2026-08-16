package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.random.Random

private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500
private const val JITTER_MILLIS = 250L

/** Keeps the doubling from overflowing if a caller ever asks for a long retry chain. */
private const val MAX_BACKOFF_DOUBLINGS = 6

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
    require(maxAttempts >= 1)
    var attempts = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempts++
            if (attempts >= maxAttempts || !e.isRetryable()) throw e
            delay(backoffMillis(initialDelayMillis, attempts))
        }
    }
}

internal fun Throwable.isRetryable(): Boolean = when (this) {
    is CancellationException -> false
    is BackendNotConfiguredException -> false
    is HttpException -> code() >= FIRST_SERVER_ERROR || code() == TOO_MANY_REQUESTS
    is IOException -> true
    else -> false
}

private fun backoffMillis(initialDelayMillis: Long, attempt: Int): Long =
    initialDelayMillis * (1L shl (attempt - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)) +
        Random.nextLong(JITTER_MILLIS)
