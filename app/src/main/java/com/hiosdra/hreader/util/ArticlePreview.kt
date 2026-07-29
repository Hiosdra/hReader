package com.hiosdra.hreader.util

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
    if (text.isBlank()) return null
    return text.take(PREVIEW_MAX_LENGTH)
}

private fun plainText(html: String): String {
    val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return ""
    document.select("script, style, svg, figure, figcaption, video, audio, source, picture, img")
        .remove()
    return document.readableText()
}

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
