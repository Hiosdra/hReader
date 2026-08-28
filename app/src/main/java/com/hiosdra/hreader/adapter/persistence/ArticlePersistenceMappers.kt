package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleListItem
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReaderItem
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.core.application.content.extractArticlePreview
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import java.time.Instant

/**
 * Merges a freshly fetched article with what is already cached. The backend owns the read state —
 * that is what makes a status change from another client land here. The one thing it cannot know
 * about is a local change that has not been pushed yet, so that one wins and stays queued.
 *
 * Read times are local bookkeeping: no backend reports them, so an article that arrives already
 * read without a recorded time is stamped [now], and one that goes back to unread loses its stamp.
 */
internal fun ArticleEntity.reconciledWith(local: ArticleEntity?, now: Instant): ArticleEntity {
    val merged = if (local != null && local.pendingSync) {
        copy(status = local.status, pendingSync = true)
    } else this
    val readAt = if (merged.status == ArticleStatus.READ) local?.readAt ?: now else null
    // A freshly fetched entity never knows it was downloaded as backlog, so the local marker is
    // carried over; losing it would expose the article to full-sync reconciliation.
    return merged.copy(
        fullContent = local?.fullContent?.takeIf { local.url == url && local.content == content },
        readAt = readAt,
        backlogFetchedAt = local?.backlogFetchedAt
    )
}

internal fun ArticleListItem.toListEntry(): ArticleListEntry = ArticleListEntry(
    id = id.toLong(),
    title = title,
    preview = preview,
    author = author,
    publishedAt = publishedAt,
    feed = Feed(
        id = feedId,
        title = feedTitle.orEmpty(),
        siteUrl = feedSiteUrl,
        feedUrl = feedUrl.orEmpty()
    ),
    imageUrl = leadImageUrl,
    status = status ?: ArticleStatus.UNREAD,
    isBacklog = backlogFetchedAt != null
)

internal fun ArticleReaderItem.toEntry(): Entry = Entry(
    id = id.toLong(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = null,
    preview = preview,
    feed = Feed(
        id = feedId,
        title = feedTitle.orEmpty(),
        siteUrl = feedSiteUrl,
        feedUrl = feedUrl.orEmpty()
    ),
    readingTime = readingTime,
    enclosures = enclosures,
    status = status ?: ArticleStatus.UNREAD,
    isBacklog = backlogFetchedAt != null
)

internal fun FeedEntity.toArticleFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

internal fun Entry.toEntity(): ArticleEntity = ArticleEntity(
    id = id.toString(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    // Empty rather than null when there is a body but no readable text in it: null means "not
    // derived yet" and puts the article back in the backfill queue on every prefetch.
    preview = content?.let { extractArticlePreview(it).orEmpty() },
    feedId = feed.id,
    readingTime = readingTime,
    enclosures = enclosures,
    leadImageUrl = enclosures.firstOrNull { it.isImage }?.url,
    status = status
)

internal fun Feed.toArticleFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)
