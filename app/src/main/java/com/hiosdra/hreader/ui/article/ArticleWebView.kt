package com.hiosdra.hreader.ui.article

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ArticleWebView(
    articleContent: String,
    baseUrl: String?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.defaultFontSize = 16
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            val htmlData = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { 
                            font-family: sans-serif; 
                            line-height: 1.5;
                            margin: 0;
                            padding: 0;
                            color: white;
                        }
                        img { max-width: 100%; height: auto; }
                        a { color: #1976D2; }
                    </style>
                </head>
                <body>
                    $articleContent
                </body>
                </html>
            """
            webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
