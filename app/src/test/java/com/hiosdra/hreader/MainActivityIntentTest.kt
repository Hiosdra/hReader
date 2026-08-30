package com.hiosdra.hreader

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.hiosdra.hreader.presentation.navigation.EntryPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainActivityIntentTestApplication::class, sdk = [35])
class MainActivityIntentTest {

    @Test
    fun `shared text uses the first valid web address`() {
        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, "Subscribe here https://example.com/feed?format=xml")

        assertEquals(
            EntryPoint.AddFeed("https://example.com/feed?format=xml"),
            intent.entryPoint()
        )
    }

    @Test
    fun `shortcut view intent opens add feed without a url`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("hreader://subscribe"))

        assertEquals(EntryPoint.AddFeed(null), intent.entryPoint())
    }

    @Test
    fun `view intent with a web address opens add feed`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/feed"))

        assertEquals(EntryPoint.AddFeed("https://example.com/feed"), intent.entryPoint())
    }

    @Test
    fun `unsupported or invalid intent opens the article list`() {
        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, "not a feed address")

        assertEquals(EntryPoint.ArticleList, intent.entryPoint())
    }
}

private class MainActivityIntentTestApplication : Application()
