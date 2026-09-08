package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import com.hiosdra.hreader.core.application.exception.FeedOperationException
import com.hiosdra.hreader.core.application.exception.FeedOperationFailureReason
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500

internal fun Throwable.toSyncFailure(stage: SyncFailureStage): SyncFailure = SyncFailure(
    stage = stage,
    reason = when (this) {
        is BackendNotConfiguredException -> SyncFailureReason.CONFIGURATION
        is FeedOperationException -> when (reason) {
            FeedOperationFailureReason.SERVER -> SyncFailureReason.SERVER
            FeedOperationFailureReason.UNREACHABLE -> SyncFailureReason.NETWORK
            FeedOperationFailureReason.TIMEOUT -> SyncFailureReason.TIMEOUT
            FeedOperationFailureReason.UNKNOWN -> SyncFailureReason.UNKNOWN
        }
        is SocketTimeoutException -> SyncFailureReason.TIMEOUT
        is UnknownHostException, is ConnectException, is IOException -> SyncFailureReason.NETWORK
        is HttpException -> SyncFailureReason.SERVER
        else -> SyncFailureReason.UNKNOWN
    },
    retryable = isSyncRetryable()
)

private fun Throwable.isSyncRetryable(): Boolean = when (this) {
    is CancellationException -> false
    is BackendNotConfiguredException -> false
    is HttpException -> code() >= FIRST_SERVER_ERROR || code() == TOO_MANY_REQUESTS
    is IOException -> true
    else -> false
}
