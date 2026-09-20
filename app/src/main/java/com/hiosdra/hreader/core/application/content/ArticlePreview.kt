package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val PREVIEW_MAX_LENGTH = 400

private val COLLAPSIBLE_WHITESPACE = Regex("\\s+")

fun extractArticlePreview(html: String?): String? {
    if (html.isNullOrBlank()) return null
    val text = plainText(html)
    if (!text.isReadableText()) return null
    return text.take(PREVIEW_MAX_LENGTH)
}

fun hasReadableArticleText(html: String?): Boolean {
    if (html.isNullOrBlank()) return false
    return plainText(html).isReadableText()
}

fun articlePreviewHtml(preview: String?): String? {
    if (preview.isNullOrBlank()) return null
    val escaped = Jsoup.parseBodyFragment("").body().appendText(preview).html()
    return "<p>$escaped</p>"
}

private fun plainText(html: String): String {
    val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return ""
    document.select(
        "script, style, nav, footer, aside, noscript, svg, figure, figcaption, video, audio, source, picture, img"
    )
        .remove()
    return document.readableText()
}

private fun String.isReadableText(): Boolean = replace('\u00A0', ' ').isNotBlank()

private fun Document.readableText(): String {
    select("br").append("\\n")
    select("p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote").append("\\n")
    return text()
        .replace("\\n", "\n")
        .lines()
        .map { it.replace(COLLAPSIBLE_WHITESPACE, " ").trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
}
