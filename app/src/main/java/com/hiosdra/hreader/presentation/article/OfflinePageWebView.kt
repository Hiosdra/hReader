package com.hiosdra.hreader.presentation.article

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.core.domain.service.cleanUrl
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

@Composable
fun OfflinePageWebView(
    page: OfflinePage,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val currentPage = rememberUpdatedState(page)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val loadedPageKey = remember { mutableStateOf<Triple<Long, String, String>?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var renderProcessError by remember(page.entryId, page.html, page.baseUrl) { mutableStateOf(false) }
    var renderAttempt by remember(page.entryId, page.html, page.baseUrl) { mutableIntStateOf(0) }

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
                        settings.blockNetworkLoads = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
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

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? = serveOfflineAsset(currentPage.value, request?.url)

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val requestUri = request?.url ?: return false
                                val url = requestUri.toString()
                                val pageUri = currentPage.value.baseUrl.toUri()
                                if (
                                    requestUri.scheme.equals(pageUri.scheme, ignoreCase = true) &&
                                    requestUri.host == pageUri.host
                                ) return false
                                val cleanedUrl = cleanUrl(url)
                                if (!isAllowedArticleLink(cleanedUrl)) return true
                                currentOnLinkClick.value?.invoke(cleanedUrl)
                                return currentOnLinkClick.value != null
                            }
                        }
                    }
                },
                update = { webView ->
                    val key = Triple(page.entryId, page.html, page.baseUrl)
                    if (loadedWebView.value !== webView || loadedPageKey.value != key) {
                        loadedWebView.value = webView
                        loadedPageKey.value = key
                        webView.loadDataWithBaseURL(page.baseUrl, page.html, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { webView -> webView.releaseResources() },
                modifier = modifier
            )
        }
    }
}

private fun serveOfflineAsset(page: OfflinePage, uri: Uri?): WebResourceResponse? {
    uri ?: return null
    val baseUri = page.baseUrl.toUri()
    val basePath = baseUri.path?.trimEnd('/') ?: return null
    if (!uri.scheme.equals(baseUri.scheme, ignoreCase = true) || uri.host != baseUri.host) return null
    val assetsPrefix = "$basePath/assets/"
    val relativePath = uri.path?.removePrefix(assetsPrefix)
        ?.takeIf { uri.path?.startsWith(assetsPrefix) == true && it.isNotBlank() }
        ?: return null
    if (relativePath.split('/').any { it == ".." || it.isBlank() }) return null

    val assetsDirectory = runCatching { File(page.resourceDirectory, "assets").canonicalFile }.getOrNull()
        ?: return null
    val file = runCatching { File(assetsDirectory, relativePath).canonicalFile }.getOrNull()
        ?: return null
    val assetsPath = assetsDirectory.path + File.separator
    if (!file.path.startsWith(assetsPath) || !file.isFile) return null

    return runCatching {
        WebResourceResponse(
            offlineMimeType(file.name),
            null,
            FileInputStream(file)
        )
    }.getOrNull()
}

private fun offlineMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "css" -> "text/css"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
}
