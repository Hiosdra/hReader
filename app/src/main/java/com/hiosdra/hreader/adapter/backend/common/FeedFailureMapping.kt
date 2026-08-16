package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.application.exception.FeedOperationException
import com.hiosdra.hreader.core.application.exception.FeedOperationFailureReason
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal suspend fun <T> withFeedFailureMapping(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw e.toFeedOperationException()
}

private fun Throwable.toFeedOperationException(): FeedOperationException = when (this) {
    is FeedOperationException -> this
    is HttpException -> FeedOperationException(
        reason = FeedOperationFailureReason.SERVER,
        serverMessage = response()?.errorBody()?.string().extractServerMessage(),
        cause = this
    )
    is UnknownHostException, is ConnectException -> FeedOperationException(
        reason = FeedOperationFailureReason.UNREACHABLE,
        cause = this
    )
    is SocketTimeoutException -> FeedOperationException(
        reason = FeedOperationFailureReason.TIMEOUT,
        cause = this
    )
    else -> FeedOperationException(
        reason = FeedOperationFailureReason.UNKNOWN,
        cause = this
    )
}

private fun String?.extractServerMessage(): String? = takeUnless { it.isNullOrBlank() }
    ?.let { body ->
        runCatching { JSONObject(body).optString("error_message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
