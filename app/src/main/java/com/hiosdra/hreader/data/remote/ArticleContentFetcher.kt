package com.hiosdra.hreader.data.remote

import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

private val NON_CONTENT_SELECTORS = listOf(
    "script", "style", "noscript", "iframe", "form", "nav", "header", "footer", "aside"
)

private val READABLE_SELECTORS = listOf(
    "[itemprop=articleBody]",
    "article",
    "main",
    ".post-content",
    ".entry-content",
    ".article-content",
    ".article-body"
)

class ArticleContentFetcher(private val client: OkHttpClient) {
    suspend fun fetchReadableContent(url: String): String =
        client.fetchDocument(url).extractReadableHtml()
}

private fun Document.extractReadableHtml(): String {
    NON_CONTENT_SELECTORS.forEach { select(it).remove() }
    val readable = READABLE_SELECTORS.firstNotNullOfOrNull { selectFirst(it) } ?: body()
    return readable.html()
}
