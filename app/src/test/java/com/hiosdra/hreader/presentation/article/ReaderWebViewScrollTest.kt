package com.hiosdra.hreader.presentation.article

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
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
    fun `forward gesture hands the header delta to the parent`() {
        val webView = createWebView()
        try {
            var parentDelta = 0f
            webView.onParentScrollDelta = { delta ->
                parentDelta += delta
                delta
            }

            sendEvent(webView, MotionEvent.ACTION_DOWN, 600f)
            sendEvent(webView, MotionEvent.ACTION_MOVE, 400f)
            sendEvent(webView, MotionEvent.ACTION_UP, 400f)

            assertEquals(200f, parentDelta, 0.001f)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `reverse gesture hands only the part beyond the body start to the parent`() {
        val webView = createWebView()
        try {
            webView.scrollTo(0, 80)
            var parentDelta = 0f
            webView.onParentScrollDelta = { delta ->
                parentDelta += delta
                delta
            }

            sendEvent(webView, MotionEvent.ACTION_DOWN, 200f)
            sendEvent(webView, MotionEvent.ACTION_MOVE, 300f)
            sendEvent(webView, MotionEvent.ACTION_UP, 300f)

            assertEquals(-20f, parentDelta, 0.001f)
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

    private fun sendEvent(webView: ReaderWebView, action: Int, y: Float) {
        val eventTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            eventTime,
            eventTime,
            action,
            100f,
            y,
            0
        )
        try {
            webView.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}

private class ReaderWebViewTestApplication : Application()
