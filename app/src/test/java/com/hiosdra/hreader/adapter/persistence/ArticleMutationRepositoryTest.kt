package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArticleMutationRepositoryTest {
    private val articleMutationDao = mockk<ArticleMutationDao>(relaxed = true)
    private val repository = ArticleMutationRepository(articleMutationDao)

    @Test
    fun `queues a local read status change`() = runBlocking {
        repository.updateReadStatus(listOf("1"), ArticleStatus.READ)

        coVerify {
            articleMutationDao.updateStatusForIds(
                listOf("1"),
                ArticleStatus.READ,
                any()
            )
        }
    }

    @Test
    fun `bulk read updates the scope without loading article ids`() = runBlocking {
        val query = ArticleListQuery(feedId = 7L)
        coEvery {
            articleMutationDao.markScopeRead(7L, ArticleStatus.READ, any())
        } returns 3

        val update = repository.updateReadStatus(query, ArticleStatus.READ)

        assertEquals(3, update.changedCount)
        assertEquals(true, update.readAt != null)
        coVerify(exactly = 1) {
            articleMutationDao.markScopeRead(7L, ArticleStatus.READ, any())
        }
        coVerify(exactly = 0) { articleMutationDao.markScopeUnread(any(), any(), any()) }
    }

    @Test
    fun `bulk unread uses the search scope`() = runBlocking {
        val query = ArticleListQuery(searchQuery = "science")
        coEvery {
            articleMutationDao.markSearchUnread(
                null,
                ArticleStatus.READ,
                ArticleStatus.UNREAD,
                "science*",
                "%science%"
            )
        } returns 2

        val update = repository.updateReadStatus(query, ArticleStatus.UNREAD)

        assertEquals(2, update.changedCount)
        assertEquals(null, update.readAt)
        coVerify(exactly = 1) {
            articleMutationDao.markSearchUnread(
                null,
                ArticleStatus.READ,
                ArticleStatus.UNREAD,
                "science*",
                "%science%"
            )
        }
    }

    @Test
    fun `mark through uses the article ordering and search scope`() = runBlocking {
        val publishedAt = Instant.parse("2026-09-01T00:00:00Z")
        val query = ArticleListQuery(searchQuery = "science")
        coEvery { articleMutationDao.getPublishedAt("20") } returns publishedAt
        coEvery {
            articleMutationDao.markSearchReadThrough(
                null,
                ArticleStatus.READ,
                any(),
                "20",
                publishedAt,
                "science*",
                "%science%"
            )
        } returns 4

        val update = repository.markReadThrough(query, articleId = 20L)

        assertEquals(4, update.changedCount)
        assertEquals(true, update.readAt != null)
        coVerify(exactly = 1) {
            articleMutationDao.markSearchReadThrough(
                null,
                ArticleStatus.READ,
                any(),
                "20",
                publishedAt,
                "science*",
                "%science%"
            )
        }
    }

    @Test
    fun `undo changes only rows carrying the bulk read token`() = runBlocking {
        val readAt = Instant.parse("2026-09-01T00:00:00Z")
        coEvery {
            articleMutationDao.undoReadStatus(readAt, ArticleStatus.READ, ArticleStatus.UNREAD)
        } returns 5

        assertEquals(5, repository.undoReadStatus(readAt))
        coVerify(exactly = 1) {
            articleMutationDao.undoReadStatus(readAt, ArticleStatus.READ, ArticleStatus.UNREAD)
        }
    }
}
