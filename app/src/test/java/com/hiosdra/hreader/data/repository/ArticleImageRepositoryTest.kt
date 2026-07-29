package com.hiosdra.hreader.data.repository

import android.content.Context
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.entity.ArticleImage
import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import com.hiosdra.hreader.data.preferences.PreferencesManager
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
    private val articleImageDao = mockk<ArticleImageDao>()
    private val articleDao = mockk<ArticleDao>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val preferencesManager = mockk<PreferencesManager>(relaxed = true)
    private val repo: ArticleImageRepository = ArticleImageRepository(
        context,
        articleImageDao,
        articleDao,
        okHttpClient,
        preferencesManager
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
        println("Running cleanupOrphanedImages_deletesImagesNotInArticles")
        val orphanImage = ArticleImage("id", 99L, "url", "/tmp/orphan.jpg", "image/jpeg", Instant.now(), 123)
        coEvery { articleImageDao.getAllArticleImages() } returns listOf(orphanImage)
        coEvery { articleDao.getAllArticlesOldestFirst() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { articleImageDao.deleteArticleImage(orphanImage) } returns Unit
        repo.cleanupOrphanedImages()
        coVerify { articleImageDao.deleteArticleImage(orphanImage) }
    }
}
