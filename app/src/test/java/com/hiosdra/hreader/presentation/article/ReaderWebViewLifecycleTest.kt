package com.hiosdra.hreader.presentation.article

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ReaderWebViewLifecycleTestApplication::class, sdk = [35])
class ReaderWebViewLifecycleTest {

    @Test
    fun `vertical gesture keeps pager disallowed until the gesture ends`() {
        val parent = RecordingParent(RuntimeEnvironment.getApplication())
        val webView = createWebView(parent)
        try {
            dispatch(webView, MotionEvent.ACTION_DOWN, 100f, 100f)
            assertTrue(parent.lastDisallowIntercept == true)

            dispatch(webView, MotionEvent.ACTION_MOVE, 103f, 180f)
            assertTrue(parent.lastDisallowIntercept == true)

            dispatch(webView, MotionEvent.ACTION_UP, 103f, 180f)
            assertFalse(parent.lastDisallowIntercept == true)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `horizontal gesture gives the pager its interception back`() {
        val parent = RecordingParent(RuntimeEnvironment.getApplication())
        val webView = createWebView(parent)
        try {
            dispatch(webView, MotionEvent.ACTION_DOWN, 100f, 100f)
            dispatch(webView, MotionEvent.ACTION_MOVE, 180f, 103f)

            assertFalse(parent.lastDisallowIntercept == true)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `scrolling can be disabled without changing the view contract`() {
        val webView = createWebView()
        try {
            webView.allowScroll = false
            webView.scrollTo(0, 200)
            assertEquals(0, webView.scrollY)

            webView.allowScroll = true
            webView.scrollTo(0, 200)
            assertEquals(200, webView.scrollY)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `a simple tap reports its coordinates for speech seeking`() {
        val webView = createWebView()
        var tap: Pair<Float, Float>? = null
        webView.onSpeechTap = { x, y -> tap = x to y }
        try {
            dispatch(webView, MotionEvent.ACTION_DOWN, 40f, 80f)
            dispatch(webView, MotionEvent.ACTION_UP, 40f, 80f)

            assertEquals(40f to 80f, tap)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `long press does not dispatch a speech tap when selection starts`() {
        val webView = createWebView()
        var tapCount = 0
        webView.onSpeechTap = { _, _ -> tapCount += 1 }
        try {
            dispatch(webView, MotionEvent.ACTION_DOWN, 40f, 80f)
            webView.speechSelectionActionEnabled = true
            webView.suppressNextClick = true
            dispatch(webView, MotionEvent.ACTION_UP, 40f, 80f)

            assertEquals(0, tapCount)
        } finally {
            webView.releaseResources()
        }
    }

    @Test
    fun `posted work and controller movement stop after release`() {
        val webView = createWebView()
        val controller = ArticleWebViewScrollController()
        var callbackCount = 0
        try {
            webView.scrollTo(0, 100)
            controller.attach(webView)
            webView.postIfActive { callbackCount += 1 }
            webView.releaseResources()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertTrue(webView.isReleased)
            assertEquals(0, callbackCount)
            assertEquals(0f, controller.consumeComposeScrollDelta(-50f), 0f)
            assertEquals(100, webView.scrollY)
        } finally {
            webView.releaseResources()
        }
    }

    private fun createWebView(parent: RecordingParent? = null): ReaderWebView {
        val webView = ReaderWebView(RuntimeEnvironment.getApplication()).apply {
            allowScroll = true
            protectVerticalScrollFromPager = true
            layout(0, 0, 400, 800)
        }
        parent?.apply {
            addView(webView)
            layout(0, 0, 400, 800)
        }
        return webView
    }

    private fun dispatch(webView: ReaderWebView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        try {
            webView.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}

private class RecordingParent(context: Context) : FrameLayout(context) {
    var lastDisallowIntercept: Boolean? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        lastDisallowIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}

private class ReaderWebViewLifecycleTestApplication : Application()
