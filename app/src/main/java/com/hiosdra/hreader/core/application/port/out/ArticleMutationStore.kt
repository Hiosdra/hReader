package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

data class BulkReadMarker(
    val feedId: Long?,
    val markedAt: Instant,
    val count: Int
)

interface ArticleMutationStore {
    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus)
    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus)
    suspend fun markUnreadAsRead(feedId: Long?): BulkReadMarker
    suspend fun undoBulkRead(marker: BulkReadMarker): Int
}
