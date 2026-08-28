package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

interface ArticleMutationStore {
    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus)
    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus)
    suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long>
    suspend fun updateStarred(articleId: Long, starred: Boolean)
}
