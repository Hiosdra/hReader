package com.hiosdra.hreader.ui.article

import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.util.BionicReadingProcessor
import com.hiosdra.hreader.util.cleanUrl
import org.koin.compose.koinInject
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import kotlin.math.roundToInt

@Composable
fun ArticleWebView(
    articleContent: String,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    allowNetworkLoads: Boolean = true,
    localImagePaths: Map<String, String> = emptyMap(),
    textScale: Float = 1f,
    onLinkClick: ((String) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    preferencesManager: PreferencesManager = koinInject()
) {
    val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val codeBg = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.toArgb())
    val ruleColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.outlineVariant.toArgb())

    // Read by the request interceptor below, which outlives any single recomposition: the client is
    // built once with the WebView, while the downloaded images arrive with the article body.
    val cachedImages = remember { mutableStateOf(localImagePaths) }
    cachedImages.value = localImagePaths

    // Watched rather than read once, so turning the setting on redraws the article already open.
    val bionicReadingEnabled by preferencesManager.observeBionicReadingEnabled()
        .collectAsState(initial = preferencesManager.getBionicReadingEnabled())

    val processedContent = remember(articleContent, bionicReadingEnabled) {
        if (bionicReadingEnabled) {
            BionicReadingProcessor.processTextToBionic(articleContent)
        } else {
            articleContent
        }
    }

    val htmlData = remember(processedContent, textColorHex, linkColorHex, codeBg, ruleColor) {
        articleHtml(processedContent, textColorHex, linkColorHex, codeBg, ruleColor)
    }

    /**
     * What was last handed to the WebView. The update block runs on every recomposition — a read
     * state changing, images arriving, a scroll progress callback — and reloading there threw away
     * the reader's position in the article each time.
     */
    val loadedHtml = remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                fun updateScrollProgress(wv: WebView) {
                    if (onScrollProgress == null) return
                    val contentHeightPx = wv.contentHeight * wv.resources.displayMetrics.density
                    val viewHeight = wv.height.toFloat()
                    val denom = (contentHeightPx - viewHeight).coerceAtLeast(1f)
                    val progress = (wv.scrollY / denom).coerceIn(0f, 1f)
                    onScrollProgress.invoke(progress)
                }
                settings.javaScriptEnabled = false
                settings.defaultFontSize = 16
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
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
                        val localPath = cachedImages.value[url] ?: return null
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

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        onLinkClick?.invoke(cleanUrl(url))
                        return true
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view != null) view.post { updateScrollProgress(view) }
                    }
                }
                setOnScrollChangeListener { v, _, _, _, _ ->
                    if (v is WebView) updateScrollProgress(v)
                }
                setOnLongClickListener { v: View ->
                    val result = (v as? WebView)?.hitTestResult
                    if (result != null) {
                        val type = result.type
                        if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                            val url = result.extra
                            if (!url.isNullOrBlank()) {
                                onImageLongClick?.invoke(url)
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
            }

            if (loadedHtml.value != htmlData) {
                loadedHtml.value = htmlData
                webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
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
