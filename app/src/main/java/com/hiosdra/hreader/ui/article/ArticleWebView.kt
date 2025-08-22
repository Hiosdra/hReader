package com.hiosdra.hreader.ui.article

import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.hiosdra.hreader.util.cleanUrl

@Composable
fun ArticleWebView(
    articleContent: String,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onScrollProgress: ((Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null
) {
    val textColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onSurface.toArgb())
    val linkColorHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val codeBg = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.surfaceVariant.toArgb())

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                fun updateScrollProgress(wv: WebView) {
                    if (onScrollProgress == null) return
                    val contentHeightPx = wv.contentHeight * wv.scale
                    val viewHeight = wv.height
                    val range = (contentHeightPx - viewHeight).coerceAtLeast(1f)
                    val progress = (wv.scrollY / range).coerceIn(0f, 1f)
                    onScrollProgress.invoke(progress)
                }
                settings.javaScriptEnabled = false
                settings.defaultFontSize = 16
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        onLinkClick?.invoke(cleanUrl(url))
                        return true
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null) {
                            onLinkClick?.invoke(cleanUrl(url))
                            return true
                        }
                        return false
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
            val htmlData = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
                    <style>
                        :root { --text:$textColorHex; --link:$linkColorHex; --code:$codeBg; }
                        body { font-family: system-ui,-apple-system,Roboto,sans-serif; line-height:1.6; margin:0; padding:0 0 32px 0; color:var(--text); background:transparent; }
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
                        th,td { border:1px solid rgba(255,255,255,.12); padding:6px 8px; text-align:left; }
                        ul,ol { padding-left:1.25em; }
                        hr { border:none; height:1px; background:rgba(255,255,255,.15); margin:32px 0; }
                    </style>
                </head>
                <body>$articleContent</body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
