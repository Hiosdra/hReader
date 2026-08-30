package com.hiosdra.hreader.presentation.navigation

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RoutesTestApplication::class, sdk = [35])
class RoutesTest {

    @Test
    fun `feed route encodes a shared url as one navigation argument`() {
        assertEquals(
            "add_feed?url=https%3A%2F%2Fexample.com%2Ffeed%3Fsource%3Dshare%26kind%3Drss",
            Routes.addFeed("https://example.com/feed?source=share&kind=rss")
        )
    }

    @Test
    fun `empty feed url uses the url-less route`() {
        assertEquals("add_feed", Routes.addFeed())
        assertEquals("add_feed", Routes.addFeed("  "))
    }

    @Test
    fun `article route preserves reader session arguments`() {
        assertEquals(
            "article?feedId=-1&startId=42&includeRead=true&session=1234",
            Routes.article(
                feedId = null,
                startArticleId = 42L,
                includeRead = true,
                sessionStartMillis = 1234L
            )
        )
    }

    @Test
    fun `feed route contains the feed id`() {
        assertEquals("feed/17", Routes.feed(17L))
    }
}

private class RoutesTestApplication : Application()
