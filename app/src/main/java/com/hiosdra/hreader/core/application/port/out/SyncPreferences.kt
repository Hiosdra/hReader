package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.SyncCheckpoint
import kotlinx.coroutines.flow.Flow

interface SyncPreferences {
    fun getLastSyncTimestamp(): Long
    fun setLastSyncTimestamp(timestamp: Long)
    fun getCacheOwnerKey(): String
    fun setCacheOwnerKey(ownerKey: String)
    fun isCacheCleanupPending(): Boolean
    fun setCacheCleanupPending(pending: Boolean)
    fun observeLastSyncTimestamp(): Flow<Long>
    fun getLastFullSyncTimestamp(): Long
    fun setLastFullSyncTimestamp(timestamp: Long)
    fun getSyncCheckpoint(): SyncCheckpoint?
    fun setSyncCheckpoint(checkpoint: SyncCheckpoint)
    fun clearSyncCheckpoint()
    fun getOfflineBacklogTarget(): Int
    fun setOfflineBacklogTarget(target: Int)
    fun getImageDownloadEnabled(): Boolean
    fun setImageDownloadEnabled(enabled: Boolean)
    fun getImageCacheBudgetMegabytes(): Int
    fun setImageCacheBudgetMegabytes(megabytes: Int)
    fun getSyncIntervalMinutes(): Int
    fun setSyncIntervalMinutes(minutes: Int)
    fun observeSyncIntervalMinutes(): Flow<Int>
    fun getSyncOnUnmeteredOnly(): Boolean
    fun setSyncOnUnmeteredOnly(enabled: Boolean)
    fun getSyncWhileRoaming(): Boolean
    fun setSyncWhileRoaming(enabled: Boolean)
    fun getQuietHoursEnabled(): Boolean
    fun setQuietHoursEnabled(enabled: Boolean)
    fun getQuietHoursStartHour(): Int
    fun getQuietHoursEndHour(): Int
    fun setQuietHours(startHour: Int, endHour: Int)
    fun getLastChainedSyncTimestamp(): Long
    fun setLastChainedSyncTimestamp(timestamp: Long)
}
