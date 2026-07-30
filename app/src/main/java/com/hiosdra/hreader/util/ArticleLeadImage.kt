package com.hiosdra.hreader.util

import org.jsoup.Jsoup

private val resizeSuffix = Regex("-\\d{2,5}x\\d{2,5}$")

/**
 * The picture to show above the article, or null when the body already carries it.
 *
 * Feeds routinely publish the lead picture twice: once as an enclosure or `media:content`, and
 * once as the first image of the article body. Rendering both showed the reader the same photo
 * twice in a row, the second time with the caption that belongs to it.
 */
fun leadImageUrl(enclosureUrl: String?, feedContent: String?, articleHtml: String?): String? {
    val candidate = enclosureUrl?.takeIf { it.isNotBlank() }
        ?: imageSources(feedContent).firstOrNull()
        ?: return null
    val candidateKeys = imageKeys(candidate)
    if (candidateKeys.isEmpty()) return candidate
    val alreadyInBody = imageSources(articleHtml).any { source ->
        imageKeys(source).any { it in candidateKeys }
    }
    return if (alreadyInBody) null else candidate
}

private fun imageSources(html: String?): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    return Jsoup.parse(html).select("img[src]")
        .map { it.attr("src").trim() }
        .filter { it.isNotBlank() }
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
    val stem = fileName.substringBeforeLast('.', "").replace(resizeSuffix, "")
    return buildSet {
        add(address)
        if (extension.isNotEmpty() && stem.isNotEmpty()) add("$stem.$extension")
    }
}
