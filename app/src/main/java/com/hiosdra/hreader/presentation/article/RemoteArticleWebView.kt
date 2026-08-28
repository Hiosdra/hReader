package com.hiosdra.hreader.presentation.article

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RemoteArticleWebView(
    entryId: Long,
    url: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    remoteResourcePolicy: RemoteResourcePolicy
) {
    val loadedUrl = remember { mutableStateOf<String?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var savedScrollY by rememberSaveable(entryId) { mutableIntStateOf(0) }
    var scrollProgress by rememberSaveable(entryId) { mutableFloatStateOf(0f) }
    var scrollbarThumbFraction by rememberSaveable(entryId) { mutableFloatStateOf(1f) }
    var isScrollable by rememberSaveable(entryId) { mutableStateOf(false) }
    var renderProcessError by remember(entryId, url) { mutableStateOf(false) }
    var renderAttempt by remember(entryId, url) { mutableIntStateOf(0) }
    var resourceAllowed by remember(url, renderAttempt) { mutableStateOf<Boolean?>(null) }
    val resourceScope = rememberCoroutineScope()
    val currentIsOnline = rememberUpdatedState(isOnline)
    LaunchedEffect(url, renderAttempt) {
        resourceAllowed = withContext(Dispatchers.IO) { remoteResourcePolicy.allows(url) }
    }

    if (resourceAllowed == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    } else if (resourceAllowed == false || renderProcessError) {
        ReaderWebViewError(
            modifier = modifier,
            onRetry = {
                renderProcessError = false
                renderAttempt += 1
            }
        )
    } else {
        key(renderAttempt) {
            Box(modifier = modifier) {
                AndroidView(
                    factory = { context ->
                        ReaderWebView(context).apply {
                            val progressReporter = ReaderWebViewScrollProgressReporter { progress, scrollable, thumbFraction ->
                                scrollProgress = progress
                                isScrollable = scrollable
                                scrollbarThumbFraction = thumbFraction
                            }
                            fun updateScrollProgress(readerView: ReaderWebView) {
                                progressReporter.update(readerView)
                            }
                            protectVerticalScrollFromPager = true
                            settings.hardenArticleContent()
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val resourceUrl = request?.url?.toString() ?: return null
                                    if (!isHttpResource(resourceUrl) || remoteResourcePolicy.allows(resourceUrl)) {
                                        return null
                                    }
                                    return blockedResourceResponse()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val navigationUrl = request?.url?.toString() ?: return false
                                    if (!isAllowedArticleLink(navigationUrl) || !currentIsOnline.value) return true
                                    val targetView = view ?: return true
                                    resourceScope.launch(Dispatchers.IO) {
                                        if (!remoteResourcePolicy.allows(navigationUrl)) return@launch
                                        withContext(Dispatchers.Main.immediate) {
                                            targetView.loadUrl(navigationUrl)
                                        }
                                    }
                                    return true
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail
                                ): Boolean {
                                    loadedWebView.value = null
                                    (view as? ReaderWebView)?.destroyAfterRenderProcessGone()
                                    scrollProgress = 0f
                                    scrollbarThumbFraction = 1f
                                    isScrollable = false
                                    renderProcessError = true
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val readerView = view as? ReaderWebView ?: return
                                    readerView.postIfActive {
                                        readerView.scrollTo(0, savedScrollY)
                                        updateScrollProgress(readerView)
                                        readerView.scheduleContentHeightUpdates {
                                            updateScrollProgress(readerView)
                                        }
                                    }
                                }
                            }
                            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                savedScrollY = scrollY
                                updateScrollProgress(this)
                            }
                            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                (view as? ReaderWebView)?.let(::updateScrollProgress)
                            }
                        }
                    },
                    update = { webView ->
                        webView.settings.blockNetworkLoads = !isOnline
                        if (loadedWebView.value !== webView || loadedUrl.value != url) {
                            loadedWebView.value = webView
                            loadedUrl.value = url
                            webView.loadUrl(url)
                            webView.postIfActive { webView.scrollTo(0, savedScrollY) }
                        }
                    },
                    onRelease = { webView -> webView.releaseResources() },
                    modifier = Modifier.fillMaxSize()
                )
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
