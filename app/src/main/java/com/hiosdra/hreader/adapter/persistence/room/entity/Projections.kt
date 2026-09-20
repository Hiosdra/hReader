package com.hiosdra.hreader.adapter.persistence.room.entity

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Enclosure
import java.time.Instant

data class ArticleListItem(
    val id: String,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val preview: String?,
    val readingTime: Int?,
    val leadImageUrl: String?,
    val status: ArticleStatus?,
    val backlogFetchedAt: Instant?,
    val feedId: Long,
    val feedTitle: String?,
    val feedSiteUrl: String?,
    val feedUrl: String?,
    val feedAutoMarkRead: Boolean?
)

data class ArticleReaderItem(
    val id: String,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val preview: String?,
    val readingTime: Int?,
    val enclosures: List<Enclosure>,
    val status: ArticleStatus?,
    val backlogFetchedAt: Instant?,
    val feedId: Long,
    val feedTitle: String?,
    val feedSiteUrl: String?,
    val feedUrl: String?,
    val feedAutoMarkRead: Boolean?
)

data class ArticleBody(
    val id: String,
    val content: String?
)

data class PendingStatus(
    val id: String,
    val status: ArticleStatus?
)

data class PrefetchTarget(
    val id: String,
    val url: String,
    val enclosures: List<Enclosure>
)

data class AiOverviewPrefetchTarget(
    val id: String,
    val title: String,
    val url: String
)

data class FeedUnreadCount(
    val feedId: Long,
    val unreadCount: Int
)
