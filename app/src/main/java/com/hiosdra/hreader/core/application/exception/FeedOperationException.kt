package com.hiosdra.hreader.core.application.exception

import java.io.IOException

enum class FeedOperationFailureReason {
    SERVER,
    UNREACHABLE,
    TIMEOUT,
    UNKNOWN
}

class FeedOperationException(
    val reason: FeedOperationFailureReason,
    val serverMessage: String? = null,
    cause: Throwable? = null
) : IOException(cause?.message, cause)
