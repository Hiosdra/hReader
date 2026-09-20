package com.hiosdra.hreader.presentation.main

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationId
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.sync.SyncOperationStatus
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshStopsWhenPrimarySyncCompletesBeforeTheDownstreamPipeline() = runTest {
        val operationId = SyncOperationId("primary")
        val pipeline = MutableStateFlow(SyncOperationStatus(SyncOperationState.RUNNING))
        val primary = MutableStateFlow(
            SyncOperationStatus(
                state = SyncOperationState.RUNNING,
                operationIds = setOf(operationId)
            )
        )
        val reader = mockk<MainReaderUseCase>()
        every { reader.isOnline } returns MutableStateFlow(true)
        every { reader.observeSync() } returns pipeline
        every { reader.observeOperation(operationId) } returns primary
        every { reader.observeHasCompletedSync() } returns flowOf(true)
        every { reader.observeOfflinePreparation() } returns flowOf(OfflinePreparationProgress())
        every { reader.observeUnreadCount(any()) } returns flowOf(0)
        every { reader.observeReadCount(any()) } returns flowOf(0)
        every { reader.requestRefresh() } returns operationId
        coEvery { reader.ensureCacheOwner() } returns true
        coEvery { reader.getFeed(any()) } returns null
        coEvery { reader.checkSelectedAiModel() } returns SelectedModelStatus.Available

        val viewModel = MainViewModel(
            reader = reader,
            articlePaging = ArticlePagingProvider { flowOf(PagingData.empty()) },
            savedStateHandle = SavedStateHandle()
        )

        viewModel.refreshFromNetwork()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        primary.value = SyncOperationStatus(
            state = SyncOperationState.SUCCEEDED,
            operationIds = setOf(operationId)
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}
