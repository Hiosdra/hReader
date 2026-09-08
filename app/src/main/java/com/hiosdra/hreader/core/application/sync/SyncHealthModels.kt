package com.hiosdra.hreader.core.application.sync

enum class SyncFreshnessState {
    NEVER_SYNCED,
    SYNCING,
    UP_TO_DATE,
    STALE,
    FAILED,
    PARTIALLY_SUCCESSFUL,
    OFFLINE
}

enum class SyncRunState {
    RUNNING,
    SUCCEEDED,
    FAILED,
    PARTIALLY_SUCCESSFUL
}

enum class SyncFailureStage {
    ARTICLE_SYNC,
    FEED_SYNC,
    ARTICLE_CONTENT,
    FULL_PAGE
}

enum class SyncFailureReason {
    NETWORK,
    SERVER,
    TIMEOUT,
    CONFIGURATION,
    UNKNOWN
}

data class SyncFailure(
    val stage: SyncFailureStage,
    val reason: SyncFailureReason,
    val retryable: Boolean
)

data class ArticleSyncResult(
    val activeFeedIds: Set<Long>? = null,
    val successfulFeedIds: Set<Long> = emptySet(),
    val updatedFeedIds: Set<Long> = emptySet(),
    val failedFeedIds: Set<Long> = emptySet(),
    val skippedFeedIds: Set<Long> = emptySet(),
    val newArticles: Int = 0,
    val latestPublishedAtByFeed: Map<Long, Long> = emptyMap(),
    val isPartial: Boolean = false,
    val failure: SyncFailure? = null
)

data class SyncFeedStatus(
    val feedId: Long,
    val lastSuccessfulSyncAt: Long = 0L,
    val lastAttemptedSyncAt: Long = 0L,
    val latestArticlePublishedAt: Long = 0L,
    val latestErrorReason: SyncFailureReason? = null,
    val latestErrorStage: SyncFailureStage? = null
)

data class SyncRunSummary(
    val startedAt: Long,
    val completedAt: Long? = null,
    val state: SyncRunState = SyncRunState.RUNNING,
    val updatedFeedIds: List<Long> = emptyList(),
    val failedFeedIds: List<Long> = emptyList(),
    val skippedFeedIds: List<Long> = emptyList(),
    val newArticles: Int = 0,
    val failureStage: SyncFailureStage? = null,
    val failureReason: SyncFailureReason? = null
)

data class SyncHealthSnapshot(
    val lastSuccessfulSyncAt: Long = 0L,
    val lastAttemptedSyncAt: Long = 0L,
    val lastRun: SyncRunSummary? = null,
    val feeds: List<SyncFeedStatus> = emptyList()
)

fun SyncHealthSnapshot.recordStarted(attemptedAt: Long): SyncHealthSnapshot = copy(
    lastAttemptedSyncAt = attemptedAt,
    lastRun = SyncRunSummary(startedAt = attemptedAt)
)

fun SyncHealthSnapshot.recordFinished(
    completedAt: Long,
    result: ArticleSyncResult
): SyncHealthSnapshot {
    val existingFeeds = feeds.associateBy { it.feedId }
    val resultFeedIds = result.activeFeedIds ?: (
        existingFeeds.keys + result.successfulFeedIds + result.updatedFeedIds +
            result.failedFeedIds + result.skippedFeedIds
        ).toSet()
    val successfulFeedIds = result.successfulFeedIds + result.updatedFeedIds
    val updatedFeedIds = result.updatedFeedIds
    val failure = result.failure ?: if (result.isPartial || result.failedFeedIds.isNotEmpty()) {
        SyncFailure(
            stage = SyncFailureStage.ARTICLE_SYNC,
            reason = SyncFailureReason.UNKNOWN,
            retryable = false
        )
    } else {
        null
    }
    val attemptedAt = if (lastRun?.state == SyncRunState.RUNNING) {
        lastRun.startedAt
    } else {
        completedAt
    }
    val nextFeeds = resultFeedIds.sorted().map { feedId ->
        val current = existingFeeds[feedId] ?: SyncFeedStatus(feedId = feedId)
        when {
            feedId in successfulFeedIds -> current.copy(
                lastSuccessfulSyncAt = completedAt,
                lastAttemptedSyncAt = maxOf(current.lastAttemptedSyncAt, attemptedAt),
                latestArticlePublishedAt = maxOf(
                    current.latestArticlePublishedAt,
                    result.latestPublishedAtByFeed[feedId] ?: 0L
                ),
                latestErrorReason = null,
                latestErrorStage = null
            )
            feedId in result.failedFeedIds -> current.copy(
                lastAttemptedSyncAt = maxOf(current.lastAttemptedSyncAt, attemptedAt),
                latestErrorReason = failure?.reason,
                latestErrorStage = failure?.stage
            )
            else -> current.copy(
                lastAttemptedSyncAt = current.lastAttemptedSyncAt
            )
        }
    }
    val runState = when {
        result.isPartial -> SyncRunState.PARTIALLY_SUCCESSFUL
        failure == null -> SyncRunState.SUCCEEDED
        successfulFeedIds.isNotEmpty() || updatedFeedIds.isNotEmpty() -> SyncRunState.PARTIALLY_SUCCESSFUL
        else -> SyncRunState.FAILED
    }
    val completeSuccess = failure == null && !result.isPartial
    return copy(
        lastSuccessfulSyncAt = if (completeSuccess) completedAt else lastSuccessfulSyncAt,
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, attemptedAt),
        lastRun = SyncRunSummary(
            startedAt = attemptedAt,
            completedAt = completedAt,
            state = runState,
            updatedFeedIds = updatedFeedIds.sorted(),
            failedFeedIds = result.failedFeedIds.sorted(),
            skippedFeedIds = result.skippedFeedIds.sorted(),
            newArticles = result.newArticles,
            failureStage = failure?.stage,
            failureReason = failure?.reason
        ),
        feeds = nextFeeds
    )
}

fun SyncHealthSnapshot.recordStageFailure(
    completedAt: Long,
    failure: SyncFailure
): SyncHealthSnapshot {
    val previousRun = lastRun
    val hasSuccessfulWork = lastSuccessfulSyncAt > 0L ||
        previousRun?.updatedFeedIds?.isNotEmpty() == true
    val attemptedAt = lastAttemptedSyncAt.takeIf { it > 0L } ?: completedAt
    return copy(
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, attemptedAt),
        lastRun = (previousRun ?: SyncRunSummary(startedAt = completedAt)).copy(
            completedAt = completedAt,
            state = if (hasSuccessfulWork) {
                SyncRunState.PARTIALLY_SUCCESSFUL
            } else {
                SyncRunState.FAILED
            },
            failureStage = failure.stage,
            failureReason = failure.reason
        )
    )
}

fun SyncHealthSnapshot.resolveFreshness(
    now: Long,
    isOnline: Boolean,
    isSyncing: Boolean,
    intervalMinutes: Int
): SyncFreshnessState {
    if (!isOnline) return SyncFreshnessState.OFFLINE
    if (isSyncing) return SyncFreshnessState.SYNCING
    when (lastRun?.state) {
        SyncRunState.RUNNING -> return if (lastSuccessfulSyncAt > 0L) {
            SyncFreshnessState.PARTIALLY_SUCCESSFUL
        } else {
            SyncFreshnessState.FAILED
        }
        SyncRunState.FAILED -> return SyncFreshnessState.FAILED
        SyncRunState.PARTIALLY_SUCCESSFUL -> return SyncFreshnessState.PARTIALLY_SUCCESSFUL
        SyncRunState.SUCCEEDED,
        null -> Unit
    }
    if (lastSuccessfulSyncAt <= 0L) return SyncFreshnessState.NEVER_SYNCED
    return if (now - lastSuccessfulSyncAt >= intervalMinutes.coerceAtLeast(1) * 60_000L) {
        SyncFreshnessState.STALE
    } else {
        SyncFreshnessState.UP_TO_DATE
    }
}

fun SyncFeedStatus.resolveFreshness(
    now: Long,
    isOnline: Boolean,
    isSyncing: Boolean,
    intervalMinutes: Int
): SyncFreshnessState {
    if (!isOnline) return SyncFreshnessState.OFFLINE
    if (isSyncing) return SyncFreshnessState.SYNCING
    if (latestErrorReason != null) return SyncFreshnessState.FAILED
    if (lastSuccessfulSyncAt <= 0L) return SyncFreshnessState.NEVER_SYNCED
    return if (now - lastSuccessfulSyncAt >= intervalMinutes.coerceAtLeast(1) * 60_000L) {
        SyncFreshnessState.STALE
    } else {
        SyncFreshnessState.UP_TO_DATE
    }
}
