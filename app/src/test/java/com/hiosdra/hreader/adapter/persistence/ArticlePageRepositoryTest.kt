package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import com.hiosdra.hreader.adapter.persistence.ArticlePageRepository
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArticlePageRepositoryTest {
    @Test
    fun `archives the document stylesheet and referenced resources`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleDao = mockk<ArticleDao>(relaxed = true)
            val snapshot = slot<ArticlePageSnapshot>()
            val repository = ArticlePageRepository(context, snapshotDao, articleDao, httpClient())

            repository.prefetchPages(listOf(42L to ARTICLE_URL))

            coVerify { snapshotDao.insert(capture(snapshot)) }
            val stored = snapshot.captured
            assertTrue(stored.isComplete)
            val html = File(stored.directoryPath, "index.html").readText()
            assertFalse(html.contains("<script"))
            assertFalse(html.contains("http-equiv=\"refresh\""))
            assertFalse(html.contains("rel=\"preload\""))
            assertTrue(html.contains("https://offline.hreader.local/article/42/assets/"))

            val css = File(stored.directoryPath, "assets")
                .walkTopDown()
                .filter { it.isFile && it.extension == "css" }
                .joinToString("\n") { it.readText() }
            assertTrue(css.contains("https://offline.hreader.local/article/42/assets/"))
            assertTrue(File(stored.directoryPath, "assets").listFiles()!!.size >= 4)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `does not expose a snapshot whose directory is outside the page cache`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        val outside = Files.createTempDirectory("hreader-outside").toFile()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleDao = mockk<ArticleDao>(relaxed = true)
            coEvery { snapshotDao.get(42L) } returns ArticlePageSnapshot(
                entryId = 42L,
                originalUrl = ARTICLE_URL,
                finalUrl = ARTICLE_URL,
                directoryPath = outside.absolutePath,
                fetchedAt = java.time.Instant.EPOCH,
                byteSize = 0,
                isComplete = true
            )
            val repository = ArticlePageRepository(context, snapshotDao, articleDao, httpClient())

            assertTrue(repository.getOfflinePage(42L, ARTICLE_URL) == null)
            coVerify { snapshotDao.deleteForEntries(listOf(42L)) }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun `treats a complete snapshot without its index as missing`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        try {
            val pageDirectory = File(root, "article_pages/42").apply { mkdirs() }
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleDao = mockk<ArticleDao>(relaxed = true)
            coEvery { snapshotDao.getAll() } returns listOf(
                ArticlePageSnapshot(
                    entryId = 42L,
                    originalUrl = ARTICLE_URL,
                    finalUrl = ARTICLE_URL,
                    directoryPath = pageDirectory.absolutePath,
                    fetchedAt = java.time.Instant.EPOCH,
                    byteSize = 0,
                    isComplete = true
                )
            )
            val repository = ArticlePageRepository(context, snapshotDao, articleDao, httpClient())

            assertEquals(
                listOf(42L to ARTICLE_URL),
                repository.entriesMissingPages(listOf(42L to ARTICLE_URL))
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `rejects an index that is too large to load into the reader`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        try {
            val pageDirectory = File(root, "article_pages/42").apply { mkdirs() }
            File(pageDirectory, "index.html").writeBytes(ByteArray(5 * 1024 * 1024 + 1))
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleDao = mockk<ArticleDao>(relaxed = true)
            coEvery { snapshotDao.get(42L) } returns ArticlePageSnapshot(
                entryId = 42L,
                originalUrl = ARTICLE_URL,
                finalUrl = ARTICLE_URL,
                directoryPath = pageDirectory.absolutePath,
                fetchedAt = java.time.Instant.EPOCH,
                byteSize = 5L * 1024 * 1024 + 1,
                isComplete = true
            )
            val repository = ArticlePageRepository(context, snapshotDao, articleDao, httpClient())

            assertTrue(repository.getOfflinePage(42L, ARTICLE_URL) == null)
            coVerify { snapshotDao.deleteForEntries(listOf(42L)) }
            assertFalse(pageDirectory.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val response = resources[request.url.encodedPath]
            if (response == null) {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", response.second)
                    .body(response.first.toResponseBody(response.second.toMediaType()))
                    .build()
            }
        })
        .build()

    private companion object {
        const val ARTICLE_URL = "https://example.com/articles/story"
        val resources: Map<String, Pair<String, String>> = mapOf(
            "/articles/story" to ("""
                <!doctype html>
                <html><head>
                    <meta http-equiv="refresh" content="0;url=/other">
                    <link rel="stylesheet" href="/styles/main.css">
                    <link rel="preload" href="/not-cached.js" as="script">
                    <script src="/app.js"></script>
                </head><body>
                    <img src="/images/hero.png">
                    <div style="background-image:url('/images/background.png')">Story</div>
                    <a href="/next">Next</a>
                </body></html>
            """.trimIndent() to "text/html; charset=utf-8"),
            "/styles/main.css" to ("""
                @import "/styles/theme.css";
                @font-face { src: url("../fonts/demo.woff2"); }
            """.trimIndent() to "text/css"),
            "/styles/theme.css" to ("body { background-image: url('/images/background.png'); }" to "text/css"),
            "/images/hero.png" to ("hero" to "image/png"),
            "/images/background.png" to ("background" to "image/png"),
            "/fonts/demo.woff2" to ("font" to "font/woff2")
        )
    }
}
