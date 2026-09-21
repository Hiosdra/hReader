package com.hiosdra.hreader.presentation.article

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ArticleReadingWebView(
    entryId: Long,
    pageKey: Any,
    contentKey: Int,
    modifier: Modifier,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit,
    initialScrollY: Int? = null,
    onScrollYChanged: ((Int) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onContentHeightChanged: ((Int, Int, Boolean) -> Unit)? = null,
    onContentLoadStarted: (() -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    scrollController: ArticleWebViewScrollController? = null,
    showScrollbar: Boolean = true,
    configure: ReaderWebView.() -> Unit,
    load: ReaderWebView.() -> Unit,
    interceptRequest: (WebView?, WebResourceRequest?) -> WebResourceResponse? = { _, _ -> null },
    handleUrlLoading: (WebView?, WebResourceRequest?) -> Boolean = { _, _ -> false },
    onUpdate: (ReaderWebView) -> Unit = {},
    markContentLayoutReady: Boolean = true
) {
    val currentConfigure = rememberUpdatedState(configure)
    val currentLoad = rememberUpdatedState(load)
    val currentInterceptRequest = rememberUpdatedState(interceptRequest)
    val currentUrlLoading = rememberUpdatedState(handleUrlLoading)
    val currentUpdate = rememberUpdatedState(onUpdate)
    val currentInitialScrollY = rememberUpdatedState(initialScrollY)
    val currentOnScrollYChanged = rememberUpdatedState(onScrollYChanged)
    val currentOnScrollProgress = rememberUpdatedState(onScrollProgress)
    val currentOnContentHeightChanged = rememberUpdatedState(onContentHeightChanged)
    val currentOnContentLoadStarted = rememberUpdatedState(onContentLoadStarted)
    val currentOnImageLongClick = rememberUpdatedState(onImageLongClick)
    val loadedPageKey = remember { mutableStateOf<Any?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var scrollProgress by rememberSaveable(entryId) { mutableFloatStateOf(0f) }
    var scrollbarThumbFraction by rememberSaveable(entryId) { mutableFloatStateOf(1f) }
    var isScrollable by rememberSaveable(entryId) { mutableStateOf(false) }
    var scrollY by rememberSaveable(entryId) { mutableIntStateOf(0) }
    var renderProcessError by remember(pageKey) { mutableStateOf(false) }
    var renderAttempt by remember(pageKey) { mutableIntStateOf(0) }
    var contentHeightPx by remember(pageKey, renderAttempt) { mutableIntStateOf(0) }
    var viewportHeightPx by remember(pageKey, renderAttempt) { mutableIntStateOf(0) }
    var contentHeightSettled by remember(pageKey, renderAttempt) { mutableStateOf(false) }

    val positionReady = readingPositionLoaded &&
        contentHeightPx > 0 &&
        viewportHeightPx > 0 &&
        contentHeightSettled &&
        loadedWebView.value != null
    ArticleReadingPositionTracker(
        entryId = entryId,
        contentKey = contentKey,
        effectKey = listOf(contentKey, contentHeightPx, viewportHeightPx, contentHeightSettled),
        readingPositionLoaded = readingPositionLoaded,
        savedReadingProgress = savedReadingProgress,
        positionReady = positionReady,
        currentProgress = {
            val maxScrollPx = readerWebViewMaxScrollPx(contentHeightPx, viewportHeightPx)
            articleScrollProgress(scrollY, maxScrollPx) to (maxScrollPx > 0)
        },
        restorePosition = { progress ->
            val maxScrollPx = readerWebViewMaxScrollPx(contentHeightPx, viewportHeightPx)
            if (maxScrollPx > 0) {
                val restoreScrollY = articleScrollOffset(progress, maxScrollPx)
                scrollY = restoreScrollY
                loadedWebView.value?.let { readerView ->
                    readerView.pageLoadRestoreScrollY = restoreScrollY
                    readerView.postIfActive { readerView.scrollTo(0, restoreScrollY) }
                }
            }
        },
        onReadingProgressChanged = onReadingProgressChanged,
        onReadingCompleted = onReadingCompleted
    )

    if (renderProcessError) {
        ReaderWebViewError(
            modifier = modifier,
            onRetry = {
                renderProcessError = false
                renderAttempt++
            }
        )
    } else {
        key(renderAttempt) {
            Box(modifier = modifier) {
                AndroidView(
                    factory = { context ->
                        ReaderWebView(context).apply {
                            val progressReporter = ReaderWebViewScrollProgressReporter {
                                    progress, scrollable, thumbFraction ->
                                scrollProgress = progress
                                isScrollable = scrollable
                                scrollbarThumbFraction = thumbFraction
                                currentOnScrollProgress.value?.invoke(progress)
                            }
                            fun updateScrollProgress(readerView: ReaderWebView) {
                                val density = readerView.resources.displayMetrics.density
                                contentHeightPx = (readerView.contentHeight * density).roundToInt()
                                viewportHeightPx = readerView.height
                                scrollY = readerView.scrollY
                                currentOnScrollYChanged.value?.invoke(readerView.scrollY)
                                progressReporter.update(readerView)
                            }

                            currentConfigure.value(this)
                            scrollController?.attach(this)
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? = currentInterceptRequest.value(view, request)

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = currentUrlLoading.value(view, request)

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail
                                ): Boolean {
                                    loadedWebView.value = null
                                    (view as? ReaderWebView)?.destroyAfterRenderProcessGone()
                                    scrollProgress = 0f
                                    scrollbarThumbFraction = 1f
                                    isScrollable = false
                                    contentHeightPx = 0
                                    viewportHeightPx = 0
                                    scrollY = 0
                                    contentHeightSettled = false
                                    renderProcessError = true
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val readerView = view as? ReaderWebView ?: return
                                    readerView.contentLayoutReady = markContentLayoutReady
                                    contentHeightSettled = false
                                    readerView.postIfActive {
                                        readerView.scrollTo(0, scrollY)
                                        updateScrollProgress(readerView)
                                        readerView.scheduleContentHeightUpdatesWithSettled { height, settled ->
                                            contentHeightSettled = settled
                                            updateScrollProgress(readerView)
                                            currentOnContentHeightChanged.value?.invoke(
                                                height,
                                                readerView.loadedContentTopInsetPx,
                                                settled
                                            )
                                        }
                                    }
                                }
                            }
                            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                (view as? ReaderWebView)?.let(::updateScrollProgress)
                            }
                            setOnScrollChangeListener { _, _, _, _, _ ->
                                updateScrollProgress(this)
                            }
                            setOnLongClickListener { view: View ->
                                val result = (view as? WebView)?.hitTestResult ?: return@setOnLongClickListener false
                                if (
                                    result.type != WebView.HitTestResult.IMAGE_TYPE &&
                                    result.type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                ) {
                                    return@setOnLongClickListener false
                                }
                                result.extra
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { currentOnImageLongClick.value?.invoke(it) }
                                result.extra?.isNotBlank() == true
                            }
                        }
                    },
                    update = { webView ->
                        currentUpdate.value(webView)
                        if (loadedWebView.value !== webView || loadedPageKey.value != pageKey) {
                            loadedWebView.value = webView
                            loadedPageKey.value = pageKey
                            contentHeightSettled = false
                            webView.contentLayoutReady = false
                            webView.cancelContentHeightUpdates()
                            val restoreY = currentInitialScrollY.value ?: scrollY
                            webView.pageLoadRestoreScrollY = restoreY
                            currentOnContentLoadStarted.value?.invoke()
                            currentLoad.value(webView)
                            webView.postIfActive { webView.scrollTo(0, restoreY) }
                        }
                    },
                    onRelease = { webView ->
                        scrollController?.detach(webView)
                        webView.releaseResources()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (showScrollbar) {
                    VerticalScrollbar(
                        metrics = if (isScrollable) {
                            VerticalScrollbarMetrics(
                                thumbFraction = scrollbarThumbFraction,
                                positionFraction = scrollProgress
                            )
                        } else {
                            null
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                    )
                }
            }
        }
    }
}
