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
    val status: ArticleStatus = ArticleStatus.UNREAD,
    /**
     * Downloaded to stock up for a stretch without a connection rather than because it was unread.
     * A backlog entry may already be read, and the article list only shows those on request.
     */
    val isBacklog: Boolean = false
)
