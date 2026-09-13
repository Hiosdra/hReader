package com.hiosdra.hreader.adapter.network

import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.random.Random

internal const val RETRY_AFTER_HEADER = "Retry-After"

private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500
private const val JITTER_MILLIS = 250L
private const val MAX_BACKOFF_DOUBLINGS = 6

internal class HttpStatusException(
    val statusCode: Int,
    val retryAfter: String?
) : IOException("HTTP $statusCode")

internal class NonRetryableNetworkException(message: String) : IOException(message)

internal suspend fun <T> withNetworkRetries(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 500,
    nowMillis: () -> Long = System::currentTimeMillis,
    delayMillis: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T
): T {
    require(maxAttempts >= 1)
    require(initialDelayMillis >= 0)
    var attempts = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempts++
            if (attempts >= maxAttempts || !e.isNetworkRetryable()) throw e
            delayMillis(retryDelayMillis(e, initialDelayMillis, attempts, nowMillis()))
        }
    }
}

internal fun Throwable.isNetworkRetryable(): Boolean = when (this) {
    is CancellationException -> false
    is BackendNotConfiguredException -> false
    is NonRetryableNetworkException -> false
    is HttpStatusException -> statusCode >= FIRST_SERVER_ERROR || statusCode == TOO_MANY_REQUESTS
    is HttpException -> code() >= FIRST_SERVER_ERROR || code() == TOO_MANY_REQUESTS
    is IOException -> true
    else -> false
}

internal fun retryDelayMillis(
    error: Throwable,
    initialDelayMillis: Long,
    attempt: Int,
    nowMillis: Long
): Long {
    val multiplier = 1L shl (attempt - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)
    val exponentialDelay = if (initialDelayMillis > Long.MAX_VALUE / multiplier) {
        Long.MAX_VALUE
    } else {
        initialDelayMillis * multiplier
    }
    val serverDelay = retryAfterMillis(error.retryAfterValue(), nowMillis) ?: 0L
    val baseDelay = max(exponentialDelay, serverDelay)
    val jitter = Random.nextLong(JITTER_MILLIS)
    return if (baseDelay > Long.MAX_VALUE - jitter) Long.MAX_VALUE else baseDelay + jitter
}

internal fun retryAfterMillis(value: String?, nowMillis: Long): Long? {
    val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    header.toLongOrNull()?.let { seconds ->
        if (seconds < 0) return null
        return if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else seconds * 1_000
    }
    return runCatching {
        val targetMillis = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        if (targetMillis <= nowMillis) 0L else targetMillis - nowMillis
    }.getOrNull()
}

private fun Throwable.retryAfterValue(): String? = when (this) {
    is HttpStatusException -> retryAfter
    is HttpException -> response()?.headers()?.get(RETRY_AFTER_HEADER)
    else -> null
}
