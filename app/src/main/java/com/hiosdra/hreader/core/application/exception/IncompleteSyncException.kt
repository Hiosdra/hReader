package com.hiosdra.hreader.core.application.exception

import java.io.IOException

class IncompleteSyncException(message: String) : IOException(message)

class CursorExpiredException(cause: Throwable? = null) : IOException(
    "The backend continuation cursor expired",
    cause
)
