package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.FullSyncSeenEntity
import com.hiosdra.hreader.core.domain.model.ArticleStatus

@Dao
interface FullSyncSeenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FullSyncSeenEntity>)

    @Query(
        "SELECT a.id FROM articles a " +
            "WHERE (a.status IS NULL OR a.status != :readStatus) " +
            "AND a.pendingSync = 0 " +
            "AND a.backlogFetchedAt IS NULL " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM full_sync_seen s " +
            "WHERE s.runId = :runId AND s.articleId = a.id)"
    )
    suspend fun getSyncedUnreadIdsMissingFrom(
        runId: String,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query("DELETE FROM full_sync_seen WHERE runId = :runId")
    suspend fun deleteRun(runId: String)

    @Query("DELETE FROM full_sync_seen")
    suspend fun deleteAll()
}
