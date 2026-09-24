package com.hiosdra.hreader.adapter.persistence

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.SyncCheckpoint
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Enclosure
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleSyncEngineIntegrationTestApplication::class, sdk = [35])
class ArticleSyncEngineIntegrationTest {
    private val fixture = EngineFixture(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() {
        fixture.database.close()
    }

    @Test
    fun `full sync persists partial pages and removes only missing synced unread articles`() = runBlocking {
        val feed = Feed(1L, "Feed", "https://example.com", "https://example.com/feed.xml")
        fixture.database.feedDao().insertFeeds(listOf(feed.toArticleFeedEntity()))
        fixture.database.articleRecordDao().insertArticles(
            listOf(
                article("1", ArticleStatus.UNREAD),
                article("2", ArticleStatus.READ, readAt = Instant.now())
            )
        )
        fixture.backend.feeds = listOf(feed)
        fixture.backend.unreadPage = { cursor ->
            if (cursor == null) {
                EntriesPage(listOf(entry(3L, feed)), "page-2")
            } else {
                EntriesPage(listOf(entry(4L, feed)), null)
            }
        }

        val result = fixture.engine.refreshArticles(forceFullSync = true)

        assertEquals(listOf("2", "3", "4"), fixture.database.articleRecordDao().getAllIds().sorted())
        assertEquals(2, result.newArticles)
        assertEquals(setOf(1L), result.activeFeedIds)
        assertEquals(0, fullSyncSeenCount())
        assertTrue(fixture.preferences.getLastFullSyncTimestamp() > 0L)
        assertNull(fixture.preferences.getSyncCheckpoint())
    }

    @Test
    fun `incremental sync preserves articles outside the changed page`() = runBlocking {
        val now = System.currentTimeMillis()
        fixture.preferences.storedLastSyncTimestamp = now - 30 * 60 * 1000L
        fixture.preferences.storedLastFullSyncTimestamp = now - 2 * 24 * 60 * 60 * 1000L
        val feed = Feed(1L, "Feed", "https://example.com", "https://example.com/feed.xml")
        fixture.database.feedDao().insertFeeds(listOf(feed.toArticleFeedEntity()))
        fixture.database.articleRecordDao().insertArticles(
            listOf(article("1", ArticleStatus.UNREAD), article("2", ArticleStatus.UNREAD))
        )
        fixture.backend.feeds = listOf(feed)
        fixture.backend.incrementalPage = EntriesPage(listOf(entry(3L, feed)), null)
        fixture.backend.unreadPage = { error("incremental run must not call the full endpoint") }

        val result = fixture.engine.refreshArticles(forceFullSync = false)

        assertTrue(result.successfulFeedIds.contains(1L))
        assertEquals(listOf("1", "2", "3"), fixture.database.articleRecordDao().getAllIds().sorted())
        assertEquals(1, fixture.backend.incrementalCalls.size)
        assertTrue(
            fixture.backend.incrementalCalls.single() <
                Instant.ofEpochMilli(fixture.preferences.storedLastSyncTimestamp)
        )
    }

    @Test
    fun `cancellation leaves the persisted page and cursor for the next run`() = runBlocking {
        val feed = Feed(1L, "Feed", "https://example.com", "https://example.com/feed.xml")
        fixture.backend.unreadPage = { cursor ->
            if (cursor == null) {
                EntriesPage(listOf(entry(7L, feed)), "page-2")
            } else {
                throw CancellationException("cancelled")
            }
        }

        val failure = runCatching {
            fixture.engine.refreshArticles(forceFullSync = true)
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf("7"), fixture.database.articleRecordDao().getAllIds())
        assertEquals("page-2", fixture.preferences.getSyncCheckpoint()?.cursor)
        assertEquals(1, fullSyncSeenCount())
        assertEquals(0L, fixture.preferences.getLastSyncTimestamp())
    }

    @Test
    fun `a local status change during push remains queued`() = runBlocking {
        val feed = Feed(1L, "Feed", "https://example.com", "https://example.com/feed.xml")
        fixture.database.feedDao().insertFeeds(listOf(feed.toArticleFeedEntity()))
        fixture.database.articleRecordDao().insertArticles(listOf(article("10", ArticleStatus.READ)))
        fixture.database.articleMutationDao().updateStatusForIds(listOf("10"), ArticleStatus.UNREAD, null)
        fixture.backend.feeds = listOf(feed)
        fixture.backend.unreadPage = { EntriesPage(emptyList(), null) }
        val pushed = CompletableDeferred<Unit>()
        val releasePush = CompletableDeferred<Unit>()
        fixture.backend.statusUpdater = { _, _ ->
            pushed.complete(Unit)
            releasePush.await()
        }

        val sync = launch { fixture.engine.refreshArticles(forceFullSync = false) }
        pushed.await()
        fixture.database.articleMutationDao().updateStatusForIds(
            listOf("10"),
            ArticleStatus.READ,
            Instant.now()
        )
        releasePush.complete(Unit)
        sync.join()

        val pending = fixture.database.pendingChangeDao().getPendingStatuses()
        assertEquals(1, pending.size)
        assertEquals(ArticleStatus.READ, pending.single().status)
    }

    private suspend fun fullSyncSeenCount(): Int = fixture.database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM full_sync_seen")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun article(
        id: String,
        status: ArticleStatus,
        readAt: Instant? = null
    ) = ArticleEntity(
        id = id,
        title = "Article $id",
        author = null,
        url = "https://example.com/$id",
        publishedAt = Instant.parse("2026-09-01T00:00:00Z"),
        content = "Body $id",
        fullContent = null,
        preview = "Body $id",
        feedId = 1L,
        readingTime = null,
        enclosures = emptyList(),
        status = status,
        pendingSync = false,
        readAt = readAt,
        backlogFetchedAt = null
    )

    private fun entry(id: Long, feed: Feed) = Entry(
        id = id,
        title = "Article $id",
        author = "Author",
        url = "https://example.com/$id",
        publishedAt = Instant.parse("2026-09-02T00:00:00Z"),
        content = "Body $id",
        preview = null,
        feed = feed,
        readingTime = 2,
        enclosures = listOf(Enclosure("https://example.com/$id.jpg", "image/jpeg")),
        status = ArticleStatus.UNREAD
    )
}

private class EngineFixture(context: Context) {
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val backend = TestFeedBackend()
    val preferences = TestSyncPreferences()
    private val identity = FixedBackendIdentity()
    private val images = mockk<ArticleImageStore>(relaxed = true)
    private val credibility = mockk<CredibilityStore>(relaxed = true)
    val engine = ArticleSyncEngine(
        articleRecordDao = database.articleRecordDao(),
        articleStatsDao = database.articleStatsDao(),
        articleContentDao = database.articleContentDao(),
        feedDao = database.feedDao(),
        fullSyncSeenDao = database.fullSyncSeenDao(),
        api = backend,
        db = database,
        preferences = preferences,
        performance = NoOpSyncPerformance,
        imageStore = images,
        credibilityStore = credibility,
        backendIdentity = identity,
        pendingChangeStore = PendingChangeRepository(database.pendingChangeDao()),
        articleRetentionStore = ArticleRetentionRepository(database.articleRetentionDao())
    )
}

private class TestFeedBackend : FeedBackend {
    var feeds: List<Feed> = emptyList()
    var unreadPage: suspend (String?) -> EntriesPage = { EntriesPage(emptyList(), null) }
    var incrementalPage: EntriesPage = EntriesPage(emptyList(), null)
    var statusUpdater: suspend (List<Long>, ArticleStatus) -> Unit = { _, _ -> }
    val incrementalCalls = mutableListOf<Instant>()

    override suspend fun getUnreadEntries(limit: Int, cursor: String?): EntriesPage = unreadPage(cursor)

    override suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage {
        incrementalCalls += changedAfter
        return incrementalPage
    }

    override suspend fun getRecentEntries(limit: Int, cursor: String?): EntriesPage = EntriesPage(emptyList(), null)

    override suspend fun getFeeds(): List<Feed> = feeds

    override suspend fun getUnreadCounts(): Map<Long, Int> = emptyMap()

    override suspend fun createFeed(feedUrl: String) = Unit

    override suspend fun deleteFeed(feedId: Long) = Unit

    override suspend fun renameFeed(feedId: Long, title: String) = Unit

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = emptyList()

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
        statusUpdater(entryIds, status)
    }

    override suspend fun fetchFullContent(entryId: Long, articleUrl: String?): String? = null

    override suspend fun verifyConnection(): Int = 200
}

private class FixedBackendIdentity : BackendIdentity {
    override fun isComplete(): Boolean = true

    override fun cacheOwnerKey(): String = "owner"
}

private class TestSyncPreferences(
    var checkpoint: SyncCheckpoint? = null,
    var storedLastSyncTimestamp: Long = 0L,
    var storedLastFullSyncTimestamp: Long = 0L
) : SyncPreferences by mockk<SyncPreferences>(relaxed = true) {
    override fun getLastSyncTimestamp(): Long = storedLastSyncTimestamp

    override fun setLastSyncTimestamp(timestamp: Long) {
        storedLastSyncTimestamp = timestamp
    }

    override fun getLastFullSyncTimestamp(): Long = storedLastFullSyncTimestamp

    override fun setLastFullSyncTimestamp(timestamp: Long) {
        storedLastFullSyncTimestamp = timestamp
    }

    override fun getCacheOwnerKey(): String = "owner"

    override fun getSyncCheckpoint(): SyncCheckpoint? = checkpoint

    override fun setSyncCheckpoint(checkpoint: SyncCheckpoint) {
        this.checkpoint = checkpoint
    }

    override fun clearSyncCheckpoint() {
        checkpoint = null
    }

    override fun getOfflineBacklogTarget(): Int = 0
}

private object NoOpSyncPerformance : SyncPerformanceTracker {
    override suspend fun <T> measureSyncTime(operation: SyncPerformanceOperation, block: suspend () -> T): T =
        block()

    override fun logBatchInfo(batchSize: Int, totalArticles: Int) = Unit

    override fun logArticleSyncStats(stats: ArticleSyncStats) = Unit

    override fun logSyncMode(isIncremental: Boolean, lastSyncTime: Long?) = Unit
}

private fun Feed.toArticleFeedEntity() = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl,
    preloadAiOverview = preloadAiOverview,
    autoMarkRead = autoMarkRead
)

internal class ArticleSyncEngineIntegrationTestApplication : Application()
