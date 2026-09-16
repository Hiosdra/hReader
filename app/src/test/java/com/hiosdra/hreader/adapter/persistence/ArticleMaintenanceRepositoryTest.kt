package com.hiosdra.hreader.adapter.persistence

import android.app.Application
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMaintenanceDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleBody
import com.hiosdra.hreader.adapter.persistence.room.entity.PrefetchTarget
import com.hiosdra.hreader.core.application.sync.PrefetchTarget as ApplicationPrefetchTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleMaintenanceRepositoryTestApplication::class, sdk = [35])
class ArticleMaintenanceRepositoryTest {
    private val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
    private val repository = ArticleMaintenanceRepository(articleMaintenanceDao)

    @Test
    fun `backfills previews from stored article bodies`() = runBlocking {
        coEvery { articleMaintenanceDao.getArticlesMissingPreview(10) } returns listOf(
            ArticleBody("1", "<p>Stored body</p>")
        )

        assertEquals(1, repository.backfillMissingPreviews(limit = 10))

        coVerify { articleMaintenanceDao.setPreview("1", "Stored body") }
    }

    @Test
    fun `maps only numeric prefetch ids`() = runBlocking {
        coEvery { articleMaintenanceDao.getPrefetchTargets() } returns listOf(
            PrefetchTarget("42", "https://example.com/42", emptyList()),
            PrefetchTarget("not-an-id", "https://example.com/bad", emptyList())
        )

        assertEquals(
            listOf(ApplicationPrefetchTarget(42L, "https://example.com/42", emptyList())),
            repository.getPrefetchTargets()
        )
    }
}

private class ArticleMaintenanceRepositoryTestApplication : Application()
