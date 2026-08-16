package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.domain.model.OfflinePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ArticlePageRepository(
    context: Context,
    private val snapshotDao: ArticlePageSnapshotDao,
    private val articleDao: ArticleDao,
    httpClient: OkHttpClient,
    private val remoteResourcePolicy: RemoteResourcePolicy
) : ArticlePageStore {
    companion object {
        const val OFFLINE_PAGE_HOST = "offline.hreader.local"

        private const val TAG = "ArticlePageRepository"
        private const val INDEX_FILE = "index.html"
        private const val ASSETS_DIRECTORY = "assets"
        private const val MAX_HTML_BYTES = 5L * 1024 * 1024
        private const val MAX_RESOURCE_BYTES = 5L * 1024 * 1024
        private const val MAX_PAGE_BYTES = 50L * 1024 * 1024
        private const val MAX_RESOURCES = 256
        private const val MAX_CSS_DEPTH = 3
        private const val DELETE_CHUNK = 500
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val CSS_URL = Regex("""url\(\s*['\"]?([^'\")]+)['\"]?\s*\)""", RegexOption.IGNORE_CASE)
        private val CSS_IMPORT = Regex("""@import\s+['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)

        fun baseUrl(entryId: Long): String =
            "https://$OFFLINE_PAGE_HOST/article/$entryId/"
    }

    private val pagesDirectory = File(context.filesDir, "article_pages").apply { mkdirs() }
    private val pageLimiter = Semaphore(4)
    private val safeHttpClient = httpClient.newBuilder()
        .addNetworkInterceptor { chain ->
            if (!remoteResourcePolicy.allows(chain.request().url.toString())) {
                throw IOException("Blocked remote resource URL")
            }
            chain.proceed(chain.request())
        }
        .dns(remoteResourcePolicy.dns())
        .build()

    override suspend fun getOfflinePage(entryId: Long, originalUrl: String): OfflinePage? =
        withContext(Dispatchers.IO) {
            val snapshot = snapshotDao.get(entryId) ?: return@withContext null
            if (snapshot.originalUrl != originalUrl) return@withContext null

            val directory = pageDirectory(entryId, snapshot.directoryPath) ?: run {
                snapshotDao.deleteForEntries(listOf(entryId))
                return@withContext null
            }
            val htmlFile = File(directory, INDEX_FILE)
            if (!htmlFile.isFile) {
                snapshotDao.deleteForEntries(listOf(entryId))
                directory.deleteRecursively()
                return@withContext null
            }
            if (htmlFile.length() > MAX_HTML_BYTES) {
                snapshotDao.deleteForEntries(listOf(entryId))
                directory.deleteRecursively()
                return@withContext null
            }

            val html = runCatching { htmlFile.readText(UTF_8) }.getOrNull()
                ?: return@withContext null
            OfflinePage(
                entryId = entryId,
                originalUrl = snapshot.originalUrl,
                baseUrl = baseUrl(entryId),
                html = html,
                resourceDirectory = directory.absolutePath,
                isComplete = snapshot.isComplete
            )
        }

    override suspend fun entriesMissingPages(entries: List<Pair<Long, String>>): List<Pair<Long, String>> =
        withContext(Dispatchers.IO) {
            val stored = snapshotDao.getAll()
                .filter { snapshot ->
                    snapshot.isComplete &&
                        pageDirectory(snapshot.entryId, snapshot.directoryPath)
                            ?.resolve(INDEX_FILE)
                            ?.isFile == true
                }
                .associate { it.entryId to it.originalUrl }
            entries.filterNot { (entryId, url) -> stored[entryId] == url }
        }

    override suspend fun prefetchPages(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        onProgress: (done: Int, total: Int) -> Unit
    ) = coroutineScope {
        val selected = if (limit == null) entries else entries.take(limit)
        val total = selected.size
        val done = AtomicInteger()
        selected.map { (entryId, url) ->
            async(Dispatchers.IO) {
                pageLimiter.withPermit {
                    try {
                        downloadPage(entryId, url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to archive page for entry $entryId", e)
                    }
                }
                onProgress(done.incrementAndGet(), total)
            }
        }.awaitAll()
        Unit
    }

    override suspend fun cleanupOrphanedPages() = withContext(Dispatchers.IO) {
        val currentEntryIds = articleDao.getAllIds().toHashSet()
        val snapshots = snapshotDao.getAll()
        val invalidSnapshots = snapshots.filterNot { snapshot ->
            pageDirectory(snapshot.entryId, snapshot.directoryPath)
                ?.resolve(INDEX_FILE)
                ?.isFile == true
        }
        invalidSnapshots.mapNotNull { snapshot ->
            pageDirectory(snapshot.entryId, snapshot.directoryPath)
        }.forEach(File::deleteRecursively)
        invalidSnapshots.map { it.entryId }.chunked(DELETE_CHUNK).forEach { chunk ->
            snapshotDao.deleteForEntries(chunk)
        }
        val validSnapshots = snapshots - invalidSnapshots.toSet()
        val snapshotsById = validSnapshots.associateBy { it.entryId }
        val orphaned = snapshotsById.keys.filterNot(currentEntryIds::contains)
        orphaned.chunked(DELETE_CHUNK).forEach { chunk ->
            chunk.mapNotNull { entryId -> snapshotsById[entryId] }
                .mapNotNull { snapshot -> pageDirectory(snapshot.entryId, snapshot.directoryPath) }
                .forEach(File::deleteRecursively)
            snapshotDao.deleteForEntries(chunk)
        }

        val referencedDirectories = validSnapshots.mapNotNull { snapshot ->
            pageDirectory(snapshot.entryId, snapshot.directoryPath)?.canonicalPath
        }.toSet()
        pagesDirectory.listFiles()
            ?.filterNot { file ->
                runCatching { file.canonicalPath in referencedDirectories }.getOrDefault(true)
            }
            ?.forEach(File::deleteRecursively)
        Unit
    }

    private fun pageDirectory(entryId: Long, path: String): File? {
        val root = runCatching { pagesDirectory.canonicalFile }.getOrNull() ?: return null
        val expected = runCatching { File(root, entryId.toString()).canonicalFile }.getOrNull() ?: return null
        val directory = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return directory.takeIf { it == expected }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        pagesDirectory.listFiles()?.forEach(File::deleteRecursively)
        snapshotDao.clearAll()
        Unit
    }

    suspend fun prefetchPages(entries: List<Pair<Long, String>>): Unit =
        prefetchPages(entries, limit = null, onProgress = { _, _ -> })

    private suspend fun downloadPage(entryId: Long, originalUrl: String): ArticlePageSnapshot? =
        withContext(Dispatchers.IO) {
            if (!isHttpUrl(originalUrl)) return@withContext null

            val stagingDirectory = File(pagesDirectory, ".staging-$entryId-${UUID.randomUUID()}")
            val assetsDirectory = File(stagingDirectory, ASSETS_DIRECTORY)
            stagingDirectory.mkdirs()
            assetsDirectory.mkdirs()

            try {
                val main = fetch(originalUrl, MAX_HTML_BYTES) ?: return@withContext null
                if (!isHtmlContentType(main.contentType)) return@withContext null
                val document = Jsoup.parse(main.bytes.toString(UTF_8), main.finalUrl)
                sanitize(document)
                val store = ResourceStore(entryId, assetsDirectory, main.bytes.size.toLong())
                rewriteDocument(document, main.finalUrl, store)

                val html = document.outerHtml()
                val htmlBytes = html.toByteArray(UTF_8)
                if (store.storedBytes + htmlBytes.size - main.bytes.size > MAX_PAGE_BYTES) {
                    return@withContext null
                }
                File(stagingDirectory, INDEX_FILE).writeBytes(htmlBytes)
                val finalDirectory = File(pagesDirectory, entryId.toString())
                replaceDirectory(entryId, stagingDirectory, finalDirectory)

                val snapshot = ArticlePageSnapshot(
                    entryId = entryId,
                    originalUrl = originalUrl,
                    finalUrl = main.finalUrl,
                    directoryPath = finalDirectory.absolutePath,
                    fetchedAt = Instant.now(),
                    byteSize = finalDirectory.walkTopDown()
                        .filter(File::isFile)
                        .sumOf(File::length),
                    isComplete = store.isComplete
                )
                snapshotDao.insert(snapshot)
                snapshot
            } finally {
                if (stagingDirectory.exists()) stagingDirectory.deleteRecursively()
            }
        }

    private suspend fun rewriteDocument(document: Document, baseUrl: String, store: ResourceStore) {
        document.select("link[href]").toList().forEach { element ->
            val rel = element.attr("rel").split(Regex("\\s+"))
            if (rel.any { it.equals("stylesheet", ignoreCase = true) }) {
                val localUrl = store.cache(resolveUrl(baseUrl, element.attr("href")), cssHint = true)
                if (localUrl == null) element.remove() else element.attr("href", localUrl)
            } else {
                element.remove()
            }
        }

        document.select("img[src], source[src]").toList().forEach { element ->
            val source = element.attr("src")
            val localUrl = if (isEmbeddedReference(source)) {
                source.trim()
            } else {
                store.cache(resolveUrl(baseUrl, source))
            }
            if (localUrl == null) element.removeAttr("src") else element.attr("src", localUrl)
        }

        document.select("img[srcset], source[srcset]").toList().forEach { element ->
            val rewritten = rewriteSrcSet(element.attr("srcset"), baseUrl, store)
            if (rewritten.isBlank()) element.removeAttr("srcset") else element.attr("srcset", rewritten)
        }

        document.select("[style]").toList().forEach { element ->
            element.attr("style", store.rewriteCss(element.attr("style"), baseUrl, 0))
        }
        document.select("style").toList().forEach { element ->
            element.html(store.rewriteCss(element.html(), baseUrl, 0))
        }
        document.select("a[href]").toList().forEach { element ->
            resolveUrl(baseUrl, element.attr("href"))?.let { element.attr("href", it) }
        }
    }

    private suspend fun rewriteSrcSet(value: String, baseUrl: String, store: ResourceStore): String =
        value.split(',').mapNotNull { candidate ->
            val parts = candidate.trim().split(Regex("\\s+"), limit = 2)
            val localUrl = store.cache(resolveUrl(baseUrl, parts.firstOrNull().orEmpty())) ?: return@mapNotNull null
            if (parts.size == 1) localUrl else "$localUrl ${parts[1]}"
        }.joinToString(", ")

    private fun sanitize(document: Document) {
        document.select("base, script, noscript, iframe, frame, object, embed, video, audio, form").remove()
        document.select("meta[http-equiv]").filter {
            it.attr("http-equiv").equals("refresh", ignoreCase = true) ||
                it.attr("http-equiv").equals("content-security-policy", ignoreCase = true)
        }.forEach(Element::remove)
        document.allElements.forEach { element ->
            element.attributes().filter { it.key.startsWith("on", ignoreCase = true) }
                .forEach { element.removeAttr(it.key) }
        }
    }

    private inner class ResourceStore(
        private val entryId: Long,
        private val assetsDirectory: File,
        initialBytes: Long
    ) {
        private val resources = mutableMapOf<String, CachedResource>()
        var storedBytes: Long = initialBytes
            private set
        var isComplete: Boolean = true
            private set

        suspend fun cache(url: String?, cssHint: Boolean = false, depth: Int = 0): String? {
            val normalized = url?.trim()?.takeIf(::isHttpUrl) ?: return null
            resources[normalized]?.let { return it.offlineUrl }
            if (depth > MAX_CSS_DEPTH || resources.size >= MAX_RESOURCES) {
                isComplete = false
                return null
            }

            val response = fetch(normalized, MAX_RESOURCE_BYTES) ?: run {
                isComplete = false
                return null
            }
            val isCss = cssHint || response.contentType?.startsWith("text/css", ignoreCase = true) == true ||
                response.finalUrl.substringBefore('?').endsWith(".css", ignoreCase = true)
            if (!isSupportedResourceType(response.contentType, response.finalUrl, isCss)) {
                isComplete = false
                return null
            }
            val fileName = resourceFileName(normalized, response.contentType, isCss)
            val resource = CachedResource(
                offlineUrl = baseUrl(entryId) + ASSETS_DIRECTORY + "/" + fileName,
                file = File(assetsDirectory, fileName)
            )
            resources[normalized] = resource

            val bytes = if (isCss) {
                rewriteCss(response.bytes.toString(UTF_8), response.finalUrl, depth + 1).toByteArray(UTF_8)
            } else {
                response.bytes
            }
            if (storedBytes + bytes.size > MAX_PAGE_BYTES) {
                resources.remove(normalized)
                isComplete = false
                return null
            }
            resource.file.writeBytes(bytes)
            storedBytes += bytes.size
            return resource.offlineUrl
        }

        suspend fun rewriteCss(value: String, baseUrl: String, depth: Int): String {
            var result = rewriteMatches(value, CSS_URL, baseUrl, depth)
            result = rewriteMatches(result, CSS_IMPORT, baseUrl, depth, importRule = true)
            return result
        }

        private suspend fun rewriteMatches(
            value: String,
            pattern: Regex,
            baseUrl: String,
            depth: Int,
            importRule: Boolean = false
        ): String {
            val matches = pattern.findAll(value).toList()
            if (matches.isEmpty()) return value
            val builder = StringBuilder(value.length)
            var cursor = 0
            matches.forEach { match ->
                builder.append(value, cursor, match.range.first)
                val original = match.groupValues[1]
                val localUrl = if (isEmbeddedReference(original)) {
                    original.trim()
                } else {
                    cache(resolveUrl(baseUrl, original), cssHint = importRule, depth = depth)
                }
                if (localUrl == null) isComplete = false
                if (importRule) {
                    builder.append("@import \"").append(localUrl ?: "about:blank").append("\"")
                } else {
                    builder.append("url(\"").append(localUrl ?: "about:blank").append("\")")
                }
                cursor = match.range.last + 1
            }
            builder.append(value, cursor, value.length)
            return builder.toString()
        }
    }

    private data class CachedResource(
        val offlineUrl: String,
        val file: File
    )

    private data class FetchedResource(
        val bytes: ByteArray,
        val finalUrl: String,
        val contentType: String?
    )

    private suspend fun fetch(url: String, maximumBytes: Long): FetchedResource? =
        withContext(Dispatchers.IO) {
            if (!remoteResourcePolicy.allows(url)) return@withContext null
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
                safeHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    if (!remoteResourcePolicy.allows(response.request.url.toString())) {
                        return@withContext null
                    }
                    val body = response.body
                    if (body.contentLength() > maximumBytes) return@withContext null
                    val bytes = readAtMost(body.byteStream(), maximumBytes) ?: return@withContext null
                    FetchedResource(
                        bytes = bytes,
                        finalUrl = response.request.url.toString(),
                        contentType = body.contentType()?.toString()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun replaceDirectory(entryId: Long, stagingDirectory: File, finalDirectory: File) {
        val backupDirectory = File(pagesDirectory, ".backup-$entryId-${UUID.randomUUID()}")
        var movedExistingDirectory = false
        try {
            if (finalDirectory.exists()) {
                check(finalDirectory.renameTo(backupDirectory)) {
                    "Could not preserve existing offline page $entryId"
                }
                movedExistingDirectory = true
            }
            check(stagingDirectory.renameTo(finalDirectory)) {
                "Could not commit offline page $entryId"
            }
            if (movedExistingDirectory) backupDirectory.deleteRecursively()
        } catch (failure: Throwable) {
            if (movedExistingDirectory) {
                finalDirectory.deleteRecursively()
                if (backupDirectory.exists()) {
                    check(backupDirectory.renameTo(finalDirectory)) {
                        "Could not restore existing offline page $entryId"
                    }
                }
            }
            throw failure
        }
    }

    private fun readAtMost(input: java.io.InputStream, maximumBytes: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        input.use { source ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                if (total > maximumBytes) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun resourceFileName(url: String, contentType: String?, css: Boolean): String {
        val extension = when {
            css -> ".css"
            contentType?.contains("html", ignoreCase = true) == true -> ".html"
            contentType?.contains("javascript", ignoreCase = true) == true -> ".js"
            contentType?.contains("svg", ignoreCase = true) == true -> ".svg"
            contentType?.contains("png", ignoreCase = true) == true -> ".png"
            contentType?.contains("webp", ignoreCase = true) == true -> ".webp"
            contentType?.contains("gif", ignoreCase = true) == true -> ".gif"
            contentType?.contains("jpeg", ignoreCase = true) == true -> ".jpg"
            contentType?.contains("woff2", ignoreCase = true) == true -> ".woff2"
            contentType?.contains("woff", ignoreCase = true) == true -> ".woff"
            contentType?.contains("truetype", ignoreCase = true) == true -> ".ttf"
            contentType?.contains("opentype", ignoreCase = true) == true -> ".otf"
            contentType?.contains("font", ignoreCase = true) == true -> ".font"
            else -> URI(url).path.substringAfterLast('.', "").takeIf { it.length in 1..8 }
                ?.let { ".$it" }.orEmpty()
        }
        return sha256(url).take(32) + extension
    }

    private fun isHtmlContentType(contentType: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return true
        return type == "text/html" || type == "application/xhtml+xml"
    }

    private fun isSupportedResourceType(contentType: String?, url: String, css: Boolean): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (css) return type == null || type == "text/css"
        if (type == null) {
            val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            return extension in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "woff", "woff2", "ttf", "otf")
        }
        return type.startsWith("image/") ||
            type.startsWith("font/") ||
            type in setOf(
                "application/font-sfnt",
                "application/vnd.ms-fontobject",
                "application/x-font-opentype",
                "application/x-font-ttf",
                "application/x-font-woff"
            )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun resolveUrl(baseUrl: String, value: String): String? {
        val reference = value.trim()
        if (reference.isBlank() || reference.startsWith("#") || reference.startsWith("data:", true) ||
            reference.startsWith("blob:", true) || reference.startsWith("javascript:", true) ||
            reference.startsWith("mailto:", true)
        ) return null
        return runCatching { URI(baseUrl).resolve(reference).toString() }
            .getOrNull()
            ?.takeIf(::isHttpUrl)
    }

    private fun isHttpUrl(url: String): Boolean =
        remoteResourcePolicy.allows(url)

    private fun isEmbeddedReference(value: String): Boolean =
        value.trim().startsWith("data:", ignoreCase = true) || value.trim().startsWith("#")
}
