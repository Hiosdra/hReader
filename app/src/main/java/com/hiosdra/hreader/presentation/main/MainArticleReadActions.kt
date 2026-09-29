package com.hiosdra.hreader.presentation.main

import android.util.Log
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.usecase.main.MainReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MainArticleReadActions(
    private val reader: MainReaderUseCase,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MainUiState>
) {
    fun updateEntryReadStatus(entryId: Long, checked: Boolean) {
        applyReadStatus(listOf(entryId), read = checked)
    }

    fun markAllAsRead(feedId: Long?, onMarkedAsRead: (Long) -> Unit) {
        if (uiState.value.isBulkReadStateUpdating) return
        uiState.update { it.copy(isBulkReadStateUpdating = true) }
        scope.launch {
            try {
                val marker = runCatchingCancellable { reader.markUnreadAsRead(feedId) }
                    .getOrElse {
                        Log.w(TAG, "Could not mark unread articles as read", it)
                        return@launch
                    }
                if (marker.count == 0) return@launch
                uiState.update {
                    it.copy(
                        undo = UndoableAction(
                            id = System.currentTimeMillis(),
                            message = UiText.Plural(
                                id = R.plurals.main_marked_articles_read,
                                count = marker.count,
                                args = listOf(marker.count)
                            ),
                            marker = marker
                        )
                    )
                }
                feedId?.let(onMarkedAsRead)
            } finally {
                uiState.update { it.copy(isBulkReadStateUpdating = false) }
            }
        }
    }

    fun undoLastAction() {
        val action = uiState.value.undo ?: return
        uiState.update { it.copy(undo = null) }
        scope.launch {
            runCatchingCancellable { reader.undoBulkRead(action.marker) }
                .onFailure { Log.w(TAG, "Could not undo bulk read state", it) }
        }
    }

    fun dismissUndo(actionId: Long? = null) {
        uiState.update { state ->
            if (actionId == null || state.undo?.id == actionId) state.copy(undo = null) else state
        }
    }

    private fun applyReadStatus(entryIds: List<Long>, read: Boolean) {
        scope.launch {
            runCatchingCancellable {
                reader.updateReadStatus(entryIds, read)
            }.onFailure {
                Log.w(TAG, "Could not store read state for ${entryIds.size} articles", it)
            }
        }
    }

    private companion object {
        const val TAG = "MainArticleReadActions"
    }
}
