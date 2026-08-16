package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

private const val UNSUPPORTED_CONTENT_SELECTOR =
    "base, script, iframe, frame, object, embed, video, audio, form"

internal fun sanitizeArticleHtml(
    html: String,
    baseUrl: String? = null,
    embeddedMediaLabel: String
): String {
    if (html.isBlank()) return html

    val document = Jsoup.parse(html, baseUrl.orEmpty())
    sanitizeArticleDocument(document, embeddedMediaLabel)
    return document.body().html()
}

internal fun sanitizeArticleDocument(document: Document, embeddedMediaLabel: String) {
    document.select(UNSUPPORTED_CONTENT_SELECTOR).toList().forEach { element ->
        val replacement = embeddedContentLink(element, embeddedMediaLabel)
        if (replacement == null) element.remove() else element.replaceWith(replacement)
    }
    document.allElements.toList().forEach { element ->
        element.attributes()
            .filter { it.key.startsWith("on", ignoreCase = true) || isDangerousUrl(it.value) }
            .forEach { attribute -> element.removeAttr(attribute.key) }
    }
}

private fun embeddedContentLink(element: Element, label: String): Element? {
    val url = when (element.tagName()) {
        "iframe", "frame", "embed" -> firstHttpUrl(element, listOf("src", "data-src"))
        "object" -> firstHttpUrl(element, listOf("data", "data-src"))
        "video", "audio" -> firstHttpUrl(element, listOf("src", "data-src"))
            ?: element.select("source").asSequence()
                .mapNotNull { source -> firstHttpUrl(source, listOf("src", "data-src")) }
                .firstOrNull()
        else -> null
    } ?: return null

    val paragraph = Element(Tag.valueOf("p"), element.baseUri())
    paragraph.appendElement("a")
        .attr("href", url)
        .text(label)
    return paragraph
}

private fun firstHttpUrl(element: Element, attributes: List<String>): String? =
    attributes.asSequence()
        .map { attribute -> element.absUrl(attribute).ifBlank { element.attr(attribute) }.trim() }
        .firstOrNull(::isHttpUrl)

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

private fun isDangerousUrl(value: String): Boolean {
    val normalized = value.trimStart().filterNot { it.isWhitespace() || it == '\u0000' }
    return normalized.startsWith("javascript:", ignoreCase = true) ||
        normalized.startsWith("vbscript:", ignoreCase = true)
}
