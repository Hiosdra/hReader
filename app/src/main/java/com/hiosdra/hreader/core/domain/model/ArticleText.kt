package com.hiosdra.hreader.core.domain.model

import java.time.Instant

/** An article body as it is read: ready to render, with the picture that leads it worked out. */
data class ArticleText(
    val html: String,
    /** Null when the body carries the picture itself, and nothing belongs above the text. */
    val leadImageUrl: String?,
    val source: ArticleContentSource = ArticleContentSource.FULL,
    val fetchedAt: Instant? = null,
    val sourceUrl: String? = null,
    val delivery: ArticleContentDelivery = ArticleContentDelivery.UNKNOWN
)
