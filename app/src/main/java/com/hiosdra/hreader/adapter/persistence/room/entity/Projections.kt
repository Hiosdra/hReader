package com.hiosdra.hreader.adapter.persistence.room.entity

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Enclosure
import java.time.Instant

/**
 * A row of the article list, joined to its feed in one statement. Reading whole articles here
 * pulled every cached body into memory for a screen that shows a title and four lines of preview,
 * and looked the feed up once per row on top of it.
 *
 * [feedTitle] is nullable because the join is outer: an article whose feed was unsubscribed
 * elsewhere used to abort the whole list with "Feed not found".
 */
data class ArticleListItem(
    val id: Long,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val preview: String?,
    val readingTime: Int?,
    val enclosures: List<Enclosure>,
    val status: ArticleStatus?,
    val starred: Boolean,
    val backlogFetchedAt: Instant?,
    val feedId: Long,
    val feedTitle: String?,
    val feedSiteUrl: String?,
    val feedUrl: String?
)

/** The reader metadata plus its feed. The body is loaded only for the nearby pages. */
data class ArticleReaderItem(
    val id: Long,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val readingTime: Int?,
    val enclosures: List<Enclosure>,
    val status: ArticleStatus?,
    val starred: Boolean,
    val backlogFetchedAt: Instant?,
    val feedId: Long,
    val feedTitle: String?,
    val feedSiteUrl: String?,
    val feedUrl: String?
)

/** Just enough of an article to derive its stored preview from the body. */
data class ArticleBody(
    val id: Long,
    val content: String?
)

/** Stars queued for the backend, the starred counterpart of [PendingStatus]. */
data class PendingStar(
    val id: Long,
    val starred: Boolean
)

/**
 * Projection of the sync queue. Loading whole articles here would pull every cached article body
 * into memory just to read two columns.
 */
data class PendingStatus(
    val id: Long,
    val status: ArticleStatus?
)

/**
 * What background prefetching needs about an article: what to download and where to file it.
 * Deliberately not the whole entity — prefetch walks every unread article, and reading them in
 * full would load the very bodies it is about to replace, plus a feed lookup per row.
 */
data class PrefetchTarget(
    val id: Long,
    val url: String,
    val enclosures: List<Enclosure>
)

/** Unread articles per feed, counted locally so the subscription list works without a connection. */
data class FeedUnreadCount(
    val feedId: Long,
    val unreadCount: Int
)
