package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val PREVIEW_MAX_LENGTH = 400

private val COLLAPSIBLE_WHITESPACE = Regex("\\s+")

/**
 * The first readable sentence of an article body, stored alongside the article so the list never
 * has to parse HTML. It used to be derived per row on every recomposition, which put four regexes
 * and an HTML parse on the scrolling frame.
 */
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

/**
 * Block elements are separated before the text is collapsed, otherwise a heading runs straight
 * into the paragraph below it and the preview opens mid-word.
 */
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
