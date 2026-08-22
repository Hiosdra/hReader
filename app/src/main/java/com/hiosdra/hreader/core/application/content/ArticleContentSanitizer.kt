package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

private const val UNSUPPORTED_CONTENT_SELECTOR =
    "base, script, noscript, style, svg, canvas, iframe, frame, object, embed, video, audio, form"

private val ARTICLE_POLICY = articlePolicy(includeEmbeddedContent = false)
private val ARTICLE_POLICY_WITH_EMBEDS = articlePolicy(includeEmbeddedContent = true)

private fun articlePolicy(includeEmbeddedContent: Boolean): PolicyFactory {
    val builder = HtmlPolicyBuilder()
        .allowElements(
            "a", "abbr", "b", "blockquote", "br", "caption", "code", "col", "colgroup",
            "dd", "del", "details", "div", "dl", "dt", "em", "figcaption", "figure", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "i", "img", "ins", "kbd",
            "li", "main", "mark", "ol", "p", "pre", "q", "s", "samp", "section", "small",
            "span", "strong", "sub", "summary", "sup", "table", "tbody", "td", "tfoot", "th",
            "thead", "time", "tr", "tt", "u", "ul", "var"
        )
        .allowWithoutAttributes("a", "img", "span")
        .allowAttributes("class", "id", "lang", "dir", "title").globally()
        .allowAttributes("href").onElements("a")
        .allowAttributes("src", "alt", "height", "width", "loading").onElements("img")
        .allowAttributes("cite").onElements("blockquote", "q", "del", "ins")
        .allowAttributes("datetime").onElements("time")
        .allowAttributes("colspan", "rowspan", "scope").onElements("td", "th")
        .allowUrlProtocols("http", "https")
        .allowStyling()

    if (includeEmbeddedContent) {
        builder
            .allowElements("iframe", "frame", "object", "embed", "video", "audio", "source")
            .allowAttributes("src", "data-src").onElements("iframe", "frame", "embed", "video", "audio", "source")
            .allowAttributes("data", "data-src").onElements("object")
    }

    return builder.toFactory()
}

internal fun sanitizeArticleHtml(
    html: String,
    baseUrl: String? = null,
    embeddedMediaLabel: String
): String {
    if (html.isBlank()) return html

    val document = Jsoup.parse(sanitizeArticleInput(html), baseUrl.orEmpty())
    sanitizeArticleDocument(document, embeddedMediaLabel)
    return document.body().html()
}

internal fun sanitizeArticleDocument(document: Document, embeddedMediaLabel: String) {
    document.body().html(sanitizeArticleInput(document.body().html()))
    document.select(UNSUPPORTED_CONTENT_SELECTOR).toList().forEach { element ->
        val replacement = embeddedContentLink(element, embeddedMediaLabel)
        if (replacement == null) element.remove() else element.replaceWith(replacement)
    }
    document.body().html(ARTICLE_POLICY.sanitize(document.body().html()))
}

internal fun sanitizeArticleInput(html: String): String = ARTICLE_POLICY_WITH_EMBEDS.sanitize(html)

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
