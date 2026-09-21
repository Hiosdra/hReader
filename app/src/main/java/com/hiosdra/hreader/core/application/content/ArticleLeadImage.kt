package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup

private val sizeSuffix = Regex("(-\\d{2,5}x\\d{2,5}|-scaled)+$")

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

private fun firstImageSource(html: String?, baseUri: String): String? {
    if (html.isNullOrBlank()) return null
    return Jsoup.parse(html, baseUri).select("img[src]")
        .map { image -> image.attr("abs:src").ifBlank { image.attr("src") }.trim() }
        .firstOrNull { it.isNotBlank() }
}

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
