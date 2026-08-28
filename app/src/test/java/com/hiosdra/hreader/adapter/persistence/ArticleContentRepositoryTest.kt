package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.ArticleContentRepository
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.domain.model.Enclosure
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant

@RunWith(JUnit4::class)
class ArticleContentRepositoryTest {

    private val entryId = 7L
    private val articleUrl = "https://example.com/posts/one"

    private val backend = mockk<FeedBackend>()
    private val articleContentDao = mockk<ArticleContentDao>(relaxed = true)
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val articleImageStore = mockk<ArticleImageStore>(relaxed = true)
    private val credibilityStore = mockk<CredibilityStore>(relaxed = true)
    private val articleAiOverviewStore = mockk<ArticleAiOverviewStore>(relaxed = true)
    private val articlePageStore = mockk<ArticlePageStore>(relaxed = true)

    private val repository = ArticleContentRepository(
        { "Open embedded media" },
        backend,
        articleContentDao,
        articleDao,
        articleImageStore,
        credibilityStore,
        articleAiOverviewStore,
        articlePageStore
    )

    private fun article(
        content: String? = "<p>What the feed carried</p>",
        fullContent: String? = null,
        preview: String? = null,
        enclosures: List<Enclosure> = emptyList()
    ) = ArticleEntity(
        id = entryId.toString(),
        title = "An article",
        author = null,
        url = articleUrl,
        publishedAt = Instant.EPOCH,
        content = content,
        fullContent = fullContent,
        preview = preview,
        feedId = 1L,
        readingTime = null,
        enclosures = enclosures
    )

    private fun storedContent(
        content: String,
        isPrepared: Boolean,
        leadImageUrl: String? = null,
        source: ArticleContentSource = ArticleContentSource.FULL,
        imageUrls: String = "\u0000"
    ) =
        ArticleContent(
            entryId = entryId,
            content = content,
            fetchedAt = Instant.EPOCH,
            url = articleUrl,
            source = source,
            isPrepared = isPrepared,
            leadImageUrl = leadImageUrl,
            imageUrls = imageUrls
        )

    @Test
    fun `a fetched article is stored ready to read`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns """<p>Text</p><img src="/media/photo.jpg">"""
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())
        val stored = slot<ArticleContent>()

        val text = repository.getArticleContent(entryId, articleUrl)

        coVerify { articleContentDao.insertArticleContent(capture(stored)) }
        assertTrue(stored.captured.isPrepared)
        assertEquals(ArticleContentSource.FULL, stored.captured.source)
        assertTrue(stored.captured.content.contains("https://example.com/media/photo.jpg"))
        assertEquals(stored.captured.content, text.html)
        assertEquals(ArticleContentSource.FULL, text.source)
    }

    @Test
    fun `the pictures a fetched article references are downloaded under the address it renders`() =
        runBlocking {
            coEvery { articleContentDao.getArticleContent(entryId) } returns null
            coEvery { backend.fetchFullContent(entryId, articleUrl) } returns """<img src="/media/photo.jpg">"""
            coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

            repository.getArticleContent(entryId, articleUrl)

            coVerify {
                articleImageStore.downloadAndStoreImage(
                    entryId,
                    "https://example.com/media/photo.jpg"
                )
            }
        }

    @Test
    fun `the enclosure leads an article whose body does not carry it`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns "<p>Text with no pictures</p>"
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(
            article(enclosures = listOf(Enclosure("https://example.com/photo.jpg", "image/jpeg")))
        )

        val text = repository.getArticleContent(entryId, articleUrl)

        assertEquals("https://example.com/photo.jpg", text.leadImageUrl)
    }

    @Test
    fun `an article whose body opens with the enclosure has nothing to show above it`() =
        runBlocking {
            coEvery { articleContentDao.getArticleContent(entryId) } returns null
            coEvery { backend.fetchFullContent(entryId, articleUrl) } returns
                """<img src="https://example.com/photo.jpg"><p>Text</p>"""
            coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(
                article(enclosures = listOf(Enclosure("https://example.com/photo.jpg", "image/jpeg")))
            )

            val text = repository.getArticleContent(entryId, articleUrl)

            assertNull(text.leadImageUrl)
        }

    @Test
    fun `an article already stored ready to read is handed over untouched`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns
            storedContent("<p>Prepared</p>", isPrepared = true, leadImageUrl = "https://example.com/photo.jpg")

        val text = repository.getArticleContent(entryId, articleUrl)

        assertEquals("<p>Prepared</p>", text.html)
        assertEquals("https://example.com/photo.jpg", text.leadImageUrl)
        coVerify(exactly = 0) { articleContentDao.insertArticleContent(any()) }
        coVerify(exactly = 0) { backend.fetchFullContent(any(), any()) }
    }

    @Test
    fun `a prepared row from an older version loses its duplicate title`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns
            storedContent(
                "<h1>An article</h1><p>Prepared</p>",
                isPrepared = true,
                leadImageUrl = "https://example.com/photo.jpg"
            )
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

        val text = repository.getArticleContent(entryId, articleUrl)

        assertFalse(text.html.contains("<h1>An article</h1>"))
        assertTrue(text.html.contains("Prepared"))
    }

    @Test
    fun `article text returns while image download is still waiting`() = runBlocking {
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns
            """<p>Text</p><img src="/media/photo.jpg">"""
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())
        coEvery { articleImageStore.downloadAndStoreImage(entryId, any()) } coAnswers {
            downloadStarted.complete(Unit)
            releaseDownload.await()
        }

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(downloadStarted.isCompleted)
        assertFalse(releaseDownload.isCompleted)
        assertTrue(text.html.contains("Text"))
        releaseDownload.complete(Unit)
        Unit
    }

    @Test
    fun `an article stored before this was prepared here is prepared and written back`() =
        runBlocking {
            coEvery { articleContentDao.getArticleContent(entryId) } returns
                storedContent("""<img src="/media/photo.jpg">""", isPrepared = false)
            coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())
            val written = slot<ArticleContent>()

            val text = repository.getArticleContent(entryId, articleUrl)

            coVerify { articleContentDao.insertArticleContent(capture(written)) }
            assertTrue(written.captured.isPrepared)
            assertTrue(written.captured.content.contains("https://example.com/media/photo.jpg"))
            assertEquals(written.captured.content, text.html)
            coVerify {
                articleImageStore.downloadAndStoreImage(
                    entryId,
                    "https://example.com/media/photo.jpg"
                )
            }
            coVerify(exactly = 0) { backend.fetchFullContent(any(), any()) }
        }

    @Test
    fun `an article the backend cannot serve falls back to what the feed carried`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns null
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article(content = "<p>Summary</p>"))

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(text.html.contains("Summary"))
        assertEquals(ArticleContentSource.FEED_FALLBACK, text.source)
    }

    @Test
    fun `a cached full body is used when the prepared content row is missing`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns null
        coEvery {
            articleDao.getArticlesImmediate(any())
        } returns listOf(article(content = null, fullContent = "<p>Cached full body</p>"))

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(text.html.contains("Cached full body"))
        assertEquals(ArticleContentSource.FULL, text.source)
    }

    @Test
    fun `the stored preview is used when article bodies are unavailable`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns null
        coEvery {
            articleDao.getArticlesImmediate(any())
        } returns listOf(article(content = null, preview = "Only the downloaded preview"))

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(text.html.contains("Only the downloaded preview"))
        assertEquals(ArticleContentSource.FEED_FALLBACK, text.source)
    }

    @Test
    fun `a blank prepared row does not hide the feed fallback`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns
            storedContent("  ", isPrepared = true)
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns null
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(text.html.contains("What the feed carried"))
        assertEquals(ArticleContentSource.FEED_FALLBACK, text.source)
    }

    @Test
    fun `a cached feed fallback upgrades when the backend later serves the full text`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns
            storedContent("<p>Summary</p>", isPrepared = true, source = ArticleContentSource.FEED_FALLBACK)
        coEvery { backend.fetchFullContent(entryId, articleUrl) } returns "<p>Full text</p>"
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

        val text = repository.getArticleContent(entryId, articleUrl)

        assertEquals(ArticleContentSource.FULL, text.source)
        assertTrue(text.html.contains("Full text"))
    }

    @Test
    fun `offline content lookup does not call the backend when only the feed body is available`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

        val text = repository.getArticleContent(entryId, articleUrl, allowNetwork = false)

        assertEquals(ArticleContentSource.FEED_FALLBACK, text.source)
        coVerify(exactly = 0) { backend.fetchFullContent(any(), any()) }
    }

    @Test
    fun `offline content lookup uses the preview when the feed body is absent`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery {
            articleDao.getArticlesImmediate(any())
        } returns listOf(article(content = null, preview = "Downloaded before going offline"))

        val text = repository.getArticleContent(entryId, articleUrl, allowNetwork = false)

        assertTrue(text.html.contains("Downloaded before going offline"))
        assertEquals(ArticleContentSource.FEED_FALLBACK, text.source)
        coVerify(exactly = 0) { backend.fetchFullContent(any(), any()) }
    }

    @Test
    fun `full offline preparation skips content already marked complete`() = runBlocking {
        val secondEntryId = 8L
        coEvery {
            articleContentDao.getFullyImagePreparedEntryIds(
                listOf(entryId, secondEntryId),
                ArticleContentSource.FULL
            )
        } returns listOf(entryId)

        val missing = repository.entriesMissingFullOfflinePreparation(
            listOf(
                entryId to articleUrl,
                secondEntryId to "https://example.com/posts/two"
            )
        )

        assertEquals(
            listOf(secondEntryId to "https://example.com/posts/two"),
            missing
        )
    }
}
