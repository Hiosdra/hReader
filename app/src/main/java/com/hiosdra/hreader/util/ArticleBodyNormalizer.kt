package com.hiosdra.hreader.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

internal fun removeDuplicateArticleTitle(html: String, title: String): String {
    if (html.isBlank() || title.isBlank()) return html

    val document = Jsoup.parseBodyFragment(html)
    val expectedTitle = normalizedArticleText(title)
    val heading = document.body()
        .select("h1, h2, h3")
        .firstOrNull { normalizedArticleText(it.text()) == expectedTitle }
        ?: return html

    val semanticHeader = heading.parents().firstOrNull { it.tagName() == "header" }
    if (semanticHeader != null && canRemoveHeader(semanticHeader, heading)) {
        semanticHeader.remove()
        return document.body().html()
    }

    var ancestor = heading.parent()
    heading.remove()
    while (
        ancestor != null &&
        ancestor !== document.body() &&
        ancestor.children().isEmpty() &&
        ancestor.text().isBlank()
    ) {
        val parent = ancestor.parent()
        ancestor.remove()
        ancestor = parent
    }
    return document.body().html()
}

private fun canRemoveHeader(header: Element, heading: Element): Boolean {
    val hasDirectText = header.textNodes().any { it.text().isNotBlank() }
    if (hasDirectText) return false

    return header.children()
        .filter { it !== heading }
        .all(::isArticleMetadata)
}

private fun isArticleMetadata(element: Element): Boolean {
    val tag = element.tagName()
    if (tag in setOf("address", "nav", "small", "time")) return true

    val marker = "${element.id()} ${element.className()}".lowercase(Locale.ROOT)
    if (listOf("author", "byline", "category", "date", "meta", "section", "tag").any(marker::contains)) {
        return true
    }

    return element.children().isNotEmpty() &&
        element.textNodes().all { it.text().isBlank() } &&
        element.children().all(::isArticleMetadata)
}

private fun normalizedArticleText(value: String): String =
    Jsoup.parse(value).text()
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', '…')
        .lowercase(Locale.ROOT)
