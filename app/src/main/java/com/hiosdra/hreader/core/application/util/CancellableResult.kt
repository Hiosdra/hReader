package com.hiosdra.hreader.core.application.util

import kotlinx.coroutines.CancellationException

suspend inline fun <T> runCatchingCancellable(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
