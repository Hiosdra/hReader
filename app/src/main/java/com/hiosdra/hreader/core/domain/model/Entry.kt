package com.hiosdra.hreader.core.domain.model

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
    val starred: Boolean = false,
    /**
     * Downloaded to stock up for a stretch without a connection rather than because it was unread.
     * A backlog entry may already be read, and the article list only shows those on request.
     */
    val isBacklog: Boolean = false
)

/**
 * What a row of the article list needs, and nothing else. The list used to be built from [Entry],
 * which carries the whole article body: several thousand cached articles were held in memory three
 * times over for a screen showing a title, a preview and one thumbnail.
 */
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
