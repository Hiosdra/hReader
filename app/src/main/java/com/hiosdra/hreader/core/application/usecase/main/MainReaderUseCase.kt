package com.hiosdra.hreader.core.application.usecase.main

import androidx.paging.PagingData
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncFreshnessState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.application.sync.resolveFreshness
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

private const val FRESHNESS_REEVALUATION_MILLIS = 60_000L

class MainReaderUseCase(
    private val articles: ArticleQueryStore,
    private val articleMutations: ArticleMutationStore,
    private val cache: CacheStore,
    private val aiModels: AiModelCatalog,
    private val sync: SyncRequester,
    network: NetworkStatus,
    private val syncHealth: SyncHealthStore,
    private val syncPreferences: SyncPreferences,
    private val clock: Clock
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListItem>> = articles.pageArticles(query)

    fun observeUnreadCount(feedId: Long?): Flow<Int> = articles.observeUnreadCount(feedId)

    fun observeReadCount(feedId: Long?): Flow<Int> = articles.observeReadCount(feedId)

    suspend fun getFeed(feedId: Long): Feed? = articles.getFeed(feedId)

    suspend fun ensureCacheOwner() = cache.ensureCacheOwner()

    fun requestRefresh(): SyncOperationId? = sync.request(SyncIntent.User(userVisible = true))

    fun observeSync(): Flow<SyncOperationStatus> = sync.observeRequestedSync()

    fun observeFreshness(): Flow<SyncFreshnessState> = combine(
        syncHealth.observe(),
        isOnline,
        sync.observeSyncActivity(),
        observeFreshnessClock()
    ) { snapshot, online, syncing, now ->
        snapshot.resolveFreshness(
            now = now,
            isOnline = online,
            isSyncing = syncing,
            intervalMinutes = syncPreferences.getSyncIntervalMinutes()
        )
    }.distinctUntilChanged()

    private fun observeFreshnessClock(): Flow<Long> = flow {
        while (true) {
            emit(clock.millis())
            delay(FRESHNESS_REEVALUATION_MILLIS)
        }
    }

    fun prepareForOffline(): SyncOperationId? = sync.prepareForOffline()

    fun observeOfflinePreparation(): Flow<OfflinePreparationProgress> = sync.observeOfflinePreparation()

    suspend fun unreadIds(feedId: Long?): List<Long> = articles.unreadIds(feedId)

    suspend fun updateReadStatus(articleIds: List<Long>, read: Boolean) = articleMutations.updateReadStatus(
        articleIds.map(Long::toString),
        if (read) ArticleStatus.READ else ArticleStatus.UNREAD
    )

    suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long> =
        articleMutations.idsStillReadSince(articleIds, readBefore)

    suspend fun checkSelectedAiModel(): SelectedModelStatus = aiModels.checkSelectedModel()
}
