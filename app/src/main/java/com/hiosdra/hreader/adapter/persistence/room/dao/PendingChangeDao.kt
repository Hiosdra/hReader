package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.PendingStatus
import com.hiosdra.hreader.core.domain.model.ArticleStatus

@Dao
interface PendingChangeDao {
    @Query("UPDATE articles SET pendingSync = 0 WHERE id IN (:ids) AND status = :pushedStatus")
    suspend fun clearPendingSync(ids: List<String>, pushedStatus: ArticleStatus)

    @Query("SELECT id, status FROM articles WHERE pendingSync = 1")
    suspend fun getPendingStatuses(): List<PendingStatus>
}
