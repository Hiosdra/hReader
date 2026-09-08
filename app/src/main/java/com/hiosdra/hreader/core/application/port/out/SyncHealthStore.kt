package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.ArticleSyncResult
import com.hiosdra.hreader.core.application.sync.SyncFailure
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import kotlinx.coroutines.flow.Flow

interface SyncHealthStore {
    fun getSnapshot(): SyncHealthSnapshot
    fun observe(): Flow<SyncHealthSnapshot>
    fun recordSyncStarted(attemptedAt: Long)
    fun recordSyncFinished(completedAt: Long, result: ArticleSyncResult)
    fun recordStageFailure(completedAt: Long, failure: SyncFailure)
    fun clear()
}
