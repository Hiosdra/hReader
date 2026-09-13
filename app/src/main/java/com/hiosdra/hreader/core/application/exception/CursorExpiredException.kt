package com.hiosdra.hreader.core.application.exception

import java.io.IOException

class CursorExpiredException(cause: Throwable? = null) : IOException(
    "The backend continuation cursor expired",
    cause
)
