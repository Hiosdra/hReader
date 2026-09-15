package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
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
    fun `returns only articles still read before the undo cutoff`() = runBlocking {
        val before = Instant.parse("2026-09-01T00:00:00Z")
        coEvery {
            articleMutationDao.getIdsReadNoLaterThan(listOf("1", "2"), before, ArticleStatus.READ)
        } returns listOf("2", "1")

        assertEquals(
            listOf(2L, 1L),
            repository.idsStillReadSince(listOf(1L, 2L), before)
        )
    }
}
