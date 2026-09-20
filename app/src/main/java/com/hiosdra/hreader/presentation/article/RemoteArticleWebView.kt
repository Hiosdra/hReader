package com.hiosdra.hreader.presentation.article

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    remoteResourcePolicy: RemoteResourcePolicy,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit
) {
    var policyAttempt by androidx.compose.runtime.remember(entryId, url) {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    var resourceAllowed by remember(url, policyAttempt) { mutableStateOf<Boolean?>(null) }
    val resourceScope = rememberCoroutineScope()
    val currentIsOnline = rememberUpdatedState(isOnline)
    val currentPolicy = rememberUpdatedState(remoteResourcePolicy)

    LaunchedEffect(url, policyAttempt) {
        resourceAllowed = withContext(Dispatchers.IO) { remoteResourcePolicy.allows(url) }
    }

    when (resourceAllowed) {
        null -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        false -> ReaderWebViewError(
            modifier = modifier,
            onRetry = {
                resourceAllowed = null
                policyAttempt++
            }
        )
        true -> ArticleReadingWebView(
            entryId = entryId,
            pageKey = url,
            contentKey = 31 * entryId.hashCode() + url.hashCode(),
            modifier = modifier,
            readingPositionLoaded = readingPositionLoaded,
            savedReadingProgress = savedReadingProgress,
            onReadingProgressChanged = onReadingProgressChanged,
            onReadingCompleted = onReadingCompleted,
            configure = {
                protectVerticalScrollFromPager = true
                settings.hardenArticleContent()
            },
            interceptRequest = { _, request: WebResourceRequest? ->
                val resourceUrl = request?.url?.toString() ?: return@ArticleReadingWebView null
                if (!isHttpResource(resourceUrl) || currentPolicy.value.allows(resourceUrl)) {
                    null
                } else {
                    blockedResourceResponse()
                }
            },
            handleUrlLoading = { view: WebView?, request: WebResourceRequest? ->
                val navigationUrl = request?.url?.toString() ?: return@ArticleReadingWebView false
                if (!isAllowedArticleLink(navigationUrl) || !currentIsOnline.value) return@ArticleReadingWebView true
                val targetView = view ?: return@ArticleReadingWebView true
                resourceScope.launch(Dispatchers.IO) {
                    if (!currentPolicy.value.allows(navigationUrl)) return@launch
                    withContext(Dispatchers.Main.immediate) { targetView.loadUrl(navigationUrl) }
                }
                true
            },
            load = { loadUrl(url) },
            onUpdate = { webView -> webView.settings.blockNetworkLoads = !isOnline }
        )
    }
}
