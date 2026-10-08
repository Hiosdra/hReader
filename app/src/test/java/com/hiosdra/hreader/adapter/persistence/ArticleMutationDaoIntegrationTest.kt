package com.hiosdra.hreader.adapter.persistence

import androidx.room.Room
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleMutationDaoIntegrationTestApplication::class, sdk = [35])
class ArticleMutationDaoIntegrationTest {
    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()
    private val articleRecordDao = database.articleRecordDao()
    private val articleMutationDao = database.articleMutationDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `marks unread articles in one feed through the current article and can undo them`() = runBlocking {
        val earlier = Instant.parse("2026-09-01T00:00:00Z")
        val current = Instant.parse("2026-09-02T00:00:00Z")
        articleRecordDao.insertArticles(
            listOf(
                article("a", 1L, earlier),
                article("b", 1L, current),
                article("c", 1L, current),
                article("d", 1L, current.plusSeconds(1)),
                article("e", 2L, earlier)
            )
        )
        val markedAt = Instant.parse("2026-09-03T00:00:00Z")

        val count = articleMutationDao.markUnreadAsReadUpTo(1L, "b", current, markedAt)
        val afterMarking = articleRecordDao.getArticlesImmediate(listOf("a", "b", "c", "d", "e"))
            .associateBy(ArticleEntity::id)

        assertEquals(2, count)
        assertEquals(ArticleStatus.READ, afterMarking.getValue("a").status)
        assertEquals(ArticleStatus.READ, afterMarking.getValue("b").status)
        assertEquals(ArticleStatus.UNREAD, afterMarking.getValue("c").status)
        assertEquals(ArticleStatus.UNREAD, afterMarking.getValue("d").status)
        assertEquals(ArticleStatus.UNREAD, afterMarking.getValue("e").status)

        assertEquals(2, articleMutationDao.undoBulkRead(1L, markedAt))
        val afterUndo = articleRecordDao.getArticlesImmediate(listOf("a", "b", "c", "d", "e"))
            .associateBy(ArticleEntity::id)
        assertEquals(ArticleStatus.UNREAD, afterUndo.getValue("a").status)
        assertEquals(ArticleStatus.UNREAD, afterUndo.getValue("b").status)
    }

    @Test
    fun `global scope includes feeds and breaks publication time ties by article id`() = runBlocking {
        val publishedAt = Instant.parse("2026-09-02T00:00:00Z")
        articleRecordDao.insertArticles(
            listOf(
                article("a", 1L, publishedAt),
                article("b", 2L, publishedAt),
                article("c", 1L, publishedAt)
            )
        )

        val count = articleMutationDao.markUnreadAsReadUpTo(
            feedId = null,
            articleId = "b",
            publishedAt = publishedAt,
            markedAt = publishedAt.plusSeconds(1)
        )
        val articles = articleRecordDao.getArticlesImmediate(listOf("a", "b", "c"))
            .associateBy(ArticleEntity::id)

        assertEquals(2, count)
        assertEquals(ArticleStatus.READ, articles.getValue("a").status)
        assertEquals(ArticleStatus.READ, articles.getValue("b").status)
        assertEquals(ArticleStatus.UNREAD, articles.getValue("c").status)
        assertNull(articles.getValue("c").readAt)
    }

    private fun article(id: String, feedId: Long, publishedAt: Instant) = ArticleEntity(
        id = id,
        title = "Article $id",
        author = null,
        url = "https://example.com/$id",
        publishedAt = publishedAt,
        content = null,
        feedId = feedId,
        readingTime = null,
        enclosures = emptyList(),
        status = ArticleStatus.UNREAD
    )
}

internal class ArticleMutationDaoIntegrationTestApplication : android.app.Application()
