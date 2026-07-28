package com.hiosdra.hreader.data.remote

import java.io.IOException

/**
 * The server address or credentials are missing. Interceptors can only fail with an [IOException],
 * but unlike a real transport error this one cannot resolve itself, so it is never retried.
 */
class BackendNotConfiguredException(message: String) : IOException(message)
