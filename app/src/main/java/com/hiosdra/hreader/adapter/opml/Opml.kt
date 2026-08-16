package com.hiosdra.hreader.adapter.opml

import com.hiosdra.hreader.core.domain.model.Feed
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A subscription as an OPML file records it, which is a feed address and not much else. */
data class OpmlFeed(
    val title: String,
    val feedUrl: String,
    val siteUrl: String?
)

/**
 * Subscriptions as OPML, the one interchange format every reader agrees on. Built from the cached
 * feed list rather than from the backend: it is complete, and it works offline.
 */
fun buildOpml(feeds: List<Feed>, title: String): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<opml version=\"2.0\">\n")
    append("  <head>\n    <title>${title.xmlEscaped()}</title>\n  </head>\n")
    append("  <body>\n")
    feeds.forEach { feed ->
        append("    <outline type=\"rss\" text=\"${feed.title.xmlEscaped()}\" ")
        append("title=\"${feed.title.xmlEscaped()}\" ")
        append("xmlUrl=\"${feed.feedUrl.xmlEscaped()}\"")
        feed.siteUrl?.takeIf { it.isNotBlank() }?.let { append(" htmlUrl=\"${it.xmlEscaped()}\"") }
        append("/>\n")
    }
    append("  </body>\n</opml>\n")
}

/**
 * Every feed an OPML file names, with the category tree flattened away — outlines nest, and what
 * matters here is the set of addresses to subscribe to. Entries without an `xmlUrl` are folders.
 */
fun parseOpml(xml: String): List<OpmlFeed> {
    val document = runCatching { Jsoup.parse(xml, "", Parser.xmlParser()) }.getOrNull()
        ?: return emptyList()
    return document.select("outline[xmlUrl]").mapNotNull { outline ->
        val feedUrl = outline.attr("xmlUrl").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = outline.attr("title").ifBlank { outline.attr("text") }.ifBlank { feedUrl }
        OpmlFeed(
            title = title.trim(),
            feedUrl = feedUrl,
            siteUrl = outline.attr("htmlUrl").trim().takeIf { it.isNotBlank() }
        )
    }.distinctBy { it.feedUrl }
}

private fun String.xmlEscaped(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
