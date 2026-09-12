package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.application.exception.CursorExpiredException
import com.hiosdra.hreader.core.application.exception.IncompleteSyncException
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
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
    private val preferenceWrites: PreferenceWriteBarrier,
    private val sessionGate: SyncSessionGate,
    private val onNewFullSync: suspend (SyncSession) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val maxPages: Int = MAX_SYNC_PAGES
) {
    init {
        require(maxPages > 0)
    }

    suspend fun run(
        forceFullSync: Boolean,
        onPage: suspend (entries: List<Entry>, fullSyncRunId: String?) -> ArticleSyncStats
    ): ArticleSyncRunResult {
        val session = sessionGate.currentSession()
        checkSession(session)
        val checkpoint = selectRun(forceFullSync, session)
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
            checkSession(session)
            val page = try {
                sessionGate.withSession(session) {
                    fetchArticleBatch(checkpoint, cursor)
                }
            } catch (error: CursorExpiredException) {
                if (cursor == null || cursorRestarted) throw error
                cursorRestarted = true
                cursor = null
                seenCursors.clear()
                if (checkpoint.mode == SyncCheckpointMode.FULL) {
                    sessionGate.withSession(session) {
                        onNewFullSync(session)
                    }
                }
                storeCheckpoint(session, checkpoint.copy(cursor = null))
                continue
            }
            checkSession(session)

            if (page.entries.isNotEmpty()) {
                stats += sessionGate.withSession(session) {
                    onPage(page.entries, checkpoint.fullSyncRunId)
                }
                fetchedFeedIds += page.entries.map { it.feed.id }
                page.entries.groupBy { it.feed.id }.forEach { (feedId, entries) ->
                    latestPublishedAtByFeed[feedId] = entries.maxOf { it.publishedAt.toEpochMilli() }
                }
            }

            pages++
            val nextCursor = page.cursor
            if (nextCursor == null) {
                walkedToTheEnd = true
                clearCheckpoint(session)
                break
            }
            if (!seenCursors.add(nextCursor)) {
                repeatedCursor = true
                clearCheckpoint(session)
                break
            }

            val nextCheckpoint = checkpoint.copy(cursor = nextCursor)
            storeCheckpoint(session, nextCheckpoint)
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

        checkSession(session)
        return ArticleSyncRunResult(
            session = session,
            checkpoint = checkpoint,
            isIncremental = useIncremental,
            stats = stats,
            fetchedFeedIds = fetchedFeedIds,
            latestPublishedAtByFeed = latestPublishedAtByFeed
        )
    }

    private suspend fun selectRun(
        forceFullSync: Boolean,
        session: SyncSession
    ): SyncCheckpoint {
        if (!forceFullSync) {
            preferences.getSyncCheckpoint()
                ?.takeIf { it.ownerKey == session.ownerKey }
                ?.takeIf { it.mode != SyncCheckpointMode.FULL || it.fullSyncRunId != null }
                ?.let { return it }
        }

        val now = clock.millis()
        val incremental = !forceFullSync && shouldUseIncrementalSync(now)
        val checkpoint = SyncCheckpoint(
            ownerKey = session.ownerKey,
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
        if (!incremental) {
            sessionGate.withSession(session) {
                onNewFullSync(session)
            }
        }
        storeCheckpoint(session, checkpoint)
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
        api.getEntriesChangedAfter(changedAfter, cursor = cursor)
    } else {
        api.getUnreadEntries(cursor = cursor)
    }

    private suspend fun storeCheckpoint(session: SyncSession, checkpoint: SyncCheckpoint) {
        sessionGate.withSession(session) {
            preferences.setSyncCheckpoint(checkpoint)
            preferenceWrites.awaitWrites()
        }
    }

    private suspend fun clearCheckpoint(session: SyncSession) {
        sessionGate.withSession(session) {
            preferences.clearSyncCheckpoint()
            preferenceWrites.awaitWrites()
        }
    }

    private fun checkSession(session: SyncSession) {
        if (!sessionGate.isCurrent(session)) throw StaleSyncSessionException()
    }
}

internal data class ArticleSyncRunResult(
    val session: SyncSession,
    val checkpoint: SyncCheckpoint,
    val isIncremental: Boolean,
    val stats: ArticleSyncStats,
    val fetchedFeedIds: Set<Long>,
    val latestPublishedAtByFeed: Map<Long, Long>
)
