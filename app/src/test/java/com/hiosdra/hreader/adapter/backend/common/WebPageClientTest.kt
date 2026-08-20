package com.hiosdra.hreader.adapter.backend.common

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPageClientTest {
    @Test
    fun `reads a response up to the configured byte limit`() {
        val body = "hello".toResponseBody("text/html".toMediaType())

        body.use {
            assertEquals("hello", readBoundedBody(it, maxBytes = 5))
        }
    }

    @Test(expected = IOException::class)
    fun `rejects a response above the configured byte limit`() {
        val body = "hello!".toResponseBody("text/html".toMediaType())

        body.use {
            readBoundedBody(it, maxBytes = 5)
        }
    }
}
