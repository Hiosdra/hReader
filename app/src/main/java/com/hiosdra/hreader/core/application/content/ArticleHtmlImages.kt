package com.hiosdra.hreader.core.application.content

import org.jsoup.Jsoup

data class PreparedArticleImages(
    val html: String,
    val imageUrls: List<String>
)

fun prepareArticleImages(
    html: String,
    baseUri: String,
    embeddedMediaLabel: String,
    articleTitle: String = ""
): PreparedArticleImages {
    if (html.isBlank()) return PreparedArticleImages(html, emptyList())

    val document = Jsoup.parse(html, baseUri)
    sanitizeArticleDocument(document, embeddedMediaLabel)
    removeDuplicateArticleTitle(document, articleTitle)
    document.select("source[srcset]").forEach { it.removeAttr("srcset") }
    val imageUrls = document.select("img").map { image ->
        image.removeAttr("srcset")
        val absolute = image.attr("abs:src")
        if (absolute.isNotBlank()) image.attr("src", absolute)
        absolute
    }
    return PreparedArticleImages(document.body().html(), imageUrls.filter { it.isNotBlank() }.distinct())
}
