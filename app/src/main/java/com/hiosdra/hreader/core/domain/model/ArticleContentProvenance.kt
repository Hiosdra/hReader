package com.hiosdra.hreader.core.domain.model

import java.time.Instant

enum class ArticleContentKind {
    FULL_ARTICLE,
    FEED_FALLBACK,
    SAVED_WEB_PAGE,
    EXTERNAL_WEB_PAGE,
    UNAVAILABLE,
    UNKNOWN
}

enum class ArticleContentDelivery {
    NETWORK,
    LOCAL_STORAGE,
    UNKNOWN
}

data class ArticleContentProvenance(
    val kind: ArticleContentKind,
    val sourceUrl: String? = null,
    val fetchedAt: Instant? = null,
    val delivery: ArticleContentDelivery = ArticleContentDelivery.UNKNOWN,
    val isComplete: Boolean? = null
)

fun ArticleText.toProvenance(): ArticleContentProvenance = ArticleContentProvenance(
    kind = when (source) {
        ArticleContentSource.FULL -> ArticleContentKind.FULL_ARTICLE
        ArticleContentSource.FEED_FALLBACK -> ArticleContentKind.FEED_FALLBACK
    },
    sourceUrl = sourceUrl,
    fetchedAt = fetchedAt,
    delivery = delivery,
    isComplete = source == ArticleContentSource.FULL
)
