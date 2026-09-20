package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.buildFtsMatchQuery
import com.hiosdra.hreader.adapter.persistence.room.buildLikePattern
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.ArticleStatusUpdate
import java.time.Instant

private const val TAG = "ArticleMutationRepository"
private const val LOCAL_UPDATE_CHUNK = 400

internal class ArticleMutationRepository(
    private val articleMutationDao: ArticleMutationDao
) : ArticleMutationStore {
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

    override suspend fun updateReadStatus(
        query: ArticleListQuery,
        newStatus: ArticleStatus
    ): ArticleStatusUpdate {
        val readAt = Instant.now().takeIf { newStatus == ArticleStatus.READ }
        val changedCount = updateScope(query, newStatus, readAt)
        return ArticleStatusUpdate(changedCount, readAt.takeIf { changedCount > 0 })
    }

    override suspend fun markReadThrough(
        query: ArticleListQuery,
        articleId: Long
    ): ArticleStatusUpdate {
        val publishedAt = articleMutationDao.getPublishedAt(articleId.toString())
            ?: return ArticleStatusUpdate(0, null)
        val readAt = Instant.now()
        val search = searchArguments(query)
        val changedCount = if (search == null) {
            articleMutationDao.markScopeReadThrough(
                feedId = query.feedId,
                readStatus = ArticleStatus.READ,
                readAt = readAt,
                articleId = articleId.toString(),
                publishedAt = publishedAt
            )
        } else {
            articleMutationDao.markSearchReadThrough(
                feedId = query.feedId,
                readStatus = ArticleStatus.READ,
                readAt = readAt,
                articleId = articleId.toString(),
                publishedAt = publishedAt,
                ftsQuery = search.ftsQuery,
                titleQuery = search.titleQuery
            )
        }
        return ArticleStatusUpdate(changedCount, readAt.takeIf { changedCount > 0 })
    }

    override suspend fun undoReadStatus(readAt: Instant): Int = articleMutationDao.undoReadStatus(
        readAt = readAt,
        readStatus = ArticleStatus.READ,
        unreadStatus = ArticleStatus.UNREAD
    )

    private suspend fun updateScope(
        query: ArticleListQuery,
        newStatus: ArticleStatus,
        readAt: Instant?
    ): Int {
        val search = searchArguments(query)
        return if (search == null) {
            if (newStatus == ArticleStatus.READ) {
                articleMutationDao.markScopeRead(
                    feedId = query.feedId,
                    readStatus = ArticleStatus.READ,
                    readAt = checkNotNull(readAt)
                )
            } else {
                articleMutationDao.markScopeUnread(
                    feedId = query.feedId,
                    readStatus = ArticleStatus.READ,
                    unreadStatus = ArticleStatus.UNREAD
                )
            }
        } else if (newStatus == ArticleStatus.READ) {
            articleMutationDao.markSearchRead(
                feedId = query.feedId,
                readStatus = ArticleStatus.READ,
                readAt = checkNotNull(readAt),
                ftsQuery = search.ftsQuery,
                titleQuery = search.titleQuery
            )
        } else {
            articleMutationDao.markSearchUnread(
                feedId = query.feedId,
                readStatus = ArticleStatus.READ,
                unreadStatus = ArticleStatus.UNREAD,
                ftsQuery = search.ftsQuery,
                titleQuery = search.titleQuery
            )
        }
    }

    private fun searchArguments(query: ArticleListQuery): SearchArguments? =
        buildFtsMatchQuery(query.searchQuery.trim())?.let { ftsQuery ->
            SearchArguments(ftsQuery, buildLikePattern(query.searchQuery))
        }

    private data class SearchArguments(
        val ftsQuery: String,
        val titleQuery: String
    )
}

internal fun List<String>.toArticleIds(what: String): List<Long> {
    val ids = mapNotNull { it.toLongOrNull() }
    if (ids.size != size) Log.w(TAG, "Ignored ${size - ids.size} unreadable article ids in $what")
    return ids
}
