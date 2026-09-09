package com.hiosdra.hreader.core.application.usecase.sync

import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SyncHealthUseCase(
    private val syncHealth: SyncHealthStore,
    private val sync: SyncRequester,
    private val syncPreferences: SyncPreferences,
    private val feeds: FeedStore,
    network: NetworkStatus
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    fun observeSnapshot(): Flow<SyncHealthSnapshot> = syncHealth.observe()

    fun observeSyncActivity(): Flow<Boolean> = sync.observeSyncActivity()

    fun observeNextScheduledSync(): Flow<Long?> = sync.observeNextScheduledSync()

    fun getSyncIntervalMinutes(): Int = syncPreferences.getSyncIntervalMinutes()

    fun observeSyncIntervalMinutes(): Flow<Int> = syncPreferences.observeSyncIntervalMinutes()

    suspend fun getCachedFeeds(): List<Feed> = feeds.getCachedFeeds()

    fun retry(): SyncOperationId? = sync.syncNow(userVisible = true)
}
