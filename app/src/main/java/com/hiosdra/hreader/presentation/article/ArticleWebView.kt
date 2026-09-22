package com.hiosdra.hreader.presentation.article

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.domain.service.cleanUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun ArticleWebView(
    articleContent: String,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    allowNetworkLoads: Boolean = true,
    localImagePaths: Map<String, String> = emptyMap(),
    textScale: Float = 1f,
    scrollEnabled: Boolean = true,
    contentTopInsetPx: Int = 0,
    scrollController: ArticleWebViewScrollController? = null,
    onContentHeightChanged: ((Int, Int, Boolean) -> Unit)? = null,
    onContentLoadStarted: (() -> Unit)? = null,
    restoreScrollY: Int = 0,
    onScrollYChanged: ((Int) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    readerPreferences: ReaderPreferences,
    remoteResourcePolicy: RemoteResourcePolicy
) {
    val colors = ArticleHtmlColors(
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        link = MaterialTheme.colorScheme.primary.toArgb(),
        code = MaterialTheme.colorScheme.surfaceVariant.toArgb(),
        rule = MaterialTheme.colorScheme.outlineVariant.toArgb()
    )
    val bionicReadingEnabled by readerPreferences.observeBionicReadingEnabled()
        .collectAsStateWithLifecycle(initialValue = readerPreferences.getBionicReadingEnabled())
    var processedContent by remember(articleContent) { mutableStateOf(articleContent) }
    LaunchedEffect(articleContent, bionicReadingEnabled) {
        processedContent = if (bionicReadingEnabled) {
            withContext(Dispatchers.Default) {
                BionicReadingProcessor.processTextToBionicCached(articleContent)
            }
        } else {
            articleContent
        }
    }

    val density = LocalDensity.current.density
    val htmlData = remember(processedContent, colors, contentTopInsetPx, density) {
        articleHtml(
            body = processedContent,
            textColorHex = colors.textHex,
            linkColorHex = colors.linkHex,
            codeBg = colors.codeHex,
            ruleColor = colors.ruleHex,
            contentTopInsetCssPx = contentTopInsetPx.coerceAtLeast(0) / density
        )
    }
    val currentLocalImagePaths = rememberUpdatedState(localImagePaths)
    val currentRemoteResourcePolicy = rememberUpdatedState(remoteResourcePolicy)
    val currentAllowNetworkLoads = rememberUpdatedState(allowNetworkLoads)
    val currentBaseUrl = rememberUpdatedState(baseUrl)
    val currentScrollEnabled = rememberUpdatedState(scrollEnabled)
    val currentRestoreScrollY = rememberUpdatedState(restoreScrollY)
    val currentTextScale = rememberUpdatedState(textScale)
    val resourceScope = rememberCoroutineScope()
    var loadedLocalImagePathsKey by remember { mutableStateOf<Int?>(null) }

    ArticleReadingWebView(
        entryId = baseUrl?.hashCode()?.toLong() ?: htmlData.hashCode().toLong(),
        pageKey = htmlData,
        contentKey = htmlData.hashCode(),
        modifier = modifier,
        readingPositionLoaded = false,
        savedReadingProgress = null,
        onReadingProgressChanged = { _, _ -> },
        onReadingCompleted = {},
        initialScrollY = restoreScrollY,
        onScrollYChanged = onScrollYChanged,
        onScrollProgress = onScrollProgress,
        onContentHeightChanged = onContentHeightChanged,
        onContentLoadStarted = onContentLoadStarted,
        onImageLongClick = onImageLongClick,
        scrollController = scrollController,
        showScrollbar = false,
        configure = {
            allowScroll = scrollEnabled
            protectVerticalScrollFromPager = scrollEnabled
            settings.hardenArticleContent()
            settings.defaultFontSize = 16
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = if (scrollEnabled) {
                android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            } else {
                android.view.View.OVER_SCROLL_NEVER
            }
            loadedContentTopInsetPx = contentTopInsetPx
        },
        interceptRequest = { view, request ->
            val url = request?.url?.toString() ?: return@ArticleReadingWebView null
            val localPath = currentLocalImagePaths.value[url]
            if (!localPath.isNullOrBlank()) {
                serveLocalArticleImage(localPath, view?.context?.filesDir)
                    ?.let { return@ArticleReadingWebView it }
            }
            val documentUrl = currentBaseUrl.value
            if (
                !isHttpResource(url) ||
                (
                    currentAllowNetworkLoads.value &&
                        documentUrl != null &&
                        isSameWebOrigin(url, documentUrl) &&
                        currentRemoteResourcePolicy.value.allows(url)
                    )
            ) {
                null
            } else {
                blockedResourceResponse()
            }
        },
        handleUrlLoading = { _, request ->
            val url = request?.url?.toString() ?: return@ArticleReadingWebView false
            val cleanedUrl = cleanUrl(url)
            if (!isAllowedArticleLink(cleanedUrl)) return@ArticleReadingWebView true
            if (!currentAllowNetworkLoads.value) {
                onLinkClick?.invoke(cleanedUrl)
                return@ArticleReadingWebView true
            }
            val policy = currentRemoteResourcePolicy.value
            resourceScope.launch(Dispatchers.IO) {
                if (policy.allows(cleanedUrl)) {
                    withContext(Dispatchers.Main.immediate) {
                        onLinkClick?.invoke(cleanedUrl)
                    }
                }
            }
            true
        },
        load = {
            loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
        },
        onUpdate = { webView ->
            webView.settings.blockNetworkLoads = !currentAllowNetworkLoads.value
            webView.allowScroll = currentScrollEnabled.value
            webView.protectVerticalScrollFromPager = currentScrollEnabled.value
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.overScrollMode = if (currentScrollEnabled.value) {
                android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            } else {
                android.view.View.OVER_SCROLL_NEVER
            }
            webView.loadedContentTopInsetPx = contentTopInsetPx

            val textZoom = (currentTextScale.value.coerceIn(0.85f, 1.35f) * 100).roundToInt()
            val textZoomChanged = webView.settings.textZoom != textZoom
            if (textZoomChanged) webView.settings.textZoom = textZoom

            val localImagePathsKey = currentLocalImagePaths.value.hashCode()
            val localImagePathsChanged = loadedLocalImagePathsKey != null &&
                loadedLocalImagePathsKey != localImagePathsKey
            loadedLocalImagePathsKey = localImagePathsKey
            if ((textZoomChanged || localImagePathsChanged) && webView.contentLayoutReady) {
                webView.restartContentHeightUpdatesWithSettled { height, settled ->
                    onContentHeightChanged?.invoke(
                        height,
                        webView.loadedContentTopInsetPx,
                        settled
                    )
                }
            }
            val currentRestore = currentRestoreScrollY.value
            if (webView.pageLoadRestoreScrollY != currentRestore) {
                webView.pageLoadRestoreScrollY = currentRestore
                webView.postIfActive { webView.scrollTo(0, currentRestore) }
            }
        }
    )
}

private data class ArticleHtmlColors(
    val text: Int,
    val link: Int,
    val code: Int,
    val rule: Int
) {
    val textHex: String get() = text.toHexColor()
    val linkHex: String get() = link.toHexColor()
    val codeHex: String get() = code.toHexColor()
    val ruleHex: String get() = rule.toHexColor()
}

private fun Int.toHexColor(): String = String.format("#%06X", 0xFFFFFF and this)

private fun articleHtml(
    body: String,
    textColorHex: String,
    linkColorHex: String,
    codeBg: String,
    ruleColor: String,
    contentTopInsetCssPx: Float
): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root { --text:$textColorHex; --link:$linkColorHex; --code:$codeBg; --rule:$ruleColor; }
            body { font-family: system-ui,-apple-system,Roboto,sans-serif; font-size:16px; line-height:1.6; margin:0; padding:${contentTopInsetCssPx}px 0 32px 0; color:var(--text); background:transparent; }
            h1,h2,h3 { line-height:1.25; margin:1.4em 0 .6em; }
            h1 { font-size:1.5em; }
            h2 { font-size:1.3em; }
            h3 { font-size:1.15em; }
            p, li { margin:0 0 1em; }
            img, video, figure { max-width:100%; height:auto; border-radius:12px; display:block; margin:16px auto; }
            pre { overflow:auto; padding:12px; background:var(--code); border-radius:10px; font-size:.85em; }
            code { background:var(--code); padding:2px 5px; border-radius:6px; }
            blockquote { margin:16px 0; padding:4px 16px; border-left:4px solid var(--link); opacity:.9; }
            a { color:var(--link); text-decoration:underline; }
            table { border-collapse:collapse; width:100%; margin:16px 0; }
            th,td { border:1px solid var(--rule); padding:6px 8px; text-align:left; }
            ul,ol { padding-left:1.25em; }
            hr { border:none; height:1px; background:var(--rule); margin:32px 0; }
        </style>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()
