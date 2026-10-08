package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
import com.hiosdra.hreader.core.application.port.out.BulkReadMarker
import com.hiosdra.hreader.core.domain.model.ArticleStatus
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
    fun `marks unread articles in one database operation and returns a compact undo marker`() = runBlocking {
        coEvery {
            articleMutationDao.markUnreadAsRead(7L, any(), ArticleStatus.READ)
        } returns 3

        val marker = repository.markUnreadAsRead(7L)

        assertEquals(7L, marker.feedId)
        assertEquals(3, marker.count)
        coVerify(exactly = 1) {
            articleMutationDao.markUnreadAsRead(7L, marker.markedAt, ArticleStatus.READ)
        }
    }

    @Test
    fun `marks unread articles through the current article in one database operation`() = runBlocking {
        val publishedAt = Instant.parse("2026-09-01T00:00:00Z")
        coEvery {
            articleMutationDao.markUnreadAsReadUpTo(7L, "42", publishedAt, any(), ArticleStatus.READ)
        } returns 2

        val marker = repository.markUnreadAsReadUpTo(7L, "42", publishedAt)

        assertEquals(7L, marker.feedId)
        assertEquals(2, marker.count)
        coVerify(exactly = 1) {
            articleMutationDao.markUnreadAsReadUpTo(7L, "42", publishedAt, marker.markedAt, ArticleStatus.READ)
        }
    }

    @Test
    fun `undoes a bulk read through its marker`() = runBlocking {
        val marker = BulkReadMarker(7L, Instant.parse("2026-09-01T00:00:00Z"), 3)
        coEvery {
            articleMutationDao.undoBulkRead(
                7L,
                marker.markedAt,
                ArticleStatus.READ,
                ArticleStatus.UNREAD
            )
        } returns marker.count

        assertEquals(marker.count, repository.undoBulkRead(marker))
        coVerify(exactly = 1) {
            articleMutationDao.undoBulkRead(
                7L,
                marker.markedAt,
                ArticleStatus.READ,
                ArticleStatus.UNREAD
            )
        }
    }
}
