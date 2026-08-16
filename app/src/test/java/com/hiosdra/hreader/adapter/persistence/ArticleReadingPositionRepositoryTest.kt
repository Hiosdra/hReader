package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.ArticleReadingPositionRepository
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleReadingPositionRepositoryTest {
    private val dao = mockk<ArticleReadingPositionDao>(relaxed = true)
    private val repository = ArticleReadingPositionRepository(dao)

    @Test
    fun `positions are returned by numeric article id`() = runBlocking {
        coEvery { dao.getForArticles(listOf(7L, 9L)) } returns listOf(
            ArticleReadingPosition(7L, 0.25f),
            ArticleReadingPosition(9L, 0.75f)
        )

        assertEquals(
            mapOf(7L to 0.25f, 9L to 0.75f),
            repository.getProgresses(listOf(7L, 9L))
        )
    }

    @Test
    fun `empty article ids do not query the dao`() = runBlocking {
        assertEquals(emptyMap<Long, Float>(), repository.getProgresses(emptyList()))

        coVerify(exactly = 0) { dao.getForArticles(emptyList()) }
    }

    @Test
    fun `saved progress is clamped`() = runBlocking {
        val saved = slot<ArticleReadingPosition>()
        coEvery { dao.upsert(capture(saved)) } just runs

        repository.saveProgress(7L, 1.5f)

        coVerify { dao.upsert(saved.captured) }
        assertEquals(1f, saved.captured.progress)
    }
}
