package com.hiosdra.hreader.data.local.entity

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Enclosure

/**
 * Projection of the sync queue. Loading whole articles here would pull every cached article body
 * into memory just to read two columns.
 */
data class PendingStatus(
    val id: String,
    val status: ArticleStatus?
)

/**
 * What background prefetching needs about an article: what to download and where to file it.
 * Deliberately not the whole entity — prefetch walks every unread article, and reading them in
 * full would load the very bodies it is about to replace, plus a feed lookup per row.
 */
data class PrefetchTarget(
    val id: String,
    val url: String,
    val enclosures: List<Enclosure>
)

/** Unread articles per feed, counted locally so the subscription list works without a connection. */
data class FeedUnreadCount(
    val feedId: Long,
    val unreadCount: Int
)
