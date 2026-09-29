package com.hiosdra.hreader.presentation.main

import com.hiosdra.hreader.core.application.sync.OfflinePreparationProgress
import com.hiosdra.hreader.core.application.sync.SyncOperationState
import com.hiosdra.hreader.core.application.port.out.BulkReadMarker
import com.hiosdra.hreader.presentation.text.UiText

data class UndoableAction(
    val id: Long,
    val message: UiText,
    val marker: BulkReadMarker
)

data class MainUiState(
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val feedTitle: String? = null,
    val searchQuery: String = "",
    val unavailableAiModelId: String? = null,
    val isOnline: Boolean = true,
    val showReadArticles: Boolean = false,
    val unreadCount: Int = 0,
    val readCount: Int = 0,
    val syncState: SyncOperationState = SyncOperationState.IDLE,
    val hasCompletedSync: Boolean = false,
    val offlinePreparation: OfflinePreparationProgress = OfflinePreparationProgress(),
    val isBulkReadStateUpdating: Boolean = false,
    val undo: UndoableAction? = null
)
