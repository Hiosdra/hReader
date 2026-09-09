package com.hiosdra.hreader.core.application.sync

import com.squareup.moshi.JsonClass

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
    PARTIALLY_SUCCESSFUL,
    CANCELLED
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

@JsonClass(generateAdapter = true)
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

@JsonClass(generateAdapter = true)
data class SyncFeedStatus(
    val feedId: Long,
    val lastSuccessfulSyncAt: Long = 0L,
    val lastAttemptedSyncAt: Long = 0L,
    val latestArticlePublishedAt: Long = 0L,
    val latestErrorReason: SyncFailureReason? = null,
    val latestErrorStage: SyncFailureStage? = null
)

@JsonClass(generateAdapter = true)
data class SyncRunSummary(
    val startedAt: Long,
    val completedAt: Long? = null,
    val state: SyncRunState = SyncRunState.RUNNING,
    val updatedFeedIds: List<Long> = emptyList(),
    val failedFeedIds: List<Long> = emptyList(),
    val skippedFeedIds: List<Long> = emptyList(),
    val newArticles: Int = 0,
    val failureStage: SyncFailureStage? = null,
    val failureReason: SyncFailureReason? = null,
    val runId: String = ""
)

@JsonClass(generateAdapter = true)
data class SyncHealthSnapshot(
    val lastSuccessfulSyncAt: Long = 0L,
    val lastAttemptedSyncAt: Long = 0L,
    val lastRun: SyncRunSummary? = null,
    val feeds: List<SyncFeedStatus> = emptyList(),
    val activeRuns: List<SyncRunSummary> = emptyList()
)

fun SyncHealthSnapshot.recordStarted(
    attemptedAt: Long,
    runId: String = ""
): SyncHealthSnapshot {
    val normalizedRunId = runId.normalizedRunId()
    if (normalizedRunId.isEmpty() && lastRun?.runId?.isNotEmpty() == true) return this
    val startedRun = SyncRunSummary(startedAt = attemptedAt, runId = normalizedRunId)
    return copy(
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, attemptedAt),
        lastRun = startedRun,
        activeRuns = if (normalizedRunId.isEmpty()) {
            activeRuns
        } else {
            activeRuns.filterNot { it.runId == normalizedRunId } + startedRun
        }
    )
}

fun SyncHealthSnapshot.recordFinished(
    completedAt: Long,
    result: ArticleSyncResult,
    runId: String = ""
): SyncHealthSnapshot {
    val normalizedRunId = runId.normalizedRunId()
    if (normalizedRunId.isEmpty() && lastRun?.runId?.isNotEmpty() == true) return this
    val runningRun = if (normalizedRunId.isEmpty()) {
        lastRun?.takeIf { it.state == SyncRunState.RUNNING }
    } else {
        activeRuns.firstOrNull { it.runId == normalizedRunId }
            ?: lastRun?.takeIf {
                it.runId == normalizedRunId && it.state == SyncRunState.RUNNING
            }
    }
    if (normalizedRunId.isNotEmpty() && runningRun == null) return this

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
    val attemptedAt = runningRun?.startedAt ?: completedAt
    val nextFeeds = resultFeedIds.sorted().map { feedId ->
        val current = existingFeeds[feedId] ?: SyncFeedStatus(feedId = feedId)
        val isNewerAttempt = attemptedAt >= current.lastAttemptedSyncAt
        when {
            feedId in successfulFeedIds -> current.copy(
                lastSuccessfulSyncAt = maxOf(current.lastSuccessfulSyncAt, completedAt),
                lastAttemptedSyncAt = maxOf(current.lastAttemptedSyncAt, attemptedAt),
                latestArticlePublishedAt = maxOf(
                    current.latestArticlePublishedAt,
                    result.latestPublishedAtByFeed[feedId] ?: 0L
                ),
                latestErrorReason = if (isNewerAttempt) null else current.latestErrorReason,
                latestErrorStage = if (isNewerAttempt) null else current.latestErrorStage
            )
            feedId in result.failedFeedIds -> current.copy(
                lastAttemptedSyncAt = maxOf(current.lastAttemptedSyncAt, attemptedAt),
                latestErrorReason = if (isNewerAttempt) failure?.reason else current.latestErrorReason,
                latestErrorStage = if (isNewerAttempt) failure?.stage else current.latestErrorStage
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
    val completedRun = SyncRunSummary(
        startedAt = attemptedAt,
        completedAt = completedAt,
        state = runState,
        updatedFeedIds = updatedFeedIds.sorted(),
        failedFeedIds = result.failedFeedIds.sorted(),
        skippedFeedIds = result.skippedFeedIds.sorted(),
        newArticles = result.newArticles,
        failureStage = failure?.stage,
        failureReason = failure?.reason,
        runId = normalizedRunId.ifEmpty { runningRun?.runId.orEmpty() }
    )
    val shouldReplaceLastRun = normalizedRunId.isEmpty() ||
        lastRun?.runId == normalizedRunId ||
        (normalizedRunId.isEmpty() && lastRun == runningRun)
    return copy(
        lastSuccessfulSyncAt = if (completeSuccess) {
            maxOf(lastSuccessfulSyncAt, completedAt)
        } else {
            lastSuccessfulSyncAt
        },
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, attemptedAt),
        lastRun = if (shouldReplaceLastRun) completedRun else lastRun,
        feeds = nextFeeds,
        activeRuns = if (normalizedRunId.isEmpty()) {
            activeRuns
        } else {
            activeRuns.filterNot { it.runId == normalizedRunId }
        }
    )
}

fun SyncHealthSnapshot.recordStageFailure(
    completedAt: Long,
    failure: SyncFailure,
    runId: String = ""
): SyncHealthSnapshot {
    val normalizedRunId = runId.normalizedRunId()
    if (normalizedRunId.isEmpty() && lastRun?.runId?.isNotEmpty() == true) return this
    val previousRun = if (normalizedRunId.isEmpty()) {
        lastRun
    } else {
        activeRuns.firstOrNull { it.runId == normalizedRunId }
            ?: lastRun?.takeIf { it.runId == normalizedRunId }
    }
    if (normalizedRunId.isNotEmpty() && previousRun == null) return this

    val hasSuccessfulWork = lastSuccessfulSyncAt > 0L ||
        previousRun?.updatedFeedIds?.isNotEmpty() == true
    val attemptedAt = previousRun?.startedAt
        ?: lastAttemptedSyncAt.takeIf { it > 0L }
        ?: completedAt
    val failedRun = (previousRun ?: SyncRunSummary(startedAt = completedAt)).copy(
        completedAt = completedAt,
        state = if (hasSuccessfulWork) {
            SyncRunState.PARTIALLY_SUCCESSFUL
        } else {
            SyncRunState.FAILED
        },
        failureStage = failure.stage,
        failureReason = failure.reason,
        runId = normalizedRunId.ifEmpty { previousRun?.runId.orEmpty() }
    )
    return copy(
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, attemptedAt),
        lastRun = if (normalizedRunId.isEmpty() || lastRun?.runId == normalizedRunId) {
            failedRun
        } else {
            lastRun
        },
        activeRuns = if (normalizedRunId.isEmpty()) {
            activeRuns
        } else {
            activeRuns.filterNot { it.runId == normalizedRunId }
        }
    )
}

fun SyncHealthSnapshot.recordCancelled(
    completedAt: Long,
    runId: String = ""
): SyncHealthSnapshot {
    val normalizedRunId = runId.normalizedRunId()
    if (normalizedRunId.isEmpty() && lastRun?.runId?.isNotEmpty() == true) return this
    val previousRun = if (normalizedRunId.isEmpty()) {
        lastRun?.takeIf { it.state == SyncRunState.RUNNING }
    } else {
        activeRuns.firstOrNull { it.runId == normalizedRunId }
            ?: lastRun?.takeIf {
                it.runId == normalizedRunId && it.state == SyncRunState.RUNNING
            }
    }
    if (normalizedRunId.isNotEmpty() && previousRun == null) return this

    val cancelledRun = (previousRun ?: SyncRunSummary(startedAt = completedAt)).copy(
        completedAt = completedAt,
        state = SyncRunState.CANCELLED,
        runId = normalizedRunId.ifEmpty { previousRun?.runId.orEmpty() }
    )
    return copy(
        lastAttemptedSyncAt = maxOf(lastAttemptedSyncAt, cancelledRun.startedAt),
        lastRun = if (normalizedRunId.isEmpty() || lastRun?.runId == normalizedRunId) {
            cancelledRun
        } else {
            lastRun
        },
        activeRuns = if (normalizedRunId.isEmpty()) {
            activeRuns
        } else {
            activeRuns.filterNot { it.runId == normalizedRunId }
        }
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
        SyncRunState.CANCELLED -> return if (lastSuccessfulSyncAt > 0L) {
            SyncFreshnessState.PARTIALLY_SUCCESSFUL
        } else {
            SyncFreshnessState.FAILED
        }
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

private fun String.normalizedRunId(): String = trim()

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
