package com.hiosdra.hreader.core.application.content

private const val MAX_CACHED_ARTICLE_CHARACTERS = 2_000_000L

object ArticleHtmlTransformer {
    private val cache = WeightedStringLruCache<CacheKey>(MAX_CACHED_ARTICLE_CHARACTERS)

    fun transform(
        html: String,
        baseUrl: String?,
        articleTitle: String,
        embeddedMediaLabel: String
    ): String {
        val key = CacheKey(contentFingerprint(html, baseUrl, articleTitle, embeddedMediaLabel))
        cache[key]?.let { return it }
        val transformed = sanitizeArticleHtml(html, baseUrl, embeddedMediaLabel)
            .let { removeDuplicateArticleTitle(it, articleTitle) }
        cache.put(key, transformed)
        return transformed
    }

    private data class CacheKey(val fingerprint: String)
}
