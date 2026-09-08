package com.hiosdra.hreader.core.application.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHealthModelsTest {
    @Test
    fun `successful run records cache refresh and distinguishes unchanged feeds`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 2_000L,
                result = ArticleSyncResult(
                    activeFeedIds = setOf(10L, 20L),
                    successfulFeedIds = setOf(10L, 20L),
                    updatedFeedIds = setOf(10L),
                    skippedFeedIds = setOf(20L),
                    newArticles = 3,
                    latestPublishedAtByFeed = mapOf(10L to 1_900L)
                )
            )

        assertEquals(2_000L, snapshot.lastSuccessfulSyncAt)
        assertEquals(1_000L, snapshot.lastAttemptedSyncAt)
        assertEquals(SyncRunState.SUCCEEDED, snapshot.lastRun?.state)
        assertEquals(listOf(10L), snapshot.lastRun?.updatedFeedIds)
        assertEquals(listOf(20L), snapshot.lastRun?.skippedFeedIds)
        assertEquals(1_900L, snapshot.feeds.first { it.feedId == 10L }.latestArticlePublishedAt)
        assertEquals(
            SyncFreshnessState.UP_TO_DATE,
            snapshot.resolveFreshness(2_000L + 59 * 60_000L, true, false, 60)
        )
    }

    @Test
    fun `partial run keeps previous global success and identifies failed feed`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 1_100L,
                result = ArticleSyncResult(
                    activeFeedIds = setOf(10L, 20L),
                    successfulFeedIds = setOf(10L, 20L)
                )
            )
            .recordStarted(2_000L)
            .recordFinished(
                completedAt = 2_100L,
                result = ArticleSyncResult(
                    successfulFeedIds = setOf(10L),
                    updatedFeedIds = setOf(10L),
                    failedFeedIds = setOf(20L),
                    failure = SyncFailure(
                        stage = SyncFailureStage.ARTICLE_SYNC,
                        reason = SyncFailureReason.NETWORK,
                        retryable = true
                    )
                )
            )

        val failedFeed = snapshot.feeds.first { it.feedId == 20L }
        assertEquals(1_100L, snapshot.lastSuccessfulSyncAt)
        assertEquals(2_000L, snapshot.lastAttemptedSyncAt)
        assertEquals(SyncRunState.PARTIALLY_SUCCESSFUL, snapshot.lastRun?.state)
        assertEquals(listOf(20L), snapshot.lastRun?.failedFeedIds)
        assertEquals(SyncFailureReason.NETWORK, failedFeed.latestErrorReason)
        assertEquals(
            SyncFreshnessState.PARTIALLY_SUCCESSFUL,
            snapshot.resolveFreshness(2_100L, true, false, 60)
        )
    }

    @Test
    fun `offline is reported even when local data is recently cached`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 2_000L,
                result = ArticleSyncResult(activeFeedIds = setOf(10L), successfulFeedIds = setOf(10L))
            )

        assertEquals(
            SyncFreshnessState.OFFLINE,
            snapshot.resolveFreshness(2_000L, false, false, 60)
        )
        assertTrue(snapshot.lastSuccessfulSyncAt > 0L)
    }

    @Test
    fun `old complete run becomes stale at the configured interval`() {
        val snapshot = SyncHealthSnapshot(lastSuccessfulSyncAt = 1_000L)

        assertEquals(
            SyncFreshnessState.STALE,
            snapshot.resolveFreshness(1_000L + 60 * 60_000L, true, false, 60)
        )
    }

    @Test
    fun `stage failure makes a completed run partial without erasing cache timestamp`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 2_000L,
                result = ArticleSyncResult(activeFeedIds = setOf(10L), successfulFeedIds = setOf(10L))
            )
            .recordStageFailure(
                completedAt = 2_500L,
                failure = SyncFailure(
                    stage = SyncFailureStage.ARTICLE_CONTENT,
                    reason = SyncFailureReason.TIMEOUT,
                    retryable = false
                )
            )

        assertEquals(2_000L, snapshot.lastSuccessfulSyncAt)
        assertEquals(1_000L, snapshot.lastAttemptedSyncAt)
        assertEquals(SyncRunState.PARTIALLY_SUCCESSFUL, snapshot.lastRun?.state)
        assertEquals(SyncFailureStage.ARTICLE_CONTENT, snapshot.lastRun?.failureStage)
        assertEquals(SyncFreshnessState.PARTIALLY_SUCCESSFUL, snapshot.resolveFreshness(2_500L, true, false, 60))
    }

    @Test
    fun `incomplete pagination identifies affected feeds and its stage`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 2_000L,
                result = ArticleSyncResult(
                    activeFeedIds = setOf(10L, 20L),
                    successfulFeedIds = setOf(10L),
                    updatedFeedIds = setOf(10L),
                    failedFeedIds = setOf(20L),
                    isPartial = true
                )
            )

        val failedFeed = snapshot.feeds.first { it.feedId == 20L }
        assertEquals(SyncRunState.PARTIALLY_SUCCESSFUL, snapshot.lastRun?.state)
        assertEquals(SyncFailureStage.ARTICLE_SYNC, snapshot.lastRun?.failureStage)
        assertEquals(SyncFailureReason.UNKNOWN, failedFeed.latestErrorReason)
        assertEquals(
            SyncFreshnessState.FAILED,
            failedFeed.resolveFreshness(2_000L, true, false, 60)
        )
    }

    @Test
    fun `partial run keeps feeds not reached by pagination unchanged`() {
        val snapshot = SyncHealthSnapshot(
            feeds = listOf(
                SyncFeedStatus(
                    feedId = 20L,
                    lastSuccessfulSyncAt = 500L,
                    lastAttemptedSyncAt = 600L,
                    latestArticlePublishedAt = 400L
                )
            )
        ).recordStarted(1_000L).recordFinished(
            completedAt = 2_000L,
            result = ArticleSyncResult(
                activeFeedIds = setOf(10L, 20L),
                successfulFeedIds = setOf(10L),
                updatedFeedIds = setOf(10L),
                skippedFeedIds = setOf(20L),
                isPartial = true
            )
        )

        val skippedFeed = snapshot.feeds.first { it.feedId == 20L }
        assertEquals(500L, skippedFeed.lastSuccessfulSyncAt)
        assertEquals(600L, skippedFeed.lastAttemptedSyncAt)
        assertEquals(null, skippedFeed.latestErrorReason)
        assertEquals(listOf(20L), snapshot.lastRun?.skippedFeedIds)
    }

    @Test
    fun `partial run stays partial when no feed was completed`() {
        val snapshot = SyncHealthSnapshot()
            .recordStarted(1_000L)
            .recordFinished(
                completedAt = 2_000L,
                result = ArticleSyncResult(
                    activeFeedIds = setOf(10L),
                    skippedFeedIds = setOf(10L),
                    isPartial = true
                )
            )

        assertEquals(SyncRunState.PARTIALLY_SUCCESSFUL, snapshot.lastRun?.state)
        assertEquals(
            SyncFreshnessState.PARTIALLY_SUCCESSFUL,
            snapshot.resolveFreshness(2_000L, true, false, 60)
        )
    }

    @Test
    fun `persisted running state resolves after the process stops`() {
        val withPreviousSuccess = SyncHealthSnapshot(
            lastSuccessfulSyncAt = 1_000L,
            lastRun = SyncRunSummary(startedAt = 2_000L)
        )
        val withoutPreviousSuccess = SyncHealthSnapshot(
            lastRun = SyncRunSummary(startedAt = 2_000L)
        )

        assertEquals(
            SyncFreshnessState.PARTIALLY_SUCCESSFUL,
            withPreviousSuccess.resolveFreshness(3_000L, true, false, 60)
        )
        assertEquals(
            SyncFreshnessState.FAILED,
            withoutPreviousSuccess.resolveFreshness(3_000L, true, false, 60)
        )
    }
}
