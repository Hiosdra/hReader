package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import java.util.Locale

private const val UNSUPPORTED_CONTENT_SELECTOR =
    "base, script, iframe, frame, object, embed, video, audio, form"
private val URL_ATTRIBUTES = setOf(
    "action",
    "cite",
    "data",
    "formaction",
    "href",
    "poster",
    "src",
    "srcset",
    "xlink:href"
)
private val URL_SCHEME = Regex("([A-Za-z][A-Za-z0-9+.-]*):")
private val SAFE_RASTER_DATA_IMAGE = Regex(
    "^data:image/(?:png|jpeg|jpg|gif|webp|bmp)(?:[;,])",
    RegexOption.IGNORE_CASE
)

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

internal fun sanitizeArticleDocument(document: Document, embeddedMediaLabel: String?) {
    document.select(UNSUPPORTED_CONTENT_SELECTOR).toList().forEach { element ->
        val replacement = embeddedMediaLabel?.let { embeddedContentLink(element, it) }
        if (replacement == null) element.remove() else element.replaceWith(replacement)
    }
    document.allElements.toList().forEach { element ->
        element.attributes()
            .filter { it.key.startsWith("on", ignoreCase = true) || isDangerousUrl(it.key, it.value) }
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

private fun isDangerousUrl(attributeName: String, value: String): Boolean {
    val normalizedName = attributeName.lowercase(Locale.ROOT)
    if (normalizedName !in URL_ATTRIBUTES) return false
    val normalized = value.filterNot { it.isWhitespace() || it.isISOControl() }
    return if (normalizedName == "srcset") {
        normalized.split(',').any { candidate ->
            val scheme = URL_SCHEME.matchAt(candidate, 0)?.value?.removeSuffix(":")
                ?: return@any false
            !isAllowedUrlScheme(scheme, candidate, normalizedName)
        }
    } else {
        val scheme = URL_SCHEME.matchAt(normalized, 0)?.value?.removeSuffix(":") ?: return false
        !isAllowedUrlScheme(scheme, normalized, normalizedName)
    }
}

private fun isAllowedUrlScheme(scheme: String, normalizedValue: String, attributeName: String): Boolean {
    val normalizedScheme = scheme.lowercase(Locale.ROOT)
    return normalizedScheme == "http" || normalizedScheme == "https" ||
        (normalizedScheme == "data" && attributeName in setOf("src", "srcset") &&
            SAFE_RASTER_DATA_IMAGE.containsMatchIn(normalizedValue))
}
