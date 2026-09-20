package com.hiosdra.hreader.core.application.usecase.main

import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant

class MainReaderUseCase(
    private val articles: ArticleQueryStore,
    private val articleMutations: ArticleMutationStore,
    private val cache: CacheStore,
    private val aiModels: AiModelCatalog,
    private val syncHealth: SyncHealthStore,
    private val sync: SyncRequester,
    network: NetworkStatus
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    fun observeUnreadCount(feedId: Long?): Flow<Int> = articles.observeUnreadCount(feedId)

    fun observeReadCount(feedId: Long?): Flow<Int> = articles.observeReadCount(feedId)

    suspend fun getFeed(feedId: Long): Feed? = articles.getFeed(feedId)

    suspend fun ensureCacheOwner() = cache.ensureCacheOwner()

    fun requestRefresh(): SyncOperationId? = sync.request(SyncIntent.User(userVisible = true))

    fun observeSync(): Flow<SyncOperationStatus> = sync.observeRequestedSync()

    fun observeOperation(operationId: SyncOperationId): Flow<SyncOperationStatus> =
        sync.observeOperation(operationId)

    fun observeHasCompletedSync(): Flow<Boolean> = syncHealth.observe()
        .map { it.lastSuccessfulSyncAt > 0L }
        .distinctUntilChanged()

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
