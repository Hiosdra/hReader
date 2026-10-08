package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.BulkReadMarker
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ArticleMutationRepository"
private const val LOCAL_UPDATE_CHUNK = 400

internal class ArticleMutationRepository(
    private val articleMutationDao: ArticleMutationDao
) : ArticleMutationStore {
    private val lastBulkReadTimestampMillis = AtomicLong(0L)

    override suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) {
        if (articleIds.isEmpty()) return
        val readAt = Instant.now().takeIf { newStatus == ArticleStatus.READ }
        articleIds.chunked(LOCAL_UPDATE_CHUNK).forEach { chunk ->
            articleMutationDao.updateStatusForIds(chunk, newStatus, readAt)
        }
    }

    override suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    override suspend fun markUnreadAsRead(feedId: Long?): BulkReadMarker {
        val markedAt = nextBulkReadTimestamp()
        val count = articleMutationDao.markUnreadAsRead(feedId, markedAt)
        return BulkReadMarker(feedId, markedAt, count)
    }

    override suspend fun markUnreadAsReadUpTo(
        feedId: Long?,
        articleId: String,
        publishedAt: Instant
    ): BulkReadMarker {
        val markedAt = nextBulkReadTimestamp()
        val count = articleMutationDao.markUnreadAsReadUpTo(feedId, articleId, publishedAt, markedAt)
        return BulkReadMarker(feedId, markedAt, count)
    }

    override suspend fun undoBulkRead(marker: BulkReadMarker): Int =
        articleMutationDao.undoBulkRead(marker.feedId, marker.markedAt)

    private fun nextBulkReadTimestamp(): Instant {
        val nowMillis = System.currentTimeMillis()
        return Instant.ofEpochMilli(
            lastBulkReadTimestampMillis.updateAndGet { previous -> maxOf(nowMillis, previous + 1) }
        )
    }
}

internal fun List<String>.toArticleIds(what: String): List<Long> {
    val ids = mapNotNull { it.toLongOrNull() }
    if (ids.size != size) Log.w(TAG, "Ignored ${size - ids.size} unreadable article ids in $what")
    return ids
}
