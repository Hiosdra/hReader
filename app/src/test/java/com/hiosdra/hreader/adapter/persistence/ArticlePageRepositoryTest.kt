package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import com.hiosdra.hreader.adapter.persistence.ArticlePageRepository
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMaintenanceDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import com.sun.net.httpserver.HttpServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.hiosdra.hreader.core.application.sync.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ArticlePageRepositoryTest {
    @Test
    fun `offline page keeps its final address and saved time`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        try {
            val pageDirectory = File(root, "article_pages/42").apply { mkdirs() }
            File(pageDirectory, "index.html").writeText("<p>Saved</p>")
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            val fetchedAt = Instant.parse("2026-09-01T12:34:56Z")
            coEvery { snapshotDao.get(42L) } returns ArticlePageSnapshot(
                entryId = 42L,
                originalUrl = ARTICLE_URL,
                finalUrl = "https://example.com/story?redirected=1",
                directoryPath = pageDirectory.absolutePath,
                fetchedAt = fetchedAt,
                byteSize = 12,
                isComplete = true
            )
            val page = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy()
            )
                .getOfflinePage(42L, ARTICLE_URL)

            assertEquals("https://example.com/story?redirected=1", page?.finalUrl)
            assertEquals(fetchedAt, page?.fetchedAt)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `archives the document stylesheet and referenced resources`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        val server = localServer()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            val snapshot = slot<ArticlePageSnapshot>()
            val articleUrl = "http://127.0.0.1:${server.address.port}/articles/story"
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy("127.0.0.1", InetAddress.getByName("127.0.0.1"))
            )

            repository.prefetchPages(listOf(42L to articleUrl))

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
            server.stop(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun `fast page prefetch allows four resources per page`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newFixedThreadPool(8)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val resourcesStarted = CountDownLatch(SyncMode.FAST.maxConcurrentPageResources)
        val releaseResources = CountDownLatch(1)
        val articleHtml = (1..5).joinToString(
            prefix = "<html><body>",
            postfix = "</body></html>"
        ) { image -> "<img src=\"/images/$image.png\">" }
        server.createContext("/articles/story") { exchange ->
            val bytes = articleHtml.toByteArray(UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        (1..5).forEach { image ->
            server.createContext("/images/$image.png") { exchange ->
                val current = active.incrementAndGet()
                maximum.getAndUpdate { previous -> maxOf(previous, current) }
                resourcesStarted.countDown()
                try {
                    releaseResources.await(10, TimeUnit.SECONDS)
                    val bytes = "image-$image".toByteArray(UTF_8)
                    exchange.responseHeaders.add("Content-Type", "image/png")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        server.executor = executor
        server.start()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy("127.0.0.1", InetAddress.getByName("127.0.0.1"))
            )
            val articleUrl = "http://127.0.0.1:${server.address.port}/articles/story"
            val prefetch = async(Dispatchers.IO) {
                repository.prefetchPages(
                    entries = listOf(42L to articleUrl),
                    syncMode = SyncMode.FAST
                )
            }

            assertTrue(resourcesStarted.await(10, TimeUnit.SECONDS))
            releaseResources.countDown()
            prefetch.await()

            assertEquals(SyncMode.FAST.maxConcurrentPageResources, maximum.get())
        } finally {
            releaseResources.countDown()
            server.stop(0)
            executor.shutdownNow()
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
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            coEvery { snapshotDao.get(42L) } returns ArticlePageSnapshot(
                entryId = 42L,
                originalUrl = ARTICLE_URL,
                finalUrl = ARTICLE_URL,
                directoryPath = outside.absolutePath,
                fetchedAt = java.time.Instant.EPOCH,
                byteSize = 0,
                isComplete = true
            )
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy()
            )

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
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
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
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy()
            )

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
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            coEvery { snapshotDao.get(42L) } returns ArticlePageSnapshot(
                entryId = 42L,
                originalUrl = ARTICLE_URL,
                finalUrl = ARTICLE_URL,
                directoryPath = pageDirectory.absolutePath,
                fetchedAt = java.time.Instant.EPOCH,
                byteSize = 5L * 1024 * 1024 + 1,
                isComplete = true
            )
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                policy()
            )

            assertTrue(repository.getOfflinePage(42L, ARTICLE_URL) == null)
            coVerify { snapshotDao.deleteForEntries(listOf(42L)) }
            assertFalse(pageDirectory.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `keeps a page snapshot and temporary directories while a replacement is in progress`() =
        runBlocking {
            val root = Files.createTempDirectory("hreader-pages").toFile()
            try {
                val pagesDirectory = File(root, "article_pages").apply { mkdirs() }
                val stagingDirectory = File(pagesDirectory, ".staging-42-replacement").apply { mkdirs() }
                val snapshotDirectory = File(pagesDirectory, "42")
                val context = mockk<Context>()
                every { context.filesDir } returns root
                val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
                val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
                val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
                coEvery { articleRecordDao.getExistingIds(listOf("42")) } returns listOf("42")
                coEvery { snapshotDao.getBatch(Long.MIN_VALUE, 500) } returns listOf(
                    ArticlePageSnapshot(
                        entryId = 42L,
                        originalUrl = ARTICLE_URL,
                        finalUrl = ARTICLE_URL,
                        directoryPath = snapshotDirectory.absolutePath,
                        fetchedAt = java.time.Instant.EPOCH,
                        byteSize = 0,
                        isComplete = true
                    )
                )
                val repository = ArticlePageRepository(
                    context,
                    snapshotDao,
                    articleMaintenanceDao,
                    articleRecordDao,
                    httpClient(),
                    policy()
                )

                ArticlePageCacheMaintenance(
                    snapshotDao = snapshotDao,
                    articleRecordDao = articleRecordDao,
                    files = ArticlePageFileStore(context)
                ).cleanupOrphaned()

                assertTrue(stagingDirectory.exists())
                coVerify(exactly = 0) { snapshotDao.deleteForEntries(any()) }
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `rejects a redirect to a host outside the resource policy`() = runBlocking {
        val root = Files.createTempDirectory("hreader-pages").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add(
                "Location",
                "http://blocked.example:${server.address.port}/article"
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            val context = mockk<Context>()
            every { context.filesDir } returns root
            val snapshotDao = mockk<ArticlePageSnapshotDao>(relaxed = true)
            val articleMaintenanceDao = mockk<ArticleMaintenanceDao>(relaxed = true)
            val articleRecordDao = mockk<ArticleRecordDao>(relaxed = true)
            val repository = ArticlePageRepository(
                context,
                snapshotDao,
                articleMaintenanceDao,
                articleRecordDao,
                httpClient(),
                RemoteResourcePolicyAdapter(
                    allowedHosts = { setOf("127.0.0.1") },
                    resolveHost = { host ->
                        if (host == "127.0.0.1") {
                            listOf(InetAddress.getByName("127.0.0.1"))
                        } else {
                            listOf(InetAddress.getByName("10.0.0.4"))
                        }
                    }
                )
            )

            repository.prefetchPages(
                listOf("http://127.0.0.1:${server.address.port}/redirect".let { 42L to it })
            )

            coVerify(exactly = 0) { snapshotDao.insert(any()) }
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { error("Application interceptors must not reach article resources") })
        .build()

    private fun policy(
        host: String = "example.com",
        address: InetAddress = InetAddress.getByName("93.184.216.34")
    ) = RemoteResourcePolicyAdapter(
        allowedHosts = { setOf(host) },
        resolveHost = { listOf(address) }
    )

    private fun localServer(): HttpServer = HttpServer.create(
        InetSocketAddress("127.0.0.1", 0),
        0
    ).also { server ->
        resources.forEach { (path, response) ->
            server.createContext(path) { exchange ->
                val bytes = response.first.toByteArray(UTF_8)
                exchange.responseHeaders.add("Content-Type", response.second)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
    }

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
