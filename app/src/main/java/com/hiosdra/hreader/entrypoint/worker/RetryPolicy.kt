package com.hiosdra.hreader.entrypoint.worker

import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

private const val TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500

internal fun Throwable.isRetryable(): Boolean = when (this) {
    is CancellationException -> false
    is BackendNotConfiguredException -> false
    is HttpException -> code() >= FIRST_SERVER_ERROR || code() == TOO_MANY_REQUESTS
    is IOException -> true
    else -> false
}
