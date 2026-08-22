package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ArticleRepositoryWindowTest {
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val repository = ArticleRepository(
        articleDao = articleDao,
        articleContentDao = mockk<ArticleContentDao>(relaxed = true),
        feedDao = mockk<FeedDao>(relaxed = true),
        api = mockk<FeedBackend>(relaxed = true),
        db = mockk<AppDatabase>(relaxed = true),
        preferencesManager = mockk<SyncPreferences>(relaxed = true),
        syncPerformanceLogger = mockk<SyncPerformanceTracker>(relaxed = true)
    )
    private val selectedAt = Instant.parse("2026-08-22T12:00:00Z")
    private val query = ArticleListQuery(sessionStart = Instant.parse("2026-08-22T00:00:00Z"))

    @Test
    fun `visible article window does not materialize the complete list`() = runBlocking {
        coEvery { articleDao.countList(any(), any(), any(), any()) } returns 100_000
        coEvery { articleDao.getPublishedAt("50") } returns selectedAt
        coEvery { articleDao.countVisibleArticle(any(), any(), any(), any(), any()) } returns 1
        coEvery { articleDao.countArticlesBefore(any(), any(), any(), any(), any(), any()) } returns 50
        coEvery { articleDao.getListWindowBefore(any(), any(), any(), any(), any(), any(), any()) } returns
            (30L..49L).map { it.toString() }.reversed()
        coEvery { articleDao.getListWindowAfter(any(), any(), any(), any(), any(), any(), any()) } returns
            (51L..70L).map { it.toString() }

        val window = repository.listWindow(query, articleId = 50L, radius = 20)

        assertEquals(100_000, window.totalCount)
        assertEquals(41, window.ids.size)
        assertEquals(30L, window.ids.first())
        assertEquals(50L, window.ids[window.currentIndex])
        assertEquals(30, window.windowStartIndex)
        coVerify(exactly = 0) { articleDao.getListWindow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing selected article falls back to a bounded first window`() = runBlocking {
        coEvery { articleDao.countList(any(), any(), any(), any()) } returns 100_000
        coEvery { articleDao.getPublishedAt("999") } returns null
        coEvery { articleDao.getListWindow(any(), any(), any(), any(), any(), any()) } returns
            (1L..41L).map { it.toString() }

        val window = repository.listWindow(query, articleId = 999L, radius = 20)

        assertEquals(100_000, window.totalCount)
        assertEquals(41, window.ids.size)
        assertTrue(window.ids.contains(1L))
        assertEquals(0, window.windowStartIndex)
        assertEquals(0, window.currentIndex)
    }
}
