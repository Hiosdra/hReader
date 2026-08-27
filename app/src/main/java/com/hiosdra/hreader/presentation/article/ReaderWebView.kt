package com.hiosdra.hreader.presentation.article

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.R
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CONTENT_HEIGHT_UPDATE_ATTEMPTS = 12
private const val CONTENT_HEIGHT_UPDATE_DELAY_MS = 100L
private const val CONTENT_HEIGHT_STABLE_SAMPLES = 2

internal enum class ReaderGestureDirection {
    Horizontal,
    Vertical
}

internal fun readerWebViewIsScrollable(contentHeightPx: Float, viewportHeightPx: Float): Boolean =
    viewportHeightPx > 0f && contentHeightPx > viewportHeightPx

internal fun readerWebViewScrollbarThumbFraction(
    contentHeightPx: Float,
    viewportHeightPx: Float
): Float =
    if (!readerWebViewIsScrollable(contentHeightPx, viewportHeightPx)) {
        1f
    } else {
        (viewportHeightPx / contentHeightPx).coerceIn(0f, 1f)
    }

internal fun readerWebViewScrollProgress(
    scrollY: Int,
    contentHeightPx: Float,
    viewportHeightPx: Float
): Float {
    if (!readerWebViewIsScrollable(contentHeightPx, viewportHeightPx)) return 0f
    val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(1f)
    return (scrollY.toFloat() / maxScrollPx).coerceIn(0f, 1f)
}

internal fun contentHeightIsSettled(
    previousHeightPx: Int,
    candidateHeightPx: Int,
    stableSamples: Int,
    requiredStableSamples: Int = CONTENT_HEIGHT_STABLE_SAMPLES
): Boolean = candidateHeightPx > 0 &&
    candidateHeightPx == previousHeightPx &&
    stableSamples >= requiredStableSamples

internal class ReaderWebViewScrollProgressReporter(
    private val onChanged: (progress: Float, isScrollable: Boolean, thumbFraction: Float) -> Unit
) {
    private var lastProgress = -1f
    private var lastScrollable = false
    private var lastThumbFraction = 1f
    private var lastReportedAt = 0L

    fun update(webView: ReaderWebView): Float {
        if (webView.isReleased) return 0f
        val density = webView.resources.displayMetrics.density
        val contentHeightPx = webView.contentHeight * density
        val viewportHeightPx = webView.height.toFloat()
        val isScrollable = readerWebViewIsScrollable(contentHeightPx, viewportHeightPx)
        val thumbFraction = readerWebViewScrollbarThumbFraction(contentHeightPx, viewportHeightPx)
        val progress = readerWebViewScrollProgress(
            scrollY = webView.scrollY,
            contentHeightPx = contentHeightPx,
            viewportHeightPx = viewportHeightPx
        )
        val now = SystemClock.elapsedRealtime()
        if (
            isScrollable != lastScrollable ||
            abs(progress - lastProgress) >= 0.01f ||
            abs(thumbFraction - lastThumbFraction) >= 0.01f ||
            now - lastReportedAt >= 500L
        ) {
            lastProgress = progress
            lastScrollable = isScrollable
            lastThumbFraction = thumbFraction
            lastReportedAt = now
            onChanged(progress, isScrollable, thumbFraction)
        }
        return progress
    }
}

internal fun readerGestureDirection(
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float
): ReaderGestureDirection? {
    val absoluteX = abs(deltaX)
    val absoluteY = abs(deltaY)
    if (maxOf(absoluteX, absoluteY) <= touchSlop) return null

    val directionalBias = 1.25f
    return when {
        absoluteX > absoluteY * directionalBias -> ReaderGestureDirection.Horizontal
        absoluteY > absoluteX * directionalBias -> ReaderGestureDirection.Vertical
        else -> null
    }
}

@Composable
internal fun ReaderWebViewError(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.article_web_error), textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.article_try_again)) }
        }
    }
}

internal class ReaderWebView(context: Context) : WebView(context) {
    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    private var released = false
    private var contentHeightUpdateRunnable: Runnable? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchInProgress = false
    private var pagerGestureDirection: ReaderGestureDirection? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    var allowScroll: Boolean = true
    var protectVerticalScrollFromPager: Boolean = false
        set(value) {
            field = value
            if (!value) {
                parent?.requestDisallowInterceptTouchEvent(false)
                touchInProgress = false
                pagerGestureDirection = null
            }
        }

    val isReleased: Boolean
        get() = released

    fun postIfActive(action: () -> Unit) {
        if (released) return
        post {
            if (!released) action()
        }
    }

    fun scheduleContentHeightUpdates(onHeightChanged: (Int) -> Unit) {
        if (contentHeightUpdateRunnable != null) return
        val update = object : Runnable {
            private var attempts = 0
            private var lastHeight = 0
            private var stableSamples = 0
            private var reportedHeight = 0

            override fun run() {
                if (released) return
                val height = (contentHeight * resources.displayMetrics.density).roundToInt()
                if (height > 0) {
                    if (height == lastHeight) {
                        stableSamples += 1
                    } else {
                        lastHeight = height
                        stableSamples = 1
                    }
                    val isFinalAttempt = attempts >= CONTENT_HEIGHT_UPDATE_ATTEMPTS
                    if (
                        (contentHeightIsSettled(lastHeight, height, stableSamples) || isFinalAttempt) &&
                        height != reportedHeight
                    ) {
                        reportedHeight = height
                        onHeightChanged(height)
                    }
                }
                if (attempts < CONTENT_HEIGHT_UPDATE_ATTEMPTS) {
                    attempts += 1
                    postDelayed(this, CONTENT_HEIGHT_UPDATE_DELAY_MS)
                } else {
                    contentHeightUpdateRunnable = null
                }
            }
        }
        contentHeightUpdateRunnable = update
        post(update)
    }

    fun releaseResources() {
        if (released) return
        released = true
        clearCallbacksAndClients()
        stopLoading()
        removeAllViews()
        destroy()
    }

    internal fun destroyAfterRenderProcessGone() {
        if (released) return
        released = true
        clearCallbacksAndClients()
        (parent as? ViewGroup)?.removeView(this)
        destroy()
    }

    private fun clearCallbacksAndClients() {
        parent?.requestDisallowInterceptTouchEvent(false)
        touchInProgress = false
        pagerGestureDirection = null
        contentHeightUpdateRunnable?.let(::removeCallbacks)
        contentHeightUpdateRunnable = null
        setOnScrollChangeListener(null)
        setOnLongClickListener(null)
        webViewClient = WebViewClient()
        webChromeClient = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val clickDetected = event.actionMasked == MotionEvent.ACTION_UP &&
            touchInProgress &&
            abs(event.x - initialTouchX) <= touchSlop &&
            abs(event.y - initialTouchY) <= touchSlop

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                touchInProgress = true
                pagerGestureDirection = null
                parent?.requestDisallowInterceptTouchEvent(protectVerticalScrollFromPager)
            }
            MotionEvent.ACTION_MOVE -> {
                if (protectVerticalScrollFromPager && pagerGestureDirection == null) {
                    pagerGestureDirection = readerGestureDirection(
                        deltaX = event.x - initialTouchX,
                        deltaY = event.y - initialTouchY,
                        touchSlop = touchSlop
                    )
                    if (pagerGestureDirection == ReaderGestureDirection.Horizontal) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                touchInProgress = false
                pagerGestureDirection = null
            }
        }

        val handled = super.onTouchEvent(event)
        if (clickDetected) performClick()
        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        parent?.requestDisallowInterceptTouchEvent(false)
        touchInProgress = false
        super.onDetachedFromWindow()
    }

    override fun scrollTo(x: Int, y: Int) {
        if (isReleased) return
        super.scrollTo(x, if (allowScroll) y else 0)
    }

    override fun scrollBy(x: Int, y: Int) {
        if (isReleased) return
        super.scrollBy(x, if (allowScroll) y else 0)
    }
}
