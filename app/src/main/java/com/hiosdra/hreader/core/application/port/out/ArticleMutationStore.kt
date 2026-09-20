package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatusUpdate
import java.time.Instant

interface ArticleMutationStore {
    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus)
    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus)
    suspend fun updateReadStatus(
        query: ArticleListQuery,
        newStatus: ArticleStatus
    ): ArticleStatusUpdate
    suspend fun markReadThrough(
        query: ArticleListQuery,
        articleId: Long
    ): ArticleStatusUpdate
    suspend fun undoReadStatus(readAt: Instant): Int
}
