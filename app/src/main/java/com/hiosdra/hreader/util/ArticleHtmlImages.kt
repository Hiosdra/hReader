package com.hiosdra.hreader.util

import org.jsoup.Jsoup

/**
 * Prepares an article body so its images can be served from the copies already on disk.
 *
 * Every `src` is resolved against the article's own address, because that absolute form is the key
 * the images were filed under when they were downloaded. `srcset` and `<picture>` sources are
 * dropped: they list sizes that were never fetched, and a renderer that honours them would go back
 * to the network for each one — which offline means a timeout per image.
 */
fun absolutizeArticleImages(html: String, baseUri: String): String {
    if (html.isBlank()) return html

    val document = Jsoup.parse(html, baseUri)
    document.select("source[srcset]").forEach { it.removeAttr("srcset") }
    document.select("img").forEach { image ->
        image.removeAttr("srcset")
        val absolute = image.attr("abs:src")
        if (absolute.isNotBlank()) image.attr("src", absolute)
    }
    return document.body().html()
}
