package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
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
    private val articleDao = mockk<ArticleDao>()
    private val okHttpClient = OkHttpClient()
    private val preferencesManager = mockk<SyncPreferences>(relaxed = true)
    private val repo: ArticleImageRepository = ArticleImageRepository(
        context,
        articleImageDao,
        articleDao,
        okHttpClient,
        preferencesManager,
        RemoteResourcePolicy(allowedHosts = { setOf("example.com") })
    ) { path ->
        println("fileExists called with: $path")
        path == "/tmp/image.jpg" || path == "/tmp/orphan.jpg"
    }

    @Test
    fun getLocalImagePath_returnsPath_whenImageExists() = runBlocking {
        println("Running getLocalImagePath_returnsPath_whenImageExists")
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
        println("Running getLocalImagePath_returnsNull_whenImageDoesNotExist")
        val entryId = 2L
        val imageUrl = "https://example.com/image2.jpg"
        coEvery { articleImageDao.getImageForArticleByUrl(entryId, imageUrl) } returns null
        val result = repo.getLocalImagePath(entryId, imageUrl)
        assertNull(result)
    }

    @Test
    fun cleanupOrphanedImages_deletesImagesNotInArticles() = runBlocking {
        coEvery { articleImageDao.getAllImageEntryIds() } returns listOf(99L)
        coEvery { articleDao.getAllIds() } returns emptyList()
        coEvery { articleImageDao.getImagePathsForArticles(listOf(99L)) } returns listOf("/tmp/orphan.jpg")
        coEvery { articleImageDao.deleteImagesForArticles(listOf(99L)) } returns Unit

        repo.cleanupOrphanedImages()

        coVerify { articleImageDao.deleteImagesForArticles(listOf(99L)) }
    }

    @Test
    fun cleanupOrphanedImages_keepsImagesOfArticlesStillCached() = runBlocking {
        coEvery { articleImageDao.getAllImageEntryIds() } returns listOf(7L)
        coEvery { articleImageDao.getAllExpectedImageEntryIds() } returns emptyList()
        coEvery { articleDao.getAllIds() } returns listOf("7")

        repo.cleanupOrphanedImages()

        coVerify(exactly = 0) { articleImageDao.deleteImagesForArticles(any()) }
    }

    @Test
    fun cleanupOrphanedImages_removesManifestOnlyEntries() = runBlocking {
        coEvery { articleImageDao.getAllImageEntryIds() } returns emptyList()
        coEvery { articleImageDao.getAllExpectedImageEntryIds() } returns listOf(99L)
        coEvery { articleDao.getAllIds() } returns emptyList()

        repo.cleanupOrphanedImages()

        coVerify { articleImageDao.deleteExpectedImagesForArticles(listOf(99L)) }
    }
}
