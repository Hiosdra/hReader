package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.PendingChangeDao
import com.hiosdra.hreader.core.application.port.out.PendingArticleStatus
import com.hiosdra.hreader.core.application.port.out.PendingChangeStore
import com.hiosdra.hreader.core.domain.model.ArticleStatus

internal class PendingChangeRepository(
    private val pendingChangeDao: PendingChangeDao
) : PendingChangeStore {
    override suspend fun getPendingStatuses(): List<PendingArticleStatus> = pendingChangeDao
        .getPendingStatuses()
        .map { pending -> PendingArticleStatus(id = pending.id, status = pending.status) }

    override suspend fun clearPendingStatuses(ids: List<String>, pushedStatus: ArticleStatus) =
        pendingChangeDao.clearPendingSync(ids, pushedStatus)
}
