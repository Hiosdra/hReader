package com.hiosdra.hreader.adapter.persistence

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiOverview
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleCredibility
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageManifest
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FullSyncSeenEntity
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = CacheDataCleanerIntegrationTestApplication::class, sdk = [35])
class CacheDataCleanerIntegrationTest {
    private val root = Files.createTempDirectory("hreader-cache-cleaner").toFile()
    private val imagesDir = File(root, "images").apply { mkdirs() }
    private val pagesDir = File(root, "pages").apply { mkdirs() }
    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()
    private val cleaner = CacheDataCleaner(database, imagesDir, pagesDir)

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun `clearAll removes database cache and filesystem cache together`() = kotlinx.coroutines.runBlocking {
        seedCache()
        val imageFile = File(imagesDir, "image.jpg")
        val pageDirectory = File(pagesDir, "1")
        imageFile.writeBytes(byteArrayOf(1, 2, 3))
        pageDirectory.mkdirs()
        File(pageDirectory, "index.html").writeText("cached page")

        cleaner.clearAll()

        assertEquals(0, database.articleDao().countArticles())
        assertEquals(0, database.feedDao().countFeeds())
        assertEquals(0, database.articleContentDao().countContent())
        assertEquals(0, database.articleImageDao().countImages())
        assertEquals(0, database.articlePageSnapshotDao().countPages())
        assertEquals(0, database.articleReadingPositionDao().countPositions())
        assertEquals(0, database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM article_image_manifest")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            })
        assertEquals(0, database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM article_credibility")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            })
        assertEquals(0, database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM article_ai_overviews")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            })
        assertEquals(0, database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM full_sync_seen")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            })
        assertEquals(0, database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM articles_fts")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            })
        assertFalse(imageFile.exists())
        assertFalse(pageDirectory.exists())
        assertTrue(imagesDir.listFiles().orEmpty().isEmpty())
        assertTrue(pagesDir.listFiles().orEmpty().isEmpty())
    }

    private suspend fun seedCache() {
        val article = ArticleEntity(
            id = "1",
            title = "Cached article",
            author = "Author",
            url = "https://example.com/article",
            publishedAt = Instant.parse("2026-09-01T00:00:00Z"),
            content = "Cached body",
            preview = "Cached body",
            feedId = 1L,
            readingTime = 2,
            enclosures = emptyList(),
            status = ArticleStatus.UNREAD
        )
        database.withTransaction {
            database.feedDao().insertFeeds(
                listOf(FeedEntity(1L, "Feed", "https://example.com", "https://example.com/feed.xml"))
            )
            database.articleDao().insertArticles(listOf(article))
            database.articleContentDao().insertArticleContent(
                ArticleContent(
                    entryId = 1L,
                    content = "Cached content",
                    fetchedAt = Instant.now(),
                    url = article.url,
                    isPrepared = true,
                    imageUrls = "https://example.com/image.jpg"
                )
            )
            database.articleImageDao().insertArticleImage(
                ArticleImage(
                    id = "image-1",
                    entryId = 1L,
                    originalUrl = "https://example.com/image.jpg",
                    localFilePath = File(imagesDir, "image.jpg").absolutePath,
                    mimeType = "image/jpeg",
                    downloadedAt = Instant.now(),
                    fileSize = 3L
                )
            )
            database.articleImageDao().insertExpectedImages(
                listOf(ArticleImageManifest(1L, "https://example.com/image.jpg"))
            )
            database.articlePageSnapshotDao().insert(
                ArticlePageSnapshot(
                    entryId = 1L,
                    originalUrl = article.url,
                    finalUrl = article.url,
                    directoryPath = File(pagesDir, "1").absolutePath,
                    fetchedAt = Instant.now(),
                    byteSize = 12L,
                    isComplete = true
                )
            )
            database.articleReadingPositionDao().upsert(ArticleReadingPosition("1", 0.5f))
            database.articleCredibilityDao().upsert(
                ArticleCredibility(
                    entryId = 1L,
                    score = 0.8f,
                    confidence = "HIGH",
                    summary = "Summary",
                    reasons = "Reasons",
                    redFlags = "",
                    factors = "",
                    modelId = "model",
                    analyzedAt = Instant.now(),
                    contentTruncated = false,
                    contentFingerprint = "fingerprint"
                )
            )
            database.articleAiOverviewDao().insert(
                ArticleAiOverview(
                    entryId = 1L,
                    overview = "Overview",
                    modelId = "model",
                    contentHash = "hash",
                    generatedAt = Instant.now()
                )
            )
            database.fullSyncSeenDao().insertAll(listOf(FullSyncSeenEntity("run", "1")))
        }
    }
}

private class CacheDataCleanerIntegrationTestApplication : Application()
