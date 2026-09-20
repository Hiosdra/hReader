package com.hiosdra.hreader.core.application.usecase.main

import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainReaderUseCaseTest {
    @Test
    fun `completed sync follows the persisted successful timestamp`() = runTest {
        val snapshots = MutableStateFlow(SyncHealthSnapshot())
        val syncHealth = mockk<SyncHealthStore>()
        val network = mockk<NetworkStatus>()
        every { syncHealth.observe() } returns snapshots
        every { network.isOnline } returns MutableStateFlow(true)

        val reader = MainReaderUseCase(
            articles = mockk<ArticleQueryStore>(relaxed = true),
            articleMutations = mockk<ArticleMutationStore>(relaxed = true),
            cache = mockk<CacheStore>(relaxed = true),
            aiModels = mockk<AiModelCatalog>(relaxed = true),
            syncHealth = syncHealth,
            sync = mockk<SyncRequester>(relaxed = true),
            network = network
        )
        val values = mutableListOf<Boolean>()
        val collection = launch {
            reader.observeHasCompletedSync().take(2).toList(values)
        }

        runCurrent()
        snapshots.value = SyncHealthSnapshot(lastSuccessfulSyncAt = 1L)
        collection.join()

        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `operation observation is scoped to the requested work`() = runTest {
        val operationId = SyncOperationId("primary")
        val sync = mockk<SyncRequester>()
        every { sync.observeOperation(operationId) } returns flowOf(
            SyncOperationStatus(
                state = SyncOperationState.SUCCEEDED,
                operationIds = setOf(operationId)
            )
        )

        val reader = MainReaderUseCase(
            articles = mockk<ArticleQueryStore>(relaxed = true),
            articleMutations = mockk<ArticleMutationStore>(relaxed = true),
            cache = mockk<CacheStore>(relaxed = true),
            aiModels = mockk<AiModelCatalog>(relaxed = true),
            syncHealth = mockk<SyncHealthStore>(relaxed = true),
            sync = sync,
            network = mockk<NetworkStatus> {
                every { isOnline } returns MutableStateFlow(true)
            }
        )

        assertEquals(
            SyncOperationState.SUCCEEDED,
            reader.observeOperation(operationId).first().state
        )
    }
}
