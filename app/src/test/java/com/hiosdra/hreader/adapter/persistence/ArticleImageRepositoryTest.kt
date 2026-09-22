package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.ArticleImageRepository
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.sun.net.httpserver.HttpServer
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class ArticleImageRepositoryTest {
    private val context = mockk<Context>().apply {
        every { filesDir } returns File("/tmp/androidstudio")
    }
    private val articleImageDao = mockk<ArticleImageDao>(relaxed = true)
    private val okHttpClient = OkHttpClient()
    private val preferencesManager = mockk<SyncPreferences>(relaxed = true)
    private val imageIndex = ArticleImageIndex(articleImageDao)
    private val imageFiles = ArticleImageFileStore(context) { path ->
        path == "/tmp/image.jpg" || path == "/tmp/orphan.jpg"
    }
    private val imageMaintenance = ArticleImageMaintenance(
        imageIndex,
        imageFiles,
        preferencesManager
    )
    private val repo: ArticleImageRepository = ArticleImageRepository(
        imageIndex,
        ArticleImageRemoteDownloader(
            okHttpClient,
            RemoteResourcePolicyAdapter(allowedHosts = { setOf("example.com") })
        ),
        imageFiles,
        preferencesManager
    )

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
        coEvery { articleImageDao.getOrphanedImageEntryIds(500) } returnsMany listOf(listOf(99L), emptyList())
        coEvery { articleImageDao.getImagePathsForArticles(listOf(99L)) } returns listOf("/tmp/orphan.jpg")
        coEvery { articleImageDao.deleteImagesForArticles(listOf(99L)) } returns Unit

        imageMaintenance.cleanupOrphaned()

        coVerify { articleImageDao.deleteImagesForArticles(listOf(99L)) }
    }

    @Test
    fun cleanupOrphanedImages_keepsImagesOfArticlesStillCached() = runBlocking {
        coEvery { articleImageDao.getOrphanedImageEntryIds(500) } returns emptyList()
        coEvery { articleImageDao.getOrphanedExpectedEntryIds(500) } returns emptyList()

        imageMaintenance.cleanupOrphaned()

        coVerify(exactly = 0) { articleImageDao.deleteImagesForArticles(any()) }
    }

    @Test
    fun cleanupOrphanedImages_removesManifestOnlyEntries() = runBlocking {
        coEvery { articleImageDao.getOrphanedImageEntryIds(500) } returns emptyList()
        coEvery { articleImageDao.getOrphanedExpectedEntryIds(500) } returnsMany listOf(listOf(99L), emptyList())

        imageMaintenance.cleanupOrphaned()

        coVerify { articleImageDao.deleteExpectedImagesForArticles(listOf(99L)) }
    }

    @Test
    fun remoteDownloader_doesNotInheritNetworkCredentials() = runBlocking {
        val receivedToken = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { httpServer ->
            httpServer.createContext("/image.png") { exchange ->
                receivedToken.set(exchange.requestHeaders.getFirst("X-Auth-Token"))
                val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            httpServer.start()
        }
        val staging = Files.createTempFile("hreader-image", ".tmp").toFile()
        try {
            val client = OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("X-Auth-Token", "must-not-leak")
                            .build()
                    )
                }
                .build()
            val policy = RemoteResourcePolicyAdapter(
                allowedHosts = { setOf("127.0.0.1") },
                resolveHost = { listOf(InetAddress.getByName("127.0.0.1")) }
            )

            val result = ArticleImageRemoteDownloader(client, policy).download(
                "http://127.0.0.1:${server.address.port}/image.png",
                staging
            )

            assertEquals("image/png", result?.contentType)
            assertNull(receivedToken.get())
        } finally {
            server.stop(0)
            staging.delete()
        }
    }
}
