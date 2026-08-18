package com.hiosdra.hreader.adapter.backend

import com.hiosdra.hreader.adapter.backend.freshrss.FreshRssApiService
import com.hiosdra.hreader.adapter.backend.freshrss.FreshRssBackend
import com.hiosdra.hreader.adapter.backend.freshrss.dto.SubscriptionListResponse
import com.hiosdra.hreader.adapter.backend.miniflux.MinifluxApiService
import com.hiosdra.hreader.adapter.backend.miniflux.MinifluxBackend
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class BackendVerificationTest {
    @Test
    fun `FreshRSS verification uses the backend retry policy`() = runBlocking {
        val api = mockk<FreshRssApiService>()
        var attempts = 0
        coEvery { api.getSubscriptions(any()) } answers {
            attempts++
            if (attempts == 1) throw IOException("temporary outage")
            SubscriptionListResponse()
        }

        assertEquals(0, FreshRssBackend(api, mockk(relaxed = true), mockk()).verifyConnection())
        assertEquals(2, attempts)
    }

    @Test
    fun `Miniflux verification uses the backend retry policy`() = runBlocking {
        val api = mockk<MinifluxApiService>()
        var attempts = 0
        coEvery { api.getFeeds() } answers {
            attempts++
            if (attempts == 1) throw IOException("temporary outage")
            emptyList()
        }

        assertEquals(0, MinifluxBackend(api).verifyConnection())
        assertEquals(2, attempts)
    }
}
