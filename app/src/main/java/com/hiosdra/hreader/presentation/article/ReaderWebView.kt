package com.hiosdra.hreader.presentation.article

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
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
private const val READ_FROM_SELECTION_MENU_ID = 0x48525453

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

internal class ArticleWebViewScrollController {
    private var webView: ReaderWebView? = null
    private var scrollTarget: ArticleWebViewScrollTarget? = null
    private var fractionalScrollY = 0f

    internal fun attach(webView: ReaderWebView) {
        if (this.webView === webView) return
        this.webView = webView
        scrollTarget = object : ArticleWebViewScrollTarget {
            override val scrollY: Int
                get() = webView.scrollY

            override fun scrollBy(deltaY: Int) {
                webView.scrollBy(0, deltaY)
            }
        }
        fractionalScrollY = 0f
    }

    internal fun attachForTest(scrollTarget: ArticleWebViewScrollTarget) {
        webView = null
        this.scrollTarget = scrollTarget
        fractionalScrollY = 0f
    }

    internal fun detach(webView: ReaderWebView) {
        if (this.webView !== webView) return
        this.webView = null
        scrollTarget = null
        fractionalScrollY = 0f
    }

    fun consumeComposeScrollDelta(deltaY: Float): Float {
        val target = scrollTarget ?: return 0f
        val requestedScrollY = -deltaY + fractionalScrollY
        val requestedScrollYPx = requestedScrollY.roundToInt()
        if (requestedScrollYPx == 0) {
            fractionalScrollY = requestedScrollY
            return 0f
        }

        val previousScrollY = target.scrollY
        target.scrollBy(requestedScrollYPx)
        val consumedScrollYPx = target.scrollY - previousScrollY
        fractionalScrollY = if (consumedScrollYPx == requestedScrollYPx) {
            requestedScrollY - requestedScrollYPx
        } else {
            0f
        }
        return -consumedScrollYPx.toFloat()
    }
}

internal interface ArticleWebViewScrollTarget {
    val scrollY: Int

    fun scrollBy(deltaY: Int)
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
    private var lastClickX = 0f
    private var lastClickY = 0f
    private var dispatchSpeechTapOnClick = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    internal var loadedContentTopInsetPx: Int = 0
    internal var pageLoadRestoreScrollY: Int = 0
    internal var contentLayoutReady: Boolean = false
    internal var onSpeechTap: ((Float, Float) -> Unit)? = null
    internal var onReadFromSelection: ((Int) -> Unit)? = null
    internal var speechSelectionActionEnabled: Boolean = false
    internal var suppressNextClick: Boolean = false

    var allowScroll: Boolean = true
    var protectVerticalScrollFromPager: Boolean = false
        set(value) {
            field = value
            if (!value) {
                parent?.requestDisallowInterceptTouchEvent(false)
                clearTouchState()
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

    fun cancelContentHeightUpdates() {
        contentHeightUpdateRunnable?.let(::removeCallbacks)
        contentHeightUpdateRunnable = null
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
        clearTouchState()
        cancelContentHeightUpdates()
        setOnScrollChangeListener(null)
        setOnLongClickListener(null)
        onSpeechTap = null
        onReadFromSelection = null
        speechSelectionActionEnabled = false
        suppressNextClick = false
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
                speechSelectionActionEnabled = false
                suppressNextClick = false
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
            }
        }

        val handled = try {
            super.onTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                clearTouchState()
            }
        }
        if (clickDetected) {
            lastClickX = event.x
            lastClickY = event.y
            dispatchSpeechTapOnClick = true
            performClick()
        }
        return handled
    }

    private fun clearTouchState() {
        touchInProgress = false
        pagerGestureDirection = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (dispatchSpeechTapOnClick && !suppressNextClick) {
            onSpeechTap?.invoke(lastClickX, lastClickY)
        }
        dispatchSpeechTapOnClick = false
        suppressNextClick = false
        return true
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        startActionMode(callback, ActionMode.TYPE_PRIMARY)

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val selectionCallback = onReadFromSelection
        val wrappedCallback = if (speechSelectionActionEnabled && selectionCallback != null) {
            SpeechSelectionActionModeCallback(this, callback, selectionCallback)
        } else {
            callback
        }
        return super.startActionMode(wrappedCallback, type)
    }

    internal fun requestSelectedSpeechOffset(onOffset: (Int) -> Unit) {
        if (!speechSelectionActionEnabled || !settings.javaScriptEnabled || !contentLayoutReady || isReleased) return
        evaluateJavascript("window.__hreaderTts && window.__hreaderTts.selectionStart()") { result ->
            result.toJavascriptInt()?.let(onOffset)
        }
    }

    override fun onDetachedFromWindow() {
        parent?.requestDisallowInterceptTouchEvent(false)
        clearTouchState()
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

private class SpeechSelectionActionModeCallback(
    private val webView: ReaderWebView,
    private val delegate: ActionMode.Callback,
    private val onReadFromSelection: (Int) -> Unit
) : ActionMode.Callback2() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val created = delegate.onCreateActionMode(mode, menu)
        if (created) addReadAction(menu)
        return created
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val prepared = delegate.onPrepareActionMode(mode, menu)
        addReadAction(menu)
        return prepared
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == READ_FROM_SELECTION_MENU_ID) {
            webView.requestSelectedSpeechOffset { offset ->
                onReadFromSelection(offset)
                mode.finish()
            }
            return true
        }
        return delegate.onActionItemClicked(mode, item)
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        delegate.onDestroyActionMode(mode)
        webView.speechSelectionActionEnabled = false
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        (delegate as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect)
    }

    private fun addReadAction(menu: Menu) {
        if (menu.findItem(READ_FROM_SELECTION_MENU_ID) != null) return
        menu.add(
            Menu.NONE,
            READ_FROM_SELECTION_MENU_ID,
            Menu.NONE,
            webView.context.getString(R.string.article_read_from_here)
        ).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
}

private fun String.toJavascriptInt(): Int? =
    trim().removeSurrounding("\"").toDoubleOrNull()?.toInt()
