package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.PendingChangeDao
import com.hiosdra.hreader.adapter.persistence.room.entity.PendingStatus
import com.hiosdra.hreader.core.application.port.out.PendingArticleStatus
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingChangeRepositoryTest {
    private val pendingChangeDao = mockk<PendingChangeDao>(relaxed = true)
    private val repository = PendingChangeRepository(pendingChangeDao)

    @Test
    fun `maps queued statuses without exposing room projections`() = runBlocking {
        coEvery { pendingChangeDao.getPendingStatuses() } returns listOf(
            PendingStatus("1", ArticleStatus.READ),
            PendingStatus("2", null)
        )

        assertEquals(
            listOf(
                PendingArticleStatus("1", ArticleStatus.READ),
                PendingArticleStatus("2", null)
            ),
            repository.getPendingStatuses()
        )
    }

    @Test
    fun `clears only the status that was pushed`() = runBlocking {
        val ids = listOf("1", "2")

        repository.clearPendingStatuses(ids, ArticleStatus.UNREAD)

        coVerify { pendingChangeDao.clearPendingSync(ids, ArticleStatus.UNREAD) }
    }
}
