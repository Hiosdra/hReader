package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageFile
import com.hiosdra.hreader.adapter.persistence.ArticleImageRepository
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.time.Instant

@RunWith(JUnit4::class)
class ArticleImageRepositoryTest {
    private val context = mockk<Context>().apply {
        every { filesDir } returns File("/tmp/androidstudio")
    }
    private val articleImageDao = mockk<ArticleImageDao>(relaxed = true)
    private val okHttpClient = OkHttpClient()
    private val preferencesManager = mockk<SyncPreferences>(relaxed = true)
    private val repo: ArticleImageRepository = ArticleImageRepository(
        context,
        articleImageDao,
        okHttpClient,
        preferencesManager,
        RemoteResourcePolicyAdapter(allowedHosts = { setOf("example.com") })
    ) { path ->
        path == "/tmp/image.jpg" || path == "/tmp/orphan.jpg"
    }

    @Test
    fun getLocalImagePath_returnsPath_whenImageExists() = runBlocking {
        val entryId = 1L
        val imageUrl = "https://example.com/image.jpg"
        val localPath = "/tmp/image.jpg"
        val articleImage = ArticleImage("id", entryId, imageUrl, localPath, "image/jpeg", Instant.now(), 123)
        coEvery { articleImageDao.getImageForArticleByUrl(entryId, imageUrl) } returns articleImage
        val result = repo.getLocalImagePath(entryId, imageUrl)
        assertEquals(localPath, result)
    }

    @Test
    fun getLocalImagePath_returnsNull_whenImageDoesNotExist() = runBlocking {
        val entryId = 2L
        val imageUrl = "https://example.com/image2.jpg"
        coEvery { articleImageDao.getImageForArticleByUrl(entryId, imageUrl) } returns null
        val result = repo.getLocalImagePath(entryId, imageUrl)
        assertNull(result)
    }

    @Test
    fun getLocalImagePaths_batchRemovesMissingFiles() = runBlocking {
        val existing = ArticleImage(
            "existing",
            1L,
            "https://example.com/image.jpg",
            "/tmp/image.jpg",
            "image/jpeg",
            Instant.now(),
            123
        )
        val missing = ArticleImage(
            "missing",
            2L,
            "https://example.com/missing.jpg",
            "/tmp/missing.jpg",
            "image/jpeg",
            Instant.now(),
            456
        )
        coEvery { articleImageDao.getImagesForArticles(listOf(1L, 2L)) } returns listOf(existing, missing)

        val result = repo.getLocalImagePaths(listOf(1L, 2L))

        assertEquals(
            mapOf(1L to mapOf("https://example.com/image.jpg" to "/tmp/image.jpg")),
            result
        )
        coVerify { articleImageDao.deleteByIds(listOf("missing")) }
    }

    @Test
    fun cleanupOrphanedImages_deletesImagesNotInArticles() = runBlocking {
        coEvery { articleImageDao.getOrphanedImageFiles(500) } returnsMany listOf(
            listOf(ArticleImageFile("orphan", "/tmp/orphan.jpg")),
            emptyList()
        )

        repo.cleanupOrphanedImages()

        coVerify { articleImageDao.deleteByIds(listOf("orphan")) }
    }

    @Test
    fun cleanupOrphanedImages_keepsImagesOfArticlesStillCached() = runBlocking {
        coEvery { articleImageDao.getOrphanedImageFiles(500) } returns emptyList()

        repo.cleanupOrphanedImages()

        coVerify(exactly = 0) { articleImageDao.deleteByIds(any()) }
    }

    @Test
    fun cleanupOrphanedImages_removesManifestOnlyEntries() = runBlocking {
        coEvery { articleImageDao.getOrphanedImageFiles(500) } returns emptyList()
        coEvery { articleImageDao.getOrphanedExpectedEntryIds(500) } returnsMany listOf(
            listOf(99L),
            emptyList()
        )

        repo.cleanupOrphanedImages()

        coVerify { articleImageDao.deleteExpectedImagesForArticles(listOf(99L)) }
    }
}
