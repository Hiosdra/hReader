package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleStatus

data class PendingArticleStatus(
    val id: String,
    val status: ArticleStatus?
)

interface PendingChangeStore {
    suspend fun getPendingStatuses(): List<PendingArticleStatus>
    suspend fun clearPendingStatuses(ids: List<String>, pushedStatus: ArticleStatus)
}
