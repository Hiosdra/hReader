package com.hiosdra.hreader.presentation.article

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import com.hiosdra.hreader.core.domain.model.OfflinePage
import com.hiosdra.hreader.core.domain.service.cleanUrl
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

@Composable
fun OfflinePageWebView(
    page: OfflinePage,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    readingPositionLoaded: Boolean,
    savedReadingProgress: Float?,
    onReadingProgressChanged: (Long, Float) -> Unit,
    onReadingCompleted: (Long) -> Unit
) {
    val currentPage = rememberUpdatedState(page)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val pageKey = page.entryId to (page.html to page.baseUrl)

    ArticleReadingWebView(
        entryId = page.entryId,
        pageKey = pageKey,
        contentKey = pageKey.hashCode(),
        modifier = modifier,
        readingPositionLoaded = readingPositionLoaded,
        savedReadingProgress = savedReadingProgress,
        onReadingProgressChanged = onReadingProgressChanged,
        onReadingCompleted = onReadingCompleted,
        configure = {
            protectVerticalScrollFromPager = true
            settings.hardenArticleContent()
            settings.blockNetworkLoads = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        },
        interceptRequest = { _, request: WebResourceRequest? ->
            serveOfflineAsset(currentPage.value, request?.url)
        },
        handleUrlLoading = { _: WebView?, request: WebResourceRequest? ->
            val requestUri = request?.url ?: return@ArticleReadingWebView false
            val url = requestUri.toString()
            if (isSameWebOrigin(url, currentPage.value.baseUrl)) {
                false
            } else {
                cleanUrl(url)
                    .takeIf(::isAllowedArticleLink)
                    ?.let { currentOnLinkClick.value?.invoke(it) }
                true
            }
        },
        load = {
            val current = currentPage.value
            loadDataWithBaseURL(current.baseUrl, current.html, "text/html", "UTF-8", null)
        },
        markContentLayoutReady = false
    )
}

private fun serveOfflineAsset(page: OfflinePage, uri: Uri?): WebResourceResponse? {
    uri ?: return null
    val baseUri = page.baseUrl.toUri()
    val basePath = baseUri.path?.trimEnd('/') ?: return null
    if (!isSameWebOrigin(uri.toString(), page.baseUrl)) return null
    val assetsPrefix = "$basePath/assets/"
    val relativePath = uri.path?.removePrefix(assetsPrefix)
        ?.takeIf { uri.path?.startsWith(assetsPrefix) == true && it.isNotBlank() }
        ?: return null
    if (relativePath.split('/').any { it == ".." || it.isBlank() }) return null

    val assetsDirectory = runCatching { File(page.resourceDirectory, "assets").canonicalFile }.getOrNull()
        ?: return null
    val file = runCatching { File(assetsDirectory, relativePath).canonicalFile }.getOrNull()
        ?: return null
    if (!isFileWithinDirectory(file.path, assetsDirectory)) return null

    return runCatching {
        WebResourceResponse(offlineMimeType(file.name), null, FileInputStream(file))
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
