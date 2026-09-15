package com.hiosdra.hreader.adapter.storage

import android.app.Application
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.core.application.port.out.StorageDatabaseStats
import com.hiosdra.hreader.core.application.port.out.StorageDatabaseStatsStore
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.storage.StorageCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = StorageRepositoryTestApplication::class, sdk = [35])
class StorageRepositoryTest {

    @Test
    fun `inspect reads database counts through the persistence port`() = runBlocking {
        val databaseStats = mockk<StorageDatabaseStatsStore>()
        coEvery { databaseStats.getStats() } returns StorageDatabaseStats(
            articleCount = 7,
            feedCount = 2,
            storedContentCount = 3,
            readingPositionCount = 4,
            imageCount = 5,
            pageCount = 6
        )
        val repository = StorageRepository(
            context = RuntimeEnvironment.getApplication(),
            databaseStats = databaseStats,
            images = mockk<ArticleImageStore>(relaxed = true),
            pages = mockk<ArticlePageStore>(relaxed = true),
            ttsModels = mockk<TtsModelGateway>(relaxed = true),
            ttsDownloads = mockk<TtsModelDownloadRequester>(relaxed = true),
            gemmaModel = mockk<GemmaModelGateway>(relaxed = true),
            gemmaDownloads = mockk<GemmaModelDownloadRequester>(relaxed = true),
            gemmaLifecycle = mockk<GemmaModelLifecycle>(relaxed = true),
            ttsPlayer = mockk<ArticleTtsPlayer>(relaxed = true)
        )

        val snapshot = repository.inspect()

        assertEquals(7, snapshot.articleCount)
        assertEquals(2, snapshot.feedCount)
        assertEquals(3, snapshot.storedContentCount)
        assertEquals(4, snapshot.readingPositionCount)
        assertEquals(
            16,
            snapshot.categories.first { it.category == StorageCategory.ARTICLE_DATA }.itemCount
        )
        assertEquals(
            5,
            snapshot.categories.first { it.category == StorageCategory.DOWNLOADED_IMAGES }.itemCount
        )
        assertEquals(
            6,
            snapshot.categories.first { it.category == StorageCategory.OFFLINE_PAGES }.itemCount
        )
        coVerify(exactly = 1) { databaseStats.getStats() }
    }
}

private class StorageRepositoryTestApplication : Application()
