package com.hiosdra.hreader.core.domain.model

import java.time.Instant

data class Entry(
    val id: Long,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val content: String?,
    val preview: String? = null,
    val feed: Feed,
    val readingTime: Int?,
    val enclosures: List<Enclosure> = emptyList(),
    val status: ArticleStatus = ArticleStatus.UNREAD,
    val isBacklog: Boolean = false
)

data class ArticleListEntry(
    val id: Long,
    val title: String,
    val preview: String?,
    val author: String?,
    val publishedAt: Instant,
    val feed: Feed,
    val imageUrl: String?,
    val status: ArticleStatus = ArticleStatus.UNREAD,
    val isBacklog: Boolean = false
)
