package com.hiosdra.hreader.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class RetriesTest {

    @Test
    fun `a transport failure is retried until it succeeds`() = runBlocking {
        var attempts = 0

        val result = withRetries(maxAttempts = 3, initialDelayMillis = 1) {
            attempts++
            if (attempts < 3) throw SocketTimeoutException("timeout") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `a server error is retried`() {
        var attempts = 0

        runCatching {
            runBlocking {
                withRetries(maxAttempts = 3, initialDelayMillis = 1) {
                    attempts++
                    throw httpException(503)
                }
            }
        }

        assertEquals(3, attempts)
    }

    @Test
    fun `a rejected request is not retried`() {
        var attempts = 0

        runCatching {
            runBlocking {
                withRetries(maxAttempts = 5, initialDelayMillis = 1) {
                    attempts++
                    throw httpException(401)
                }
            }
        }

        assertEquals("A bad token fails the same way every time", 1, attempts)
    }

    @Test
    fun `a cancellation propagates without another attempt`() {
        var attempts = 0

        val outcome = runCatching {
            runBlocking {
                withRetries(maxAttempts = 5, initialDelayMillis = 1) {
                    attempts++
                    throw CancellationException("cancelled")
                }
            }
        }

        assertEquals(1, attempts)
        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `a missing server configuration is not retried`() {
        var attempts = 0

        runCatching {
            runBlocking {
                withRetries(maxAttempts = 5, initialDelayMillis = 1) {
                    attempts++
                    throw BackendNotConfiguredException("no token")
                }
            }
        }

        assertEquals("Retrying cannot configure the backend", 1, attempts)
    }

    @Test
    fun `only recoverable failures are retryable`() {
        assertTrue(IOException("dropped").isRetryable())
        assertTrue(httpException(500).isRetryable())
        assertTrue(httpException(429).isRetryable())
        assertFalse(httpException(400).isRetryable())
        assertFalse(httpException(404).isRetryable())
        assertFalse(IllegalStateException("bug").isRetryable())
        assertFalse(CancellationException("cancelled").isRetryable())
        assertFalse(BackendNotConfiguredException("no token").isRetryable())
    }

    private fun httpException(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )
}
