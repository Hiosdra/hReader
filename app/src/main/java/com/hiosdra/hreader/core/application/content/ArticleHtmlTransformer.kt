package com.hiosdra.hreader.core.application.content

private const val MAX_CACHED_ARTICLE_VARIANTS = 12

object ArticleHtmlTransformer {
    private val cache = object : LinkedHashMap<CacheKey, String>(
        MAX_CACHED_ARTICLE_VARIANTS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, String>?): Boolean =
            size > MAX_CACHED_ARTICLE_VARIANTS
    }

    @Synchronized
    fun transform(
        html: String,
        baseUrl: String?,
        articleTitle: String,
        embeddedMediaLabel: String
    ): String {
        val key = CacheKey(html, baseUrl, articleTitle, embeddedMediaLabel)
        cache[key]?.let { return it }
        val transformed = sanitizeArticleHtml(html, baseUrl, embeddedMediaLabel)
            .let { removeDuplicateArticleTitle(it, articleTitle) }
        cache[key] = transformed
        return transformed
    }

    private data class CacheKey(
        val html: String,
        val baseUrl: String?,
        val articleTitle: String,
        val embeddedMediaLabel: String
    )
}
