package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRetentionDao
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArticleRetentionRepositoryTest {
    private val articleRetentionDao = mockk<ArticleRetentionDao>(relaxed = true)
    private val repository = ArticleRetentionRepository(articleRetentionDao)

    @Test
    fun `delegates retention cutoff and read status`() = runBlocking {
        val before = Instant.parse("2026-08-01T00:00:00Z")
        coEvery {
            articleRetentionDao.deleteReadArticlesBefore(before, ArticleStatus.READ)
        } returns 7

        assertEquals(7, repository.deleteReadArticlesBefore(before))

        coVerify {
            articleRetentionDao.deleteReadArticlesBefore(before, ArticleStatus.READ)
        }
    }
}
