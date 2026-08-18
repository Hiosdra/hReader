package com.hiosdra.hreader.presentation.article

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun RemoteArticleWebView(
    entryId: Long,
    url: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val loadedUrl = remember { mutableStateOf<String?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var savedScrollY by rememberSaveable(entryId) { mutableIntStateOf(0) }
    var renderProcessError by remember(entryId, url) { mutableStateOf(false) }
    var renderAttempt by remember(entryId, url) { mutableIntStateOf(0) }

    if (renderProcessError) {
        ReaderWebViewError(
            modifier = modifier,
            onRetry = {
                renderProcessError = false
                renderAttempt += 1
            }
        )
    } else {
        key(renderAttempt) {
            AndroidView(
                factory = { context ->
                    ReaderWebView(context).apply {
                        protectVerticalScrollFromPager = true
                        settings.hardenArticleContent()
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail
                            ): Boolean {
                                loadedWebView.value = null
                                (view as? ReaderWebView)?.destroyAfterRenderProcessGone()
                                renderProcessError = true
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val readerView = view as? ReaderWebView ?: return
                                readerView.postIfActive {
                                    readerView.scrollTo(0, savedScrollY)
                                }
                            }
                        }
                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            savedScrollY = scrollY
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
                modifier = modifier
            )
        }
    }
}
