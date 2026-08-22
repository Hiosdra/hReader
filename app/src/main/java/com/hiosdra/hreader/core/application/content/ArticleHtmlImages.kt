package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup

data class PreparedArticleImages(
    /** The body, ready to render. */
    val html: String,
    /** Every picture it references, in the absolute form the downloaded copies are filed under. */
    val imageUrls: List<String>
)

/**
 * Prepares an article body so its images can be served from the copies already on disk.
 *
 * Every `src` is resolved against the article's own address, because that absolute form is the key
 * the images were filed under when they were downloaded. `srcset` and `<picture>` sources are
 * dropped: they list sizes that were never fetched, and a renderer that honours them would go back
 * to the network for each one — which offline means a timeout per image.
 *
 * The addresses come back alongside the body rather than being read out of it again: what to
 * download, what to render and which picture leads the article are three questions about the same
 * document, and parsing it once to answer all three is what keeps this off the reader's way.
 */
fun prepareArticleImages(
    html: String,
    baseUri: String,
    embeddedMediaLabel: String
): PreparedArticleImages {
    if (html.isBlank()) return PreparedArticleImages(html, emptyList())

    val document = Jsoup.parse(sanitizeArticleInput(html), baseUri)
    sanitizeArticleDocument(document, embeddedMediaLabel)
    document.select("source[srcset]").forEach { it.removeAttr("srcset") }
    val imageUrls = document.select("img").map { image ->
        image.removeAttr("srcset")
        val absolute = image.attr("abs:src")
        if (absolute.isNotBlank()) image.attr("src", absolute)
        absolute
    }
    return PreparedArticleImages(document.body().html(), imageUrls.filter { it.isNotBlank() }.distinct())
}
