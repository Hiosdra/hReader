package com.hiosdra.hreader.core.domain.model

import java.time.Instant

data class ArticleText(
    val html: String,
    val leadImageUrl: String?,
    val source: ArticleContentSource = ArticleContentSource.FULL,
    val fetchedAt: Instant? = null,
    val sourceUrl: String? = null,
    val delivery: ArticleContentDelivery = ArticleContentDelivery.UNKNOWN
)
