package com.hiosdra.hreader.presentation.article

import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.tts.TtsTextDocumentFactory
import com.hiosdra.hreader.core.application.tts.TtsTextRange
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
    onContentHeightChanged: ((Int, Int) -> Unit)? = null,
    restoreScrollY: Int = 0,
    onScrollYChanged: ((Int) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    articleTitle: String = "",
    speechInteractionEnabled: Boolean = false,
    speechRange: TtsTextRange? = null,
    onSpeechPosition: ((Int) -> Unit)? = null,
    onReadFromSelection: ((Int) -> Unit)? = null,
    readerPreferences: ReaderPreferences,
    remoteResourcePolicy: RemoteResourcePolicy
) {
    val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val codeBg = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.toArgb())
    val ruleColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outlineVariant.toArgb())
    val speechHighlightHex = String.format(
        "#%06X",
        0xFFFFFF and MaterialTheme.colorScheme.primaryContainer.toArgb()
    )
    val speechHighlightTextHex = String.format(
        "#%06X",
        0xFFFFFF and MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    )

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
    val currentSpeechInteractionEnabled = rememberUpdatedState(speechInteractionEnabled)
    val currentSpeechRange = rememberUpdatedState(speechRange)
    val currentOnSpeechPosition = rememberUpdatedState(onSpeechPosition)
    val currentOnReadFromSelection = rememberUpdatedState(onReadFromSelection)
    val currentRemoteResourcePolicy = rememberUpdatedState(remoteResourcePolicy)
    val currentAllowNetworkLoads = rememberUpdatedState(allowNetworkLoads)
    val resourceScope = rememberCoroutineScope()

    // Watched rather than read once, so turning the setting on redraws the article already open.
    val bionicReadingEnabled by readerPreferences.observeBionicReadingEnabled()
        .collectAsStateWithLifecycle(initialValue = readerPreferences.getBionicReadingEnabled())
    var processedContent by remember(articleContent) { mutableStateOf(articleContent) }
    androidx.compose.runtime.LaunchedEffect(
        articleContent,
        bionicReadingEnabled
    ) {
        val content = articleContent
        processedContent = if (bionicReadingEnabled) {
            withContext(Dispatchers.Default) {
                BionicReadingProcessor.processTextToBionicCached(content)
            }
        } else {
            content
        }
    }

    val speechMarkup = remember(processedContent, articleTitle, speechInteractionEnabled) {
        if (speechInteractionEnabled) {
            TtsTextDocumentFactory.annotateHtml(articleTitle, processedContent)
        } else {
            processedContent
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val contentTopInsetCssPx = contentTopInsetPx.coerceAtLeast(0) / density
    val htmlData = remember(
        speechMarkup,
        textColorHex,
        linkColorHex,
        codeBg,
        ruleColor,
        speechHighlightHex,
        speechHighlightTextHex,
        contentTopInsetCssPx
    ) {
        articleHtml(
            speechMarkup,
            textColorHex,
            linkColorHex,
            codeBg,
            ruleColor,
            speechHighlightHex,
            speechHighlightTextHex,
            contentTopInsetCssPx
        )
    }

    /**
     * What was last handed to the WebView. The update block runs on every recomposition — a read
     * state changing or images arriving — and reloading there threw away the reader's position in
     * the article each time.
     */
    val loadedHtml = remember { mutableStateOf<String?>(null) }
    val loadedBaseUrl = remember { mutableStateOf<String?>(null) }
    val loadedWebView = remember { mutableStateOf<ReaderWebView?>(null) }
    val lastAppliedRestoreScrollY = remember { mutableIntStateOf(Int.MIN_VALUE) }
    var renderProcessError by remember(articleContent, baseUrl) { mutableStateOf(false) }
    var renderAttempt by remember(articleContent, baseUrl) { mutableIntStateOf(0) }

    fun applySpeechHighlight(webView: ReaderWebView) {
        if (!currentSpeechInteractionEnabled.value || !webView.contentLayoutReady) return
        val range = currentSpeechRange.value
        val script = if (range == null) {
            "window.__hreaderTts && window.__hreaderTts.clear();"
        } else {
            "window.__hreaderTts && window.__hreaderTts.highlight(${range.start},${range.endExclusive});"
        }
        webView.evaluateJavascript(script, null)
    }

    @Suppress("DEPRECATION")
    fun requestSpeechPosition(webView: ReaderWebView, x: Float, y: Float) {
        if (!currentSpeechInteractionEnabled.value || !webView.contentLayoutReady || webView.isReleased) return
        when (webView.hitTestResult.type) {
            WebView.HitTestResult.ANCHOR_TYPE,
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> return
        }
        val cssX = x / density
        val cssY = y / density
        webView.evaluateJavascript(
            "window.__hreaderTts && window.__hreaderTts.positionAt($cssX,$cssY);"
        ) { result ->
            result.toJavascriptInt()?.let { currentOnSpeechPosition.value?.invoke(it) }
        }
    }

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
                        scrollController?.attach(this)
                        var lastScrollY = -1
                        val progressReporter = ReaderWebViewScrollProgressReporter { progress, _, _ ->
                            currentOnScrollProgress.value?.invoke(progress)
                        }
                        fun updateScrollProgress(wv: ReaderWebView) {
                            if (wv.isReleased) return
                            val progress = progressReporter.update(wv)
                            if (lastScrollY != wv.scrollY || progress == 0f || progress == 1f) {
                                lastScrollY = wv.scrollY
                                currentOnScrollYChanged.value?.invoke(wv.scrollY)
                            }
                        }
                        allowScroll = currentScrollEnabled.value
                        settings.hardenArticleContent()
                        settings.javaScriptEnabled = currentSpeechInteractionEnabled.value
                        settings.defaultFontSize = 16
                        val readerView = this
                        readerView.onSpeechTap = { x, y -> requestSpeechPosition(readerView, x, y) }
                        readerView.onReadFromSelection = currentOnReadFromSelection.value
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = if (allowScroll) {
                            View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        } else {
                            View.OVER_SCROLL_NEVER
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
                                val localPath = currentLocalImagePaths.value[url]
                                if (!localPath.isNullOrBlank()) {
                                    serveLocalArticleImage(localPath, view?.context?.filesDir)?.let { return it }
                                }
                                if (!isHttpResource(url)) return null
                                if (currentRemoteResourcePolicy.value.allows(url)) return null
                                return blockedResourceResponse()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                val cleanedUrl = cleanUrl(url)
                                if (!isAllowedArticleLink(cleanedUrl)) return true
                                if (!currentAllowNetworkLoads.value) {
                                    currentOnLinkClick.value?.invoke(cleanedUrl)
                                    return true
                                }
                                val policy = currentRemoteResourcePolicy.value
                                resourceScope.launch(Dispatchers.IO) {
                                    if (!policy.allows(cleanedUrl)) return@launch
                                    withContext(Dispatchers.Main.immediate) {
                                        currentOnLinkClick.value?.invoke(cleanedUrl)
                                    }
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val readerView = view as? ReaderWebView ?: return
                                readerView.contentLayoutReady = true
                                readerView.postIfActive {
                                    readerView.scrollTo(0, readerView.pageLoadRestoreScrollY)
                                    readerView.scheduleContentHeightUpdates { height ->
                                        if (readerView.contentLayoutReady) {
                                            currentOnContentHeightChanged.value?.invoke(
                                                height,
                                                readerView.loadedContentTopInsetPx
                                            )
                                        }
                                    }
                                    updateScrollProgress(readerView)
                                    applySpeechHighlight(readerView)
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                val readerView = view as? ReaderWebView ?: return
                                if (newProgress == 100) {
                                    readerView.scheduleContentHeightUpdates { height ->
                                        if (readerView.contentLayoutReady) {
                                            currentOnContentHeightChanged.value?.invoke(
                                                height,
                                                readerView.loadedContentTopInsetPx
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            val readerView = view as? ReaderWebView ?: return@addOnLayoutChangeListener
                            readerView.scheduleContentHeightUpdates { height ->
                                if (readerView.contentLayoutReady) {
                                    currentOnContentHeightChanged.value?.invoke(
                                        height,
                                        readerView.loadedContentTopInsetPx
                                    )
                                }
                            }
                        }
                        setOnScrollChangeListener { v, _, _, _, _ ->
                            if (v is ReaderWebView) updateScrollProgress(v)
                        }
                        setOnLongClickListener { v: View ->
                            val readerView = v as? ReaderWebView
                            val result = (v as? WebView)?.hitTestResult
                            if (result != null) {
                                val type = result.type
                                if (type == WebView.HitTestResult.IMAGE_TYPE ||
                                    type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                ) {
                                    val url = result.extra
                                    if (!url.isNullOrBlank()) {
                                        readerView?.speechSelectionActionEnabled = false
                                        readerView?.suppressNextClick = true
                                        currentOnImageLongClick.value?.invoke(url)
                                        return@setOnLongClickListener true
                                    }
                                }
                            }
                            readerView?.speechSelectionActionEnabled =
                                currentSpeechInteractionEnabled.value
                            readerView?.suppressNextClick = true
                            false
                        }
                    }
                },
                update = { webView ->
                    // Images the article references have already been rewritten to local files where they
                    // were downloaded. Whatever is left points at the network, and offline every one of
                    // those costs a connect timeout before the page settles.
                    webView.settings.blockNetworkLoads = !allowNetworkLoads
                    webView.settings.javaScriptEnabled = currentSpeechInteractionEnabled.value
                    webView.onReadFromSelection = currentOnReadFromSelection.value
                    val textZoom = (textScale.coerceIn(0.85f, 1.35f) * 100).roundToInt()
                    if (webView.settings.textZoom != textZoom) {
                        webView.settings.textZoom = textZoom
                        webView.scheduleContentHeightUpdates { height ->
                            if (webView.contentLayoutReady) {
                                currentOnContentHeightChanged.value?.invoke(
                                    height,
                                    webView.loadedContentTopInsetPx
                                )
                            }
                        }
                    }
                    webView.allowScroll = currentScrollEnabled.value
                    scrollController?.attach(webView)
                    webView.protectVerticalScrollFromPager = webView.allowScroll
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    webView.overScrollMode = if (webView.allowScroll) {
                        View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    } else {
                        View.OVER_SCROLL_NEVER
                    }

                    val restoreScrollY = currentRestoreScrollY.value
                    if (lastAppliedRestoreScrollY.intValue != restoreScrollY) {
                        lastAppliedRestoreScrollY.intValue = restoreScrollY
                        webView.pageLoadRestoreScrollY = restoreScrollY
                        webView.postIfActive { webView.scrollTo(0, restoreScrollY) }
                    }

                    if (
                        loadedWebView.value !== webView ||
                        loadedHtml.value != htmlData ||
                        loadedBaseUrl.value != baseUrl
                    ) {
                        val reloadScrollY = if (loadedWebView.value === webView) {
                            oversizedArticleScrollYAfterHeaderResize(
                                webViewScrollY = webView.scrollY,
                                previousHeaderHeightPx = webView.loadedContentTopInsetPx,
                                newHeaderHeightPx = contentTopInsetPx
                            )
                        } else {
                            currentRestoreScrollY.value
                        }
                        webView.loadedContentTopInsetPx = contentTopInsetPx
                        webView.pageLoadRestoreScrollY = reloadScrollY.coerceAtLeast(0)
                        webView.contentLayoutReady = false
                        webView.cancelContentHeightUpdates()
                        loadedWebView.value = webView
                        loadedHtml.value = htmlData
                        loadedBaseUrl.value = baseUrl
                        webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
                    }
                    applySpeechHighlight(webView)
                },
                onRelease = { webView ->
                    scrollController?.detach(webView)
                    webView.releaseResources()
                },
                modifier = modifier
            )
        }
    }
}

private fun articleHtml(
    body: String,
    textColorHex: String,
    linkColorHex: String,
    codeBg: String,
    ruleColor: String,
    speechHighlightHex: String,
    speechHighlightTextHex: String,
    contentTopInsetCssPx: Float
): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; font-src http: https: data:;">
        <style>
            :root { --text:$textColorHex; --link:$linkColorHex; --code:$codeBg; --rule:$ruleColor; --speech-highlight:$speechHighlightHex; --speech-highlight-text:$speechHighlightTextHex; }
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
            .tts-current { background:var(--speech-highlight); color:var(--speech-highlight-text); border-radius:.55em; box-shadow:0 0 0 .12em var(--speech-highlight); box-decoration-break:clone; -webkit-box-decoration-break:clone; }
        </style>
        <script>
            window.__hreaderTts = (() => {
                const markerSelector = '[data-hreader-tts-start]';

                const markerFor = node => {
                    if (!node) return null;
                    if (node.nodeType === Node.TEXT_NODE) {
                        return node.parentElement ? node.parentElement.closest(markerSelector) : null;
                    }
                    return node.nodeType === Node.ELEMENT_NODE ? node.closest(markerSelector) : null;
                };

                const positionInMarker = (node, offset) => {
                    const marker = markerFor(node);
                    if (!marker) return null;
                    let prefix = '';
                    try {
                        const range = document.createRange();
                        range.selectNodeContents(marker);
                        range.setEnd(node, Math.max(0, offset));
                        prefix = range.toString();
                    } catch (_) {
                        return null;
                    }
                    const rawMarker = marker.textContent || '';
                    const leadingWhitespace = /^\s/.test(rawMarker) ? 1 : 0;
                    const relative = Math.max(0, prefix.replace(/\s+/g, ' ').length - leadingWhitespace);
                    const start = Number(marker.dataset.hreaderTtsStart);
                    const end = Number(marker.dataset.hreaderTtsEnd);
                    return Math.max(start, Math.min(end, start + relative));
                };

                const positionForBoundary = (node, offset) => {
                    const direct = positionInMarker(node, offset);
                    if (direct !== null) return direct;
                    if (!node || !node.childNodes || node.childNodes.length === 0) return null;
                    const boundary = Math.max(0, Math.min(offset, node.childNodes.length));
                    for (let index = boundary; index < node.childNodes.length; index += 1) {
                        const nextPosition = positionForBoundary(node.childNodes[index], 0);
                        if (nextPosition !== null) return nextPosition;
                    }
                    for (let index = boundary - 1; index >= 0; index -= 1) {
                        const previous = node.childNodes[index];
                        const previousOffset = previous.nodeType === Node.TEXT_NODE
                            ? previous.nodeValue.length
                            : previous.childNodes.length;
                        const previousPosition = positionForBoundary(previous, previousOffset);
                        if (previousPosition !== null) return previousPosition;
                    }
                    return null;
                };

                const nearestMarker = (x, y) => {
                    let closest = null;
                    let closestDistance = Number.POSITIVE_INFINITY;
                    document.querySelectorAll(markerSelector).forEach(marker => {
                        const rect = marker.getBoundingClientRect();
                        const horizontal = x < rect.left ? rect.left - x : x > rect.right ? x - rect.right : 0;
                        const vertical = y < rect.top ? rect.top - y : y > rect.bottom ? y - rect.bottom : 0;
                        const distance = horizontal + vertical;
                        if (distance < closestDistance) {
                            closest = marker;
                            closestDistance = distance;
                        }
                    });
                    return closest ? Number(closest.dataset.hreaderTtsStart) : null;
                };

                const positionAt = (x, y) => {
                    let range = null;
                    if (document.caretRangeFromPoint) {
                        range = document.caretRangeFromPoint(x, y);
                    } else if (document.caretPositionFromPoint) {
                        const caret = document.caretPositionFromPoint(x, y);
                        const position = caret
                            ? positionForBoundary(caret.offsetNode, caret.offset)
                            : null;
                        if (position !== null) return position;
                    }
                    const position = range
                        ? positionForBoundary(range.startContainer, range.startOffset)
                        : null;
                    if (position !== null) return position;
                    return nearestMarker(x, y);
                };

                const selectionStart = () => {
                    const selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0) return null;
                    const range = selection.getRangeAt(0);
                    return positionForBoundary(range.startContainer, range.startOffset);
                };

                const highlight = (start, end) => {
                    const left = Number(start);
                    const right = Number(end);
                    document.querySelectorAll(markerSelector).forEach(marker => {
                        const markerStart = Number(marker.dataset.hreaderTtsStart);
                        const markerEnd = Number(marker.dataset.hreaderTtsEnd);
                        marker.classList.toggle('tts-current', left < markerEnd && right > markerStart);
                    });
                };

                const clear = () => {
                    document.querySelectorAll('.tts-current').forEach(marker => marker.classList.remove('tts-current'));
                };

                return { positionAt, selectionStart, highlight, clear };
            })();
        </script>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()

private fun String.toJavascriptInt(): Int? =
    trim().removeSurrounding("\"").toDoubleOrNull()?.toInt()
