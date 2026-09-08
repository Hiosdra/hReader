package com.hiosdra.hreader.presentation.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiosdra.hreader.core.application.sync.SyncFailureReason
import com.hiosdra.hreader.core.application.sync.SyncFailureStage
import com.hiosdra.hreader.core.application.sync.SyncFeedStatus
import com.hiosdra.hreader.core.application.sync.SyncFreshnessState
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import com.hiosdra.hreader.core.application.sync.resolveFreshness
import com.hiosdra.hreader.core.application.usecase.sync.SyncHealthUseCase
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock

private const val TAG = "SyncHealthViewModel"

data class SyncHealthFeedUiState(
    val feed: Feed,
    val freshness: SyncFreshnessState,
    val lastSuccessfulSyncAt: Long,
    val lastAttemptedSyncAt: Long,
    val latestArticlePublishedAt: Long,
    val latestErrorReason: SyncFailureReason?,
    val latestErrorStage: SyncFailureStage?
)

data class SyncHealthUiState(
    val freshness: SyncFreshnessState = SyncFreshnessState.NEVER_SYNCED,
    val snapshot: SyncHealthSnapshot = SyncHealthSnapshot(),
    val feeds: List<SyncHealthFeedUiState> = emptyList(),
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val nextScheduledSyncAt: Long? = null,
    val intervalMinutes: Int = 60,
    val now: Long = 0L
)

private data class SyncHealthInputs(
    val snapshot: SyncHealthSnapshot,
    val feeds: List<Feed>,
    val isOnline: Boolean,
    val isSyncing: Boolean,
    val nextScheduledSyncAt: Long?
)

class SyncHealthViewModel(
    private val health: SyncHealthUseCase,
    private val clock: Clock
) : ViewModel() {
    private val snapshot = MutableStateFlow(SyncHealthSnapshot())
    private val feeds = MutableStateFlow<List<Feed>>(emptyList())
    private val isSyncing = MutableStateFlow(false)
    private val nextScheduledSyncAt = MutableStateFlow<Long?>(null)
    private val now = MutableStateFlow(clock.millis())

    private val inputs = combine(
        snapshot,
        feeds,
        health.isOnline,
        isSyncing
    ) { currentSnapshot, cachedFeeds, online, syncing ->
        SyncHealthInputs(currentSnapshot, cachedFeeds, online, syncing, null)
    }.combine(nextScheduledSyncAt) { current, nextSync ->
        current.copy(nextScheduledSyncAt = nextSync)
    }

    val uiState: StateFlow<SyncHealthUiState> = inputs.combine(now) { current, currentTime ->
        val intervalMinutes = health.getSyncIntervalMinutes()
        SyncHealthUiState(
            freshness = current.snapshot.resolveFreshness(
                now = currentTime,
                isOnline = current.isOnline,
                isSyncing = current.isSyncing,
                intervalMinutes = intervalMinutes
            ),
            snapshot = current.snapshot,
            feeds = current.feeds.map { feed ->
                val status = current.snapshot.feeds.firstOrNull { it.feedId == feed.id }
                    ?: SyncFeedStatus(feedId = feed.id)
                SyncHealthFeedUiState(
                    feed = feed,
                    freshness = status.resolveFreshness(
                        now = currentTime,
                        isOnline = current.isOnline,
                        isSyncing = current.isSyncing,
                        intervalMinutes = intervalMinutes
                    ),
                    lastSuccessfulSyncAt = status.lastSuccessfulSyncAt,
                    lastAttemptedSyncAt = status.lastAttemptedSyncAt,
                    latestArticlePublishedAt = status.latestArticlePublishedAt,
                    latestErrorReason = status.latestErrorReason,
                    latestErrorStage = status.latestErrorStage
                )
            },
            isOnline = current.isOnline,
            isSyncing = current.isSyncing,
            nextScheduledSyncAt = current.nextScheduledSyncAt,
            intervalMinutes = intervalMinutes,
            now = currentTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncHealthUiState(now = clock.millis())
    )

    init {
        viewModelScope.launch {
            health.observeSnapshot().collect { currentSnapshot ->
                snapshot.value = currentSnapshot
                loadCachedFeeds()
            }
        }
        viewModelScope.launch {
            health.observeSyncActivity().collect { syncing ->
                isSyncing.value = syncing
            }
        }
        viewModelScope.launch {
            health.observeNextScheduledSync().collect { nextSync ->
                nextScheduledSyncAt.value = nextSync
            }
        }
        viewModelScope.launch {
            health.isOnline.collect { online ->
                if (online) loadCachedFeeds()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                now.value = clock.millis()
                delay(60_000L)
            }
        }
    }

    fun retry() {
        if (!health.isOnline.value || isSyncing.value) return
        health.retry()
    }

    fun retry(feedId: Long) {
        if (uiState.value.feeds.none {
                it.feed.id == feedId && it.freshness == SyncFreshnessState.FAILED
            }
        ) {
            return
        }
        retry()
    }

    private suspend fun loadCachedFeeds() {
        try {
            feeds.value = health.getCachedFeeds()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not load cached feeds for sync health", error)
        }
    }
}
