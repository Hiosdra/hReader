package com.hiosdra.hreader.adapter.backend.miniflux

import android.content.Context
import com.hiosdra.hreader.adapter.backend.common.ServerConfig
import com.hiosdra.hreader.core.domain.model.BackendType
import io.mockk.every
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinifluxAuthInterceptorTest {
    @Test
    fun `adds token only to the configured Miniflux origin`() {
        val config = mockk<ServerConfig>()
        val context = context()
        every { config.minifluxBaseUrl() } returns "https://reader.example/".toHttpUrl()
        every { config.secretFor(BackendType.MINIFLUX) } returns "token"
        val forwarded = slot<Request>()
        val chain = chain(
            Request.Builder().url("https://reader.example/api/v1/feeds").build(),
            forwarded
        )

        MinifluxAuthInterceptor(config, context).intercept(chain)

        assertEquals("token", forwarded.captured.header("X-Auth-Token"))
    }

    @Test
    fun `strips token from a cross-origin redirect request`() {
        val config = mockk<ServerConfig>()
        val context = context()
        every { config.minifluxBaseUrl() } returns "https://reader.example/".toHttpUrl()
        val forwarded = slot<Request>()
        val chain = chain(
            Request.Builder()
                .url("https://attacker.example/redirect")
                .header("X-Auth-Token", "token")
                .build(),
            forwarded
        )

        MinifluxAuthInterceptor(config, context).intercept(chain)

        assertNull(forwarded.captured.header("X-Auth-Token"))
    }

    private fun context(): Context = mockk<Context>().also { context ->
        every { context.applicationContext } returns context
    }

    private fun chain(request: Request, forwarded: io.mockk.CapturingSlot<Request>): Interceptor.Chain =
        mockk<Interceptor.Chain>().also { chain ->
            every { chain.request() } returns request
            every { chain.proceed(capture(forwarded)) } returns mockk<Response>()
        }
}
