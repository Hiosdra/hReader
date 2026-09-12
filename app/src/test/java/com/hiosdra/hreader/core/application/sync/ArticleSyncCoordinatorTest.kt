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
import io.mockk.mockk
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
    var checkpoint: SyncCheckpoint? = null
) : SyncPreferences by mockk<SyncPreferences>(relaxed = true) {
    override fun getCacheOwnerKey(): String = "owner"
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
