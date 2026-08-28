package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

private const val TAG = "ArticleMutationRepository"
private const val LOCAL_UPDATE_CHUNK = 400

internal class ArticleMutationRepository(
    private val articleDao: ArticleDao
) : ArticleMutationStore {
    override suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) {
        if (articleIds.isEmpty()) return
        val readAt = Instant.now().takeIf { newStatus == ArticleStatus.READ }
        articleIds.chunked(LOCAL_UPDATE_CHUNK).forEach { chunk ->
            articleDao.updateStatusForIds(chunk, newStatus, readAt)
        }
    }

    override suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    override suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long> =
        articleIds.map { it.toString() }
            .chunked(LOCAL_UPDATE_CHUNK)
            .flatMap { articleDao.getIdsReadNoLaterThan(it, readBefore) }
            .toArticleIds("an undo")
}

internal fun List<String>.toArticleIds(what: String): List<Long> {
    val ids = mapNotNull { it.toLongOrNull() }
    if (ids.size != size) Log.w(TAG, "Ignored ${size - ids.size} unreadable article ids in $what")
    return ids
}
