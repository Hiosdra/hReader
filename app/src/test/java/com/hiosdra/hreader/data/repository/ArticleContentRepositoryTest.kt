package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.model.Enclosure
import com.hiosdra.hreader.data.remote.FeedBackend
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    private val articleDao = mockk<ArticleDao>()
    private val articleImageRepository = mockk<ArticleImageRepository>(relaxed = true)
    private val credibilityRepository = mockk<CredibilityRepository>(relaxed = true)

    private val repository = ArticleContentRepository(
        backend,
        articleContentDao,
        articleDao,
        articleImageRepository,
        credibilityRepository
    )

    private fun article(
        content: String? = "<p>What the feed carried</p>",
        enclosures: List<Enclosure> = emptyList()
    ) = ArticleEntity(
        id = entryId.toString(),
        title = "An article",
        author = null,
        url = articleUrl,
        publishedAt = Instant.EPOCH,
        content = content,
        feedId = 1L,
        readingTime = null,
        enclosures = enclosures
    )

    private fun storedContent(content: String, isPrepared: Boolean, leadImageUrl: String? = null) =
        ArticleContent(
            entryId = entryId,
            content = content,
            fetchedAt = Instant.EPOCH,
            url = articleUrl,
            isPrepared = isPrepared,
            leadImageUrl = leadImageUrl
        )

    @Test
    fun `a fetched article is stored ready to read`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId) } returns """<p>Text</p><img src="/media/photo.jpg">"""
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())
        val stored = slot<ArticleContent>()

        val text = repository.getArticleContent(entryId, articleUrl)

        coVerify { articleContentDao.insertArticleContent(capture(stored)) }
        assertTrue(stored.captured.isPrepared)
        assertTrue(stored.captured.content.contains("https://example.com/media/photo.jpg"))
        assertEquals(stored.captured.content, text.html)
    }

    @Test
    fun `the pictures a fetched article references are downloaded under the address it renders`() =
        runBlocking {
            coEvery { articleContentDao.getArticleContent(entryId) } returns null
            coEvery { backend.fetchFullContent(entryId) } returns """<img src="/media/photo.jpg">"""
            coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article())

            repository.getArticleContent(entryId, articleUrl)

            coVerify {
                articleImageRepository.downloadAndStoreImage(
                    entryId,
                    "https://example.com/media/photo.jpg"
                )
            }
        }

    @Test
    fun `the enclosure leads an article whose body does not carry it`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId) } returns "<p>Text with no pictures</p>"
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
            coEvery { backend.fetchFullContent(entryId) } returns
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
        coVerify(exactly = 0) { backend.fetchFullContent(any()) }
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
            // Its pictures were downloaded when it was first stored.
            coVerify(exactly = 0) { articleImageRepository.downloadAndStoreImage(any(), any()) }
            coVerify(exactly = 0) { backend.fetchFullContent(any()) }
        }

    @Test
    fun `an article the backend cannot serve falls back to what the feed carried`() = runBlocking {
        coEvery { articleContentDao.getArticleContent(entryId) } returns null
        coEvery { backend.fetchFullContent(entryId) } returns null
        coEvery { articleDao.getArticlesImmediate(any()) } returns listOf(article(content = "<p>Summary</p>"))

        val text = repository.getArticleContent(entryId, articleUrl)

        assertTrue(text.html.contains("Summary"))
    }
}
