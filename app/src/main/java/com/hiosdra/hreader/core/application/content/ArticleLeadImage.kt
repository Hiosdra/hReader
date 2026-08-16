package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup

private val sizeSuffix = Regex("(-\\d{2,5}x\\d{2,5}|-scaled)+$")

/**
 * The picture to show above the article, or null when the body already carries it.
 *
 * Feeds routinely publish the lead picture twice: once as an enclosure or `media:content`, and
 * once as the first image of the article body. Rendering both showed the reader the same photo
 * twice in a row, the second time with the caption that belongs to it.
 *
 * [bodyImageUrls] is what [prepareArticleImages] collected while resolving the body, so deciding
 * this costs no second reading of the article.
 */
fun leadImageUrl(
    enclosureUrl: String?,
    feedContent: String?,
    bodyImageUrls: List<String>,
    baseUri: String
): String? {
    val candidate = enclosureUrl?.takeIf { it.isNotBlank() }
        ?: firstImageSource(feedContent, baseUri)
        ?: return null
    val candidateKeys = imageKeys(candidate)
    if (candidateKeys.isEmpty()) return candidate
    val alreadyInBody = bodyImageUrls.any { source -> imageKeys(source).any { it in candidateKeys } }
    return if (alreadyInBody) null else candidate
}

/** Resolved against the article's own address, so a feed carrying relative sources still loads. */
private fun firstImageSource(html: String?, baseUri: String): String? {
    if (html.isNullOrBlank()) return null
    return Jsoup.parse(html, baseUri).select("img[src]")
        .map { image -> image.attr("abs:src").ifBlank { image.attr("src") }.trim() }
        .firstOrNull { it.isNotBlank() }
}

/**
 * The same photo rarely arrives under the same address twice: the enclosure is the original, while
 * the body carries a resized copy, a protocol-relative address or one routed through an image
 * proxy. Comparing the file name as well as the whole address catches those without needing to
 * know any particular publisher's conventions.
 */
private fun imageKeys(url: String): Set<String> {
    val address = url.trim()
        .substringAfter("://")
        .removePrefix("//")
        .substringBefore('#')
        .substringBefore('?')
        .removePrefix("www.")
        .trimEnd('/')
        .lowercase()
    if (address.isEmpty()) return emptySet()

    val fileName = address.substringAfterLast('/')
    val extension = fileName.substringAfterLast('.', "")
    val stem = fileName.substringBeforeLast('.', "").replace(sizeSuffix, "")
    return buildSet {
        add(address)
        if (extension.isNotEmpty() && stem.isNotEmpty()) add("$stem.$extension")
    }
}
