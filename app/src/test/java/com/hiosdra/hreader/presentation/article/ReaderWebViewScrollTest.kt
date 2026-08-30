package com.hiosdra.hreader.presentation.article

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ReaderWebViewTestApplication::class, sdk = [35])
class ReaderWebViewScrollTest {

    @Test
    fun `Compose header scroll uses the actual ReaderWebView offset`() {
        val webView = createWebView()
        try {
            webView.scrollTo(0, 200)
            val controller = ArticleWebViewScrollController()
            controller.attach(webView)

            val consumed = controller.consumeComposeScrollDelta(80f)

            assertEquals(120, webView.scrollY)
            assertEquals(80f, consumed, 0f)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `released ReaderWebView receives no delayed controller movement`() {
        val webView = createWebView()
        try {
            val controller = ArticleWebViewScrollController()
            controller.attach(webView)
            controller.detach(webView)

            val consumed = controller.consumeComposeScrollDelta(-100f)

            assertEquals(0, webView.scrollY)
            assertEquals(0f, consumed, 0f)
        } finally {
            webView.releaseResources()
        }
    }

    private fun createWebView(): ReaderWebView = ReaderWebView(
        RuntimeEnvironment.getApplication()
    ).apply {
        allowScroll = true
        protectVerticalScrollFromPager = true
        layout(0, 0, 400, 800)
    }

}

private class ReaderWebViewTestApplication : Application()
