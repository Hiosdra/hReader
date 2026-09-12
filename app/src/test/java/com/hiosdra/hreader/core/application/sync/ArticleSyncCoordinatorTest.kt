package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.application.exception.CursorExpiredException
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArticleSyncCoordinatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-12T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `checkpoint resumes a sync after more than the page limit`() = runBlocking {
        val preferences = preferences()
        val api = mockk<FeedBackend>()
        val calls = mutableListOf<String?>()
        val totalEntries = ENTRIES_PER_PAGE * 201
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            val cursor = secondArg<String?>()
            calls += cursor
            val start = cursor?.toIntOrNull() ?: 0
            val entries = (start until (start + ENTRIES_PER_PAGE).coerceAtMost(totalEntries))
                .map(::entry)
            EntriesPage(
                entries = entries,
                cursor = (start + entries.size).toString().takeIf { start + entries.size < totalEntries }
            )
        }
        val coordinator = coordinator(api, preferences)

        val incomplete = runCatching {
            coordinator.run(forceFullSync = true) { entries, _ ->
                ArticleSyncStats(fetched = entries.size)
            }
        }

        assertTrue(incomplete.isFailure)
        assertEquals(200, calls.size)
        assertEquals("40000", preferences.checkpoint?.cursor)
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            val cursor = secondArg<String?>()
            calls += cursor
            val start = cursor?.toIntOrNull() ?: 0
            EntriesPage(
                entries = (start until totalEntries).map(::entry),
                cursor = null
            )
        }

        val resumedCoordinator = coordinator(api, preferences)
        val result = resumedCoordinator.run(forceFullSync = false) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(201, calls.size)
        assertEquals(200, result.stats.fetched)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `stale backend response is discarded before the page callback can write it`() = runBlocking {
        val preferences = preferences()
        val api = mockk<FeedBackend>()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        coEvery { api.getUnreadEntries(any(), any()) } coAnswers {
            fetchStarted.complete(Unit)
            releaseResponse.await()
            EntriesPage(entries = listOf(entry(3L)), cursor = null)
        }
        val sessionGate = SwitchableSessionGate()
        val coordinator = ArticleSyncCoordinator(
            api = api,
            preferences = preferences,
            preferenceWrites = mockk<PreferenceWriteBarrier>(relaxed = true),
            sessionGate = sessionGate,
            clock = clock
        )
        val writtenEntryIds = mutableListOf<Long>()

        val run = async {
            runCatching {
                coordinator.run(forceFullSync = true) { entries, _ ->
                    writtenEntryIds += entries.map { it.id }
                    ArticleSyncStats(fetched = entries.size)
                }
            }
        }

        fetchStarted.await()
        sessionGate.invalidate()
        releaseResponse.complete(Unit)

        val result = run.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleSyncSessionException)
        assertTrue(writtenEntryIds.isEmpty())
    }

    @Test
    fun `replays a page when persistence fails before its checkpoint is stored`() = runBlocking {
        val preferences = preferences()
        val api = mockk<FeedBackend>()
        val calls = mutableListOf<String?>()
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            val cursor = secondArg<String?>()
            calls += cursor
            if (cursor == null) {
                EntriesPage(entries = listOf(entry(1L)), cursor = "next")
            } else {
                EntriesPage(entries = listOf(entry(2L)), cursor = null)
            }
        }
        val coordinator = coordinator(api, preferences)
        var failPersistence = true
        val persistedEntryIds = mutableListOf<Long>()

        val firstRun = runCatching {
            coordinator.run(forceFullSync = true) { entries, _ ->
                if (failPersistence) {
                    failPersistence = false
                    error("simulated page persistence failure")
                }
                persistedEntryIds += entries.map { it.id }
                ArticleSyncStats(fetched = entries.size)
            }
        }

        assertTrue(firstRun.isFailure)
        assertEquals(null, preferences.checkpoint?.cursor)

        val resumed = coordinator.run(forceFullSync = false) { entries, _ ->
            persistedEntryIds += entries.map { it.id }
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(listOf(null, null, "next"), calls)
        assertEquals(listOf(1L, 2L), persistedEntryIds)
        assertEquals(2, resumed.stats.fetched)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `repeated cursor clears the checkpoint and fails the run`() = runBlocking {
        val preferences = preferences(
            SyncCheckpoint(
                ownerKey = OWNER,
                mode = SyncCheckpointMode.FULL,
                startedAt = clock.millis(),
                changedAfter = null,
                cursor = null,
                fullSyncRunId = "run"
            )
        )
        val api = mockk<FeedBackend>()
        coEvery { api.getUnreadEntries(any(), any()) } returns EntriesPage(
            entries = listOf(entry(1L)),
            cursor = "same"
        )
        val coordinator = coordinator(api, preferences)

        val result = runCatching {
            coordinator.run(forceFullSync = false) { entries, _ ->
                ArticleSyncStats(fetched = entries.size)
            }
        }

        assertTrue(result.isFailure)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `expired cursor restarts the durable run from the beginning`() = runBlocking {
        val preferences = preferences(
            SyncCheckpoint(
                ownerKey = OWNER,
                mode = SyncCheckpointMode.FULL,
                startedAt = clock.millis(),
                changedAfter = null,
                cursor = "expired",
                fullSyncRunId = "run"
            )
        )
        val api = mockk<FeedBackend>()
        val calls = mutableListOf<String?>()
        var fullSyncResets = 0
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            val cursor = secondArg<String?>()
            calls += cursor
            if (cursor == "expired") throw CursorExpiredException()
            EntriesPage(entries = listOf(entry(2L)), cursor = null)
        }
        val coordinator = ArticleSyncCoordinator(
            api = api,
            preferences = preferences,
            preferenceWrites = mockk<PreferenceWriteBarrier>(relaxed = true),
            sessionGate = FixedSessionGate(),
            onNewFullSync = { fullSyncResets++ },
            clock = clock
        )

        val result = coordinator.run(forceFullSync = false) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(listOf("expired", null), calls)
        assertEquals(1, result.stats.fetched)
        assertEquals(1, fullSyncResets)
        assertNull(preferences.checkpoint)
    }

    private fun coordinator(
        api: FeedBackend,
        preferences: SyncPreferences
    ) = ArticleSyncCoordinator(
        api = api,
        preferences = preferences,
        preferenceWrites = mockk<PreferenceWriteBarrier>(relaxed = true),
        sessionGate = FixedSessionGate(),
        clock = clock
    )

    private fun preferences(checkpoint: SyncCheckpoint? = null): FakeSyncPreferences {
        val preferences = FakeSyncPreferences(checkpoint)
        return preferences
    }

    private fun entry(id: Int): Entry = entry(id.toLong())

    private fun entry(id: Long) = Entry(
        id = id,
        title = "Article $id",
        author = null,
        url = "https://example.com/$id",
        publishedAt = Instant.ofEpochMilli(id),
        content = "Body",
        feed = Feed(1L, "Feed", null, "https://example.com/feed.xml"),
        readingTime = null,
        status = ArticleStatus.UNREAD
    )

    private companion object {
        const val ENTRIES_PER_PAGE = 200
        const val OWNER = "owner"
    }
}

private class FakeSyncPreferences(
    var checkpoint: SyncCheckpoint?
) : SyncPreferences by mockk<SyncPreferences>(relaxed = true) {

    override fun getSyncCheckpoint(): SyncCheckpoint? = checkpoint

    override fun setSyncCheckpoint(checkpoint: SyncCheckpoint) {
        this.checkpoint = checkpoint
    }

    override fun clearSyncCheckpoint() {
        checkpoint = null
    }

    override fun getLastSyncTimestamp(): Long = 0L

    override fun getLastFullSyncTimestamp(): Long = 0L
}

private class FixedSessionGate : SyncSessionGate {
    private val session = SyncSession(ownerKey = "owner", generation = 1L)

    override fun currentSession(): SyncSession = session

    override fun isCurrent(session: SyncSession): Boolean = session == this.session

    override suspend fun <T> withSession(session: SyncSession, block: suspend () -> T): T {
        check(isCurrent(session))
        return block()
    }

    override suspend fun <T> withSessionChange(block: suspend () -> T): T = block()
}

private class SwitchableSessionGate : SyncSessionGate {
    private val session = SyncSession(ownerKey = "owner", generation = 1L)
    private var valid = true

    fun invalidate() {
        valid = false
    }

    override fun currentSession(): SyncSession = session

    override fun isCurrent(session: SyncSession): Boolean = valid && session == this.session

    override suspend fun <T> withSession(session: SyncSession, block: suspend () -> T): T {
        if (!isCurrent(session)) throw StaleSyncSessionException()
        return block()
    }

    override suspend fun <T> withSessionChange(block: suspend () -> T): T = block()
}
