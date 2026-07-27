package com.hiosdra.hreader.data.model

import java.time.Instant

data class Entry(
    val id: Long,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val content: String?,
    val feed: Feed,
    val readingTime: Int?,
    val enclosures: List<Enclosure> = emptyList(),
    val status: ArticleStatus = ArticleStatus.UNREAD
)
