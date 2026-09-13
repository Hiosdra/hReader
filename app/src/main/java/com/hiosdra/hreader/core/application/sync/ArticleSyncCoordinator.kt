package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.application.exception.CursorExpiredException
import com.hiosdra.hreader.core.application.exception.IncompleteSyncException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.domain.model.Entry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val MAX_SYNC_PAGES = 200
private val INCREMENTAL_SYNC_WINDOW: Duration = Duration.ofHours(24)
private val FULL_SYNC_INTERVAL: Duration = Duration.ofDays(7)
private val INCREMENTAL_SYNC_OVERLAP: Duration = Duration.ofMinutes(5)

internal class ArticleSyncCoordinator(
    private val api: FeedBackend,
    private val preferences: SyncPreferences,
    private val onNewFullSync: suspend () -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val maxPages: Int = MAX_SYNC_PAGES
) {
    init {
        require(maxPages > 0)
    }

    suspend fun run(
        forceFullSync: Boolean,
        ownerKey: String,
        onPage: suspend (entries: List<Entry>, fullSyncRunId: String?) -> ArticleSyncStats
    ): ArticleSyncRunResult {
        val checkpoint = selectRun(forceFullSync, ownerKey)
        val useIncremental = checkpoint.mode == SyncCheckpointMode.INCREMENTAL
        var stats = ArticleSyncStats()
        val fetchedFeedIds = mutableSetOf<Long>()
        val latestPublishedAtByFeed = mutableMapOf<Long, Long>()
        var cursor = checkpoint.cursor
        val seenCursors = mutableSetOf<String>().apply { cursor?.let(::add) }
        var cursorRestarted = false
        var walkedToTheEnd = false
        var repeatedCursor = false
        var pages = 0

        while (true) {
            val page = try {
                fetchArticleBatch(checkpoint, cursor)
            } catch (error: CursorExpiredException) {
                if (cursor == null || cursorRestarted) throw error
                cursorRestarted = true
                cursor = null
                seenCursors.clear()
                if (checkpoint.mode == SyncCheckpointMode.FULL) onNewFullSync()
                storeCheckpoint(checkpoint.copy(cursor = null))
                continue
            }

            if (page.entries.isNotEmpty()) {
                stats += onPage(page.entries, checkpoint.fullSyncRunId)
                fetchedFeedIds += page.entries.map { it.feed.id }
                page.entries.groupBy { it.feed.id }.forEach { (feedId, entries) ->
                    latestPublishedAtByFeed[feedId] = entries.maxOf { it.publishedAt.toEpochMilli() }
                }
            }

            pages++
            val nextCursor = page.cursor
            if (nextCursor == null || page.entries.isEmpty()) {
                walkedToTheEnd = true
                clearCheckpoint()
                break
            }
            if (!seenCursors.add(nextCursor)) {
                repeatedCursor = true
                clearCheckpoint()
                break
            }

            storeCheckpoint(checkpoint.copy(cursor = nextCursor))
            cursor = nextCursor
            if (pages >= maxPages) break
        }

        if (!walkedToTheEnd) {
            val reason = if (repeatedCursor) {
                "Article sync encountered a repeated cursor"
            } else {
                "Article sync reached its page limit"
            }
            throw IncompleteSyncException(reason)
        }

        return ArticleSyncRunResult(
            checkpoint = checkpoint,
            isIncremental = useIncremental,
            stats = stats,
            fetchedFeedIds = fetchedFeedIds,
            latestPublishedAtByFeed = latestPublishedAtByFeed
        )
    }

    private suspend fun selectRun(forceFullSync: Boolean, ownerKey: String): SyncCheckpoint {
        if (!forceFullSync) {
            preferences.getSyncCheckpoint()
                ?.takeIf { it.ownerKey == ownerKey }
                ?.takeIf { it.mode != SyncCheckpointMode.FULL || it.fullSyncRunId != null }
                ?.let { return it }
        }

        val now = clock.millis()
        val incremental = !forceFullSync && shouldUseIncrementalSync(now)
        val checkpoint = SyncCheckpoint(
            ownerKey = ownerKey,
            mode = if (incremental) SyncCheckpointMode.INCREMENTAL else SyncCheckpointMode.FULL,
            startedAt = now,
            changedAfter = if (incremental) {
                Instant.ofEpochMilli(preferences.getLastSyncTimestamp())
                    .minus(INCREMENTAL_SYNC_OVERLAP)
                    .toEpochMilli()
            } else {
                null
            },
            cursor = null,
            fullSyncRunId = if (incremental) null else UUID.randomUUID().toString()
        )
        if (!incremental) onNewFullSync()
        storeCheckpoint(checkpoint)
        return checkpoint
    }

    private fun shouldUseIncrementalSync(syncStartTime: Long): Boolean {
        val lastSync = preferences.getLastSyncTimestamp()
        if (lastSync <= 0L) return false
        val lastFull = preferences.getLastFullSyncTimestamp()
        if (lastFull <= 0L || syncStartTime - lastFull >= FULL_SYNC_INTERVAL.toMillis()) return false
        return syncStartTime - lastSync < INCREMENTAL_SYNC_WINDOW.toMillis()
    }

    private suspend fun fetchArticleBatch(
        checkpoint: SyncCheckpoint,
        cursor: String?
    ): EntriesPage = if (checkpoint.mode == SyncCheckpointMode.INCREMENTAL) {
        val changedAfter = Instant.ofEpochMilli(requireNotNull(checkpoint.changedAfter))
        api.getEntriesChangedAfter(changedAfter, ENTRIES_PAGE_LIMIT, cursor)
    } else {
        api.getUnreadEntries(ENTRIES_PAGE_LIMIT, cursor)
    }

    private fun storeCheckpoint(checkpoint: SyncCheckpoint) {
        preferences.setSyncCheckpoint(checkpoint)
    }

    private fun clearCheckpoint() {
        preferences.clearSyncCheckpoint()
    }
}

internal data class ArticleSyncRunResult(
    val checkpoint: SyncCheckpoint,
    val isIncremental: Boolean,
    val stats: ArticleSyncStats,
    val fetchedFeedIds: Set<Long>,
    val latestPublishedAtByFeed: Map<Long, Long>
)
