package com.hiosdra.hreader.core.application.sync

import com.hiosdra.hreader.core.application.exception.CursorExpiredException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
        val preferences = FakeSyncPreferences()
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
            coordinator.run(forceFullSync = true, ownerKey = OWNER) { entries, _ ->
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
            EntriesPage(entries = (start until totalEntries).map(::entry), cursor = null)
        }

        val result = coordinator.run(forceFullSync = false, ownerKey = OWNER) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(201, calls.size)
        assertEquals(200, result.stats.fetched)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `a persistence failure leaves the previous cursor available for replay`() = runBlocking {
        val preferences = FakeSyncPreferences()
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
            coordinator.run(forceFullSync = true, ownerKey = OWNER) { entries, _ ->
                if (failPersistence) {
                    failPersistence = false
                    error("simulated page persistence failure")
                }
                persistedEntryIds += entries.map { it.id }
                ArticleSyncStats(fetched = entries.size)
            }
        }

        assertTrue(firstRun.isFailure)
        assertNull(preferences.checkpoint?.cursor)

        val resumed = coordinator.run(forceFullSync = false, ownerKey = OWNER) { entries, _ ->
            persistedEntryIds += entries.map { it.id }
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(listOf(null, null, "next"), calls)
        assertEquals(listOf(1L, 2L), persistedEntryIds)
        assertEquals(2, resumed.stats.fetched)
    }

    @Test
    fun `repeated cursor clears the checkpoint and fails the run`() = runBlocking {
        val preferences = FakeSyncPreferences(
            checkpoint = SyncCheckpoint(
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
            coordinator.run(forceFullSync = false, ownerKey = OWNER) { entries, _ ->
                ArticleSyncStats(fetched = entries.size)
            }
        }

        assertTrue(result.isFailure)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `expired cursor restarts the durable run from the beginning`() = runBlocking {
        val preferences = FakeSyncPreferences(
            checkpoint = SyncCheckpoint(
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
            onNewFullSync = { fullSyncResets++ },
            clock = clock
        )

        val result = coordinator.run(forceFullSync = false, ownerKey = OWNER) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(listOf("expired", null), calls)
        assertEquals(1, result.stats.fetched)
        assertEquals(1, fullSyncResets)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `a recent completed full sync uses incremental changes with overlap`() = runBlocking {
        val lastSync = clock.millis() - 30 * 60 * 1000L
        val lastFull = clock.millis() - 2 * 24 * 60 * 60 * 1000L
        val preferences = FakeSyncPreferences(
            lastSyncTimestamp = lastSync,
            lastFullSyncTimestamp = lastFull
        )
        val api = mockk<FeedBackend>()
        val changedAfter = io.mockk.slot<Instant>()
        coEvery {
            api.getEntriesChangedAfter(capture(changedAfter), any(), any())
        } returns EntriesPage(listOf(entry(3L)), null)
        val coordinator = coordinator(api, preferences)

        val result = coordinator.run(forceFullSync = false, ownerKey = OWNER) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertTrue(result.isIncremental)
        assertEquals(Instant.ofEpochMilli(lastSync).minusSeconds(5 * 60), changedAfter.captured)
        assertNull(preferences.checkpoint)
        coVerify(exactly = 0) { api.getUnreadEntries(any(), any()) }
    }

    @Test
    fun `empty page is a successful terminal page and does not invoke persistence`() = runBlocking {
        val preferences = FakeSyncPreferences()
        val api = mockk<FeedBackend>()
        coEvery { api.getUnreadEntries(any(), any()) } returns EntriesPage(emptyList(), "ignored")
        val coordinator = coordinator(api, preferences)
        var persistedPages = 0

        val result = coordinator.run(forceFullSync = true, ownerKey = OWNER) { _, _ ->
            persistedPages++
            ArticleSyncStats(fetched = 1)
        }

        assertEquals(0, result.stats.fetched)
        assertEquals(0, persistedPages)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `partial pages are accumulated until the terminal cursor`() = runBlocking {
        val preferences = FakeSyncPreferences()
        val api = mockk<FeedBackend>()
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            when (secondArg<String?>()) {
                null -> EntriesPage(listOf(entry(4L)), "next")
                else -> EntriesPage(listOf(entry(5L)), null)
            }
        }
        val coordinator = coordinator(api, preferences)

        val result = coordinator.run(forceFullSync = true, ownerKey = OWNER) { entries, _ ->
            ArticleSyncStats(fetched = entries.size)
        }

        assertEquals(2, result.stats.fetched)
        assertNull(preferences.checkpoint)
    }

    @Test
    fun `cancellation leaves the last durable cursor for retry`() = runBlocking {
        val preferences = FakeSyncPreferences()
        val api = mockk<FeedBackend>()
        coEvery { api.getUnreadEntries(any(), any()) } answers {
            if (secondArg<String?>() == null) {
                EntriesPage(listOf(entry(6L)), "next")
            } else {
                throw CancellationException("cancelled")
            }
        }
        val coordinator = coordinator(api, preferences)

        try {
            coordinator.run(forceFullSync = true, ownerKey = OWNER) { entries, _ ->
                ArticleSyncStats(fetched = entries.size)
            }
            error("Cancellation should be propagated")
        } catch (_: CancellationException) {
            assertEquals("next", preferences.checkpoint?.cursor)
        }
    }

    private fun coordinator(
        api: FeedBackend,
        preferences: FakeSyncPreferences
    ) = ArticleSyncCoordinator(
        api = api,
        preferences = preferences,
        clock = clock
    )

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
    var checkpoint: SyncCheckpoint? = null,
    private val lastSyncTimestamp: Long = 0L,
    private val lastFullSyncTimestamp: Long = 0L
) : SyncPreferences by mockk<SyncPreferences>(relaxed = true) {
    override fun getCacheOwnerKey(): String = "owner"
    override fun getSyncCheckpoint(): SyncCheckpoint? = checkpoint
    override fun setSyncCheckpoint(checkpoint: SyncCheckpoint) {
        this.checkpoint = checkpoint
    }
    override fun clearSyncCheckpoint() {
        checkpoint = null
    }
    override fun getLastSyncTimestamp(): Long = lastSyncTimestamp
    override fun getLastFullSyncTimestamp(): Long = lastFullSyncTimestamp
}
