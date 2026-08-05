package com.hiosdra.hreader.ui.article

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.model.OfflinePage
import com.hiosdra.hreader.util.BionicReadingProcessor
import com.hiosdra.hreader.util.cleanUrl
import com.hiosdra.hreader.util.sanitizeArticleHtml
import org.koin.compose.koinInject
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CONTENT_HEIGHT_UPDATE_ATTEMPTS = 12
private const val CONTENT_HEIGHT_UPDATE_DELAY_MS = 100L

internal enum class ReaderGestureDirection {
    Horizontal,
    Vertical
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
fun ArticleWebView(
    articleContent: String,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    allowNetworkLoads: Boolean = true,
    localImagePaths: Map<String, String> = emptyMap(),
    textScale: Float = 1f,
    scrollEnabled: Boolean = true,
    onContentHeightChanged: ((Int) -> Unit)? = null,
    restoreScrollY: Int = 0,
    onScrollYChanged: ((Int) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    preferencesManager: PreferencesManager = koinInject()
) {
    val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val codeBg = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.toArgb())
    val ruleColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outlineVariant.toArgb())

    // Read by the request interceptor below, which outlives any single recomposition: the client is
    // built once with the WebView, while the downloaded images arrive with the article body.
    val currentLocalImagePaths = rememberUpdatedState(localImagePaths)
    val currentScrollEnabled = rememberUpdatedState(scrollEnabled)
    val currentOnContentHeightChanged = rememberUpdatedState(onContentHeightChanged)
    val currentRestoreScrollY = rememberUpdatedState(restoreScrollY)
    val currentOnScrollYChanged = rememberUpdatedState(onScrollYChanged)
    val currentOnScrollProgress = rememberUpdatedState(onScrollProgress)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val currentOnImageLongClick = rememberUpdatedState(onImageLongClick)

    // Watched rather than read once, so turning the setting on redraws the article already open.
    val bionicReadingEnabled by preferencesManager.observeBionicReadingEnabled()
        .collectAsState(initial = preferencesManager.getBionicReadingEnabled())

    val processedContent = remember(articleContent, baseUrl, bionicReadingEnabled) {
        val safeContent = sanitizeArticleHtml(articleContent, baseUrl)
        if (bionicReadingEnabled) {
            BionicReadingProcessor.processTextToBionic(safeContent)
        } else {
            safeContent
        }
    }

    val htmlData = remember(processedContent, textColorHex, linkColorHex, codeBg, ruleColor) {
        articleHtml(processedContent, textColorHex, linkColorHex, codeBg, ruleColor)
    }

    /**
     * What was last handed to the WebView. The update block runs on every recomposition — a read
     * state changing or images arriving — and reloading there threw away the reader's position in
     * the article each time.
     */
    val loadedHtml = remember { mutableStateOf<String?>(null) }
    val loadedBaseUrl = remember { mutableStateOf<String?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var renderProcessError by remember(articleContent, baseUrl) { mutableStateOf(false) }
    var renderAttempt by remember(articleContent, baseUrl) { mutableIntStateOf(0) }

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
                        var lastProgress = -1f
                        var lastScrollY = -1
                        var lastProgressAt = 0L
                        fun updateScrollProgress(wv: ReaderWebView) {
                            if (wv.isReleased) return
                            val contentHeightPx = wv.contentHeight * wv.resources.displayMetrics.density
                            val viewHeight = wv.height.toFloat()
                            val denom = (contentHeightPx - viewHeight).coerceAtLeast(1f)
                            val progress = (wv.scrollY / denom).coerceIn(0f, 1f)
                            val now = SystemClock.elapsedRealtime()
                            if (abs(progress - lastProgress) >= 0.01f || now - lastProgressAt >= 500L) {
                                lastProgress = progress
                                lastProgressAt = now
                                currentOnScrollProgress.value?.invoke(progress)
                            }
                            if (lastScrollY < 0 || abs(wv.scrollY - lastScrollY) >= 8 || progress == 0f || progress == 1f) {
                                lastScrollY = wv.scrollY
                                currentOnScrollYChanged.value?.invoke(wv.scrollY)
                            }
                        }

                        allowScroll = currentScrollEnabled.value
                        settings.javaScriptEnabled = false
                        settings.defaultFontSize = 16
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = allowScroll
                        isHorizontalScrollBarEnabled = allowScroll
                        overScrollMode = if (allowScroll) {
                            View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        } else {
                            View.OVER_SCROLL_NEVER
                        }
                        setOnTouchListener { view, _ ->
                            if (!currentScrollEnabled.value) {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
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

                            /**
                             * Serves an image from the copy prefetching downloaded instead of fetching it
                             * again. Interception rather than rewriting the `src` to a `file://` address:
                             * the document is loaded under the article's own https origin, which is not
                             * allowed to pull in local files.
                             */
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null
                                val localPath = currentLocalImagePaths.value[url] ?: return null
                                val file = File(localPath)
                                if (!file.exists()) return null
                                return runCatching {
                                    WebResourceResponse(
                                        URLConnection.guessContentTypeFromName(file.name) ?: "image/*",
                                        null,
                                        FileInputStream(file)
                                    )
                                }.getOrNull()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                currentOnLinkClick.value?.invoke(cleanUrl(url))
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val readerView = view as? ReaderWebView ?: return
                                readerView.postIfActive {
                                    readerView.scrollTo(0, currentRestoreScrollY.value)
                                    readerView.scheduleContentHeightUpdates { height ->
                                        currentOnContentHeightChanged.value?.invoke(height)
                                    }
                                    updateScrollProgress(readerView)
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                val readerView = view as? ReaderWebView ?: return
                                if (newProgress == 100) {
                                    readerView.scheduleContentHeightUpdates { height ->
                                        currentOnContentHeightChanged.value?.invoke(height)
                                    }
                                }
                            }
                        }
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            val readerView = view as? ReaderWebView ?: return@addOnLayoutChangeListener
                            readerView.scheduleContentHeightUpdates { height ->
                                currentOnContentHeightChanged.value?.invoke(height)
                            }
                        }
                        setOnScrollChangeListener { v, _, _, _, _ ->
                            if (v is ReaderWebView) updateScrollProgress(v)
                        }
                        setOnLongClickListener { v: View ->
                            val result = (v as? WebView)?.hitTestResult
                            if (result != null) {
                                val type = result.type
                                if (type == WebView.HitTestResult.IMAGE_TYPE ||
                                    type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                ) {
                                    val url = result.extra
                                    if (!url.isNullOrBlank()) {
                                        currentOnImageLongClick.value?.invoke(url)
                                        return@setOnLongClickListener true
                                    }
                                }
                            }
                            false
                        }
                    }
                },
                update = { webView ->
                    // Images the article references have already been rewritten to local files where they
                    // were downloaded. Whatever is left points at the network, and offline every one of
                    // those costs a connect timeout before the page settles.
                    webView.settings.blockNetworkLoads = !allowNetworkLoads
                    val textZoom = (textScale.coerceIn(0.85f, 1.35f) * 100).roundToInt()
                    if (webView.settings.textZoom != textZoom) {
                        webView.settings.textZoom = textZoom
                        webView.scheduleContentHeightUpdates { height ->
                            currentOnContentHeightChanged.value?.invoke(height)
                        }
                    }
                    webView.allowScroll = currentScrollEnabled.value
                    webView.isVerticalScrollBarEnabled = webView.allowScroll
                    webView.isHorizontalScrollBarEnabled = webView.allowScroll
                    webView.overScrollMode = if (webView.allowScroll) {
                        View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    } else {
                        View.OVER_SCROLL_NEVER
                    }

                    if (
                        loadedWebView.value !== webView ||
                        loadedHtml.value != htmlData ||
                        loadedBaseUrl.value != baseUrl
                    ) {
                        loadedWebView.value = webView
                        loadedHtml.value = htmlData
                        loadedBaseUrl.value = baseUrl
                        webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
                        webView.postIfActive { webView.scrollTo(0, currentRestoreScrollY.value) }
                        webView.scheduleContentHeightUpdates { height ->
                            currentOnContentHeightChanged.value?.invoke(height)
                        }
                    }
                },
                onRelease = { webView -> webView.releaseResources() },
                modifier = modifier
            )
        }
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
            Text("Could not display this article.", textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

internal class ReaderWebView(context: Context) : WebView(context) {
    private var released = false
    private var contentHeightUpdateRunnable: Runnable? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pagerGestureDirection: ReaderGestureDirection? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    var allowScroll: Boolean = true
    var protectVerticalScrollFromPager: Boolean = false
        set(value) {
            field = value
            if (!value) {
                parent?.requestDisallowInterceptTouchEvent(false)
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

            override fun run() {
                if (released) return
                val height = (contentHeight * resources.displayMetrics.density).roundToInt()
                if (height > 0 && height != lastHeight) {
                    lastHeight = height
                    onHeightChanged(height)
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
        parent?.requestDisallowInterceptTouchEvent(false)
        pagerGestureDirection = null
        contentHeightUpdateRunnable?.let(::removeCallbacks)
        contentHeightUpdateRunnable = null
        released = true
        setOnTouchListener(null)
        setOnScrollChangeListener(null)
        setOnLongClickListener(null)
        webViewClient = WebViewClient()
        webChromeClient = null
        stopLoading()
        removeAllViews()
        destroy()
    }

    internal fun destroyAfterRenderProcessGone() {
        if (released) return
        released = true
        parent?.requestDisallowInterceptTouchEvent(false)
        (parent as? ViewGroup)?.removeView(this)
        pagerGestureDirection = null
        contentHeightUpdateRunnable?.let(::removeCallbacks)
        contentHeightUpdateRunnable = null
        destroy()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (protectVerticalScrollFromPager) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.x
                    initialTouchY = event.y
                    pagerGestureDirection = null
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pagerGestureDirection == null) {
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
                    pagerGestureDirection = null
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        parent?.requestDisallowInterceptTouchEvent(false)
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

@Composable
fun OfflinePageWebView(
    page: OfflinePage,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val currentPage = androidx.compose.runtime.rememberUpdatedState(page)
    val currentOnLinkClick = androidx.compose.runtime.rememberUpdatedState(onLinkClick)
    val loadedPageKey = remember { mutableStateOf<Triple<Long, String, String>?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    var renderProcessError by remember(page.entryId, page.html) { mutableStateOf(false) }
    var renderAttempt by remember(page.entryId, page.html) { mutableIntStateOf(0) }

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
                        settings.javaScriptEnabled = false
                        settings.blockNetworkLoads = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
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
                                val pageUri = Uri.parse(currentPage.value.baseUrl)
                                if (requestUri.host == pageUri.host) return false
                                currentOnLinkClick.value?.invoke(cleanUrl(url))
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
    val baseUri = Uri.parse(page.baseUrl)
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

private fun articleHtml(
    body: String,
    textColorHex: String,
    linkColorHex: String,
    codeBg: String,
    ruleColor: String
): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root { --text:$textColorHex; --link:$linkColorHex; --code:$codeBg; --rule:$ruleColor; }
            body { font-family: system-ui,-apple-system,Roboto,sans-serif; font-size:16px; line-height:1.6; margin:0; padding:0 0 32px 0; color:var(--text); background:transparent; }
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
