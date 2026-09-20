package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.network.HttpStatusException
import com.hiosdra.hreader.adapter.network.NonRetryableNetworkException
import com.hiosdra.hreader.adapter.network.RETRY_AFTER_HEADER
import com.hiosdra.hreader.adapter.network.withNetworkRetries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

internal data class ArchivedArticlePage(
    val finalUrl: String,
    val directoryPath: String,
    val byteSize: Long,
    val isComplete: Boolean
)

internal object ArticlePageFiles {
    const val OFFLINE_PAGE_HOST = "offline.hreader.local"
    const val INDEX_FILE = "index.html"
    const val ASSETS_DIRECTORY = "assets"
    const val MAX_HTML_BYTES = 5L * 1024 * 1024
    const val MAX_RESOURCE_BYTES = 5L * 1024 * 1024
    const val MAX_PAGE_BYTES = 50L * 1024 * 1024
    const val MAX_RESOURCES = 256
    const val MAX_CSS_DEPTH = 3

    fun baseUrl(entryId: Long): String =
        "https://$OFFLINE_PAGE_HOST/article/$entryId/"
}

internal class ArticlePageArchiver(
    private val files: ArticlePageFileStore,
    httpClient: OkHttpClient,
    private val remoteResourcePolicy: RemoteResourcePolicyAdapter
) {
    private val safeHttpClient = httpClient.newBuilder()
        .apply { interceptors().clear() }
        .addNetworkInterceptor { chain ->
            if (!remoteResourcePolicy.allows(chain.request().url.toString())) {
                throw NonRetryableNetworkException("Blocked remote resource URL")
            }
            chain.proceed(chain.request())
        }
        .dns(remoteResourcePolicy.dns())
        .build()

    suspend fun archive(
        entryId: Long,
        originalUrl: String,
        maxConcurrentResources: Int
    ): ArchivedArticlePage? = withContext(Dispatchers.IO) {
        if (!isHttpUrl(originalUrl)) return@withContext null

        val stagingDirectory = files.stagingDirectory(entryId)
        val assetsDirectory = File(stagingDirectory, ArticlePageFiles.ASSETS_DIRECTORY)
        stagingDirectory.mkdirs()
        assetsDirectory.mkdirs()

        try {
            val main = fetch(originalUrl, ArticlePageFiles.MAX_HTML_BYTES) ?: return@withContext null
            if (!isHtmlContentType(main.contentType)) return@withContext null
            val document = Jsoup.parse(main.bytes.toString(UTF_8), main.finalUrl)
            sanitize(document)
            val store = ResourceStore(
                entryId = entryId,
                assetsDirectory = assetsDirectory,
                initialBytes = main.bytes.size.toLong(),
                maxConcurrentResources = maxConcurrentResources
            )
            rewriteDocument(document, main.finalUrl, store)

            val htmlBytes = document.outerHtml().toByteArray(UTF_8)
            if (store.storedBytes + htmlBytes.size - main.bytes.size > ArticlePageFiles.MAX_PAGE_BYTES) {
                return@withContext null
            }
            File(stagingDirectory, ArticlePageFiles.INDEX_FILE).writeBytes(htmlBytes)
            files.replaceDirectory(entryId, stagingDirectory)
            val finalDirectory = files.finalDirectory(entryId)
            ArchivedArticlePage(
                finalUrl = main.finalUrl,
                directoryPath = finalDirectory.absolutePath,
                byteSize = finalDirectory.walkTopDown().filter(File::isFile).sumOf(File::length),
                isComplete = store.isComplete
            )
        } finally {
            if (stagingDirectory.exists()) stagingDirectory.deleteRecursively()
        }
    }

    private suspend fun rewriteDocument(document: Document, baseUrl: String, store: ResourceStore) =
        coroutineScope {
            val stylesheetElements = document.select("link[href]").toList()
            val stylesheetTasks = stylesheetElements.mapNotNull { element ->
                val rel = element.attr("rel").split(Regex("\\s+"))
                if (rel.any { it.equals("stylesheet", ignoreCase = true) }) {
                    element to async {
                        store.cache(resolveUrl(baseUrl, element.attr("href")), cssHint = true)
                    }
                } else {
                    element.remove()
                    null
                }
            }
            stylesheetTasks.forEach { (element, task) ->
                val localUrl = task.await()
                if (localUrl == null) element.remove() else element.attr("href", localUrl)
            }

            val imageElements = document.select("img[src], source[src]").toList()
            val imageUrls = imageElements.map { element ->
                async {
                    val source = element.attr("src")
                    if (isEmbeddedReference(source)) source.trim()
                    else store.cache(resolveUrl(baseUrl, source))
                }
            }.awaitAll()
            imageElements.zip(imageUrls).forEach { (element, localUrl) ->
                if (localUrl == null) element.removeAttr("src") else element.attr("src", localUrl)
            }

            val srcSetElements = document.select("img[srcset], source[srcset]").toList()
            val srcSets = srcSetElements.map { element ->
                async { rewriteSrcSet(element.attr("srcset"), baseUrl, store) }
            }.awaitAll()
            srcSetElements.zip(srcSets).forEach { (element, rewritten) ->
                if (rewritten.isBlank()) element.removeAttr("srcset") else element.attr("srcset", rewritten)
            }

            val styledElements = document.select("[style]").toList()
            val inlineStyles = styledElements.map { element ->
                async { store.rewriteCss(element.attr("style"), baseUrl, 0) }
            }.awaitAll()
            styledElements.zip(inlineStyles).forEach { (element, style) -> element.attr("style", style) }

            val styleElements = document.select("style").toList()
            val styleContents = styleElements.map { element ->
                async { store.rewriteCss(element.html(), baseUrl, 0) }
            }.awaitAll()
            styleElements.zip(styleContents).forEach { (element, style) -> element.html(style) }

            document.select("a[href]").toList().forEach { element ->
                resolveUrl(baseUrl, element.attr("href"))?.let { element.attr("href", it) }
            }
        }

    private suspend fun rewriteSrcSet(value: String, baseUrl: String, store: ResourceStore): String =
        coroutineScope {
            value.split(',')
                .map { candidate -> candidate.trim().split(Regex("\\s+"), limit = 2) }
                .map { parts ->
                    async {
                        val localUrl = store.cache(resolveUrl(baseUrl, parts.firstOrNull().orEmpty()))
                        localUrl?.let { if (parts.size == 1) it else "$it ${parts[1]}" }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .joinToString(", ")
        }

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

    private data class CachedResource(
        val offlineUrl: String,
        val file: File
    )

    private sealed class ResourceClaim {
        data class Cached(val resource: CachedResource) : ResourceClaim()
        data class Pending(val result: CompletableDeferred<CachedResource?>) : ResourceClaim()
        data class Owner(val result: CompletableDeferred<CachedResource?>) : ResourceClaim()
        data object Rejected : ResourceClaim()
    }

    private inner class ResourceStore(
        private val entryId: Long,
        private val assetsDirectory: File,
        initialBytes: Long,
        maxConcurrentResources: Int
    ) {
        private val resources = mutableMapOf<String, CachedResource>()
        private val inFlight = mutableMapOf<String, CompletableDeferred<CachedResource?>>()
        private val stateMutex = Mutex()
        private val resourceLimiter = Semaphore(maxConcurrentResources)
        var storedBytes = initialBytes
            private set
        var isComplete: Boolean = true
            private set

        suspend fun cache(
            url: String?,
            cssHint: Boolean = false,
            depth: Int = 0,
            ancestors: Set<String> = emptySet()
        ): String? {
            val normalized = url?.trim()?.takeIf(::isHttpUrl) ?: return null
            val claim = stateMutex.withLock {
                resources[normalized]?.let { return@withLock ResourceClaim.Cached(it) }
                if (normalized in ancestors) return@withLock ResourceClaim.Rejected
                inFlight[normalized]?.let { return@withLock ResourceClaim.Pending(it) }
                if (depth > ArticlePageFiles.MAX_CSS_DEPTH ||
                    resources.size + inFlight.size >= ArticlePageFiles.MAX_RESOURCES
                ) {
                    isComplete = false
                    return@withLock ResourceClaim.Rejected
                }
                CompletableDeferred<CachedResource?>().also { inFlight[normalized] = it }
                    .let(ResourceClaim::Owner)
            }

            val owner = when (claim) {
                is ResourceClaim.Cached -> return claim.resource.offlineUrl
                is ResourceClaim.Pending -> return claim.result.await()?.offlineUrl
                ResourceClaim.Rejected -> return null
                is ResourceClaim.Owner -> claim
            }

            var result: CachedResource? = null
            try {
                result = cacheResource(normalized, cssHint, depth, ancestors)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                markIncomplete()
            } finally {
                stateMutex.withLock { inFlight.remove(normalized) }
                owner.result.complete(result)
            }
            return result?.offlineUrl
        }

        suspend fun rewriteCss(
            value: String,
            baseUrl: String,
            depth: Int,
            ancestors: Set<String> = emptySet()
        ): String {
            var result = rewriteMatches(value, CSS_URL, baseUrl, depth, ancestors = ancestors)
            result = rewriteMatches(result, CSS_IMPORT, baseUrl, depth, importRule = true, ancestors = ancestors)
            return result
        }

        private suspend fun rewriteMatches(
            value: String,
            pattern: Regex,
            baseUrl: String,
            depth: Int,
            importRule: Boolean = false,
            ancestors: Set<String>
        ): String = coroutineScope {
            val matches = pattern.findAll(value).toList()
            if (matches.isEmpty()) return@coroutineScope value

            val replacements = matches.map { match ->
                async {
                    val original = match.groupValues[1]
                    val localUrl = if (isEmbeddedReference(original)) {
                        original.trim()
                    } else {
                        cache(
                            resolveUrl(baseUrl, original),
                            cssHint = importRule,
                            depth = depth,
                            ancestors = ancestors
                        )
                    }
                    if (localUrl == null) markIncomplete()
                    localUrl
                }
            }.awaitAll()

            val builder = StringBuilder(value.length)
            var cursor = 0
            matches.forEachIndexed { index, match ->
                builder.append(value, cursor, match.range.first)
                val localUrl = replacements[index]
                if (importRule) {
                    builder.append("@import \"").append(localUrl ?: "about:blank").append("\"")
                } else {
                    builder.append("url(\"").append(localUrl ?: "about:blank").append("\")")
                }
                cursor = match.range.last + 1
            }
            builder.append(value, cursor, value.length)
            builder.toString()
        }

        private suspend fun cacheResource(
            normalized: String,
            cssHint: Boolean,
            depth: Int,
            ancestors: Set<String>
        ): CachedResource? {
            val response = resourceLimiter.withPermit {
                fetch(normalized, ArticlePageFiles.MAX_RESOURCE_BYTES)
            } ?: return markIncompleteAndReturnNull()
            val isCss = cssHint || response.contentType?.startsWith("text/css", ignoreCase = true) == true ||
                response.finalUrl.substringBefore('?').endsWith(".css", ignoreCase = true)
            if (!isSupportedResourceType(response.contentType, response.finalUrl, isCss)) {
                return markIncompleteAndReturnNull()
            }
            val fileName = resourceFileName(normalized, response.contentType, isCss)
            val resource = CachedResource(
                offlineUrl = ArticlePageFiles.baseUrl(entryId) + ArticlePageFiles.ASSETS_DIRECTORY + "/" + fileName,
                file = File(assetsDirectory, fileName)
            )
            val bytes = if (isCss) {
                rewriteCss(
                    value = response.bytes.toString(UTF_8),
                    baseUrl = response.finalUrl,
                    depth = depth + 1,
                    ancestors = ancestors + normalized
                ).toByteArray(UTF_8)
            } else {
                response.bytes
            }
            return stateMutex.withLock {
                if (storedBytes + bytes.size > ArticlePageFiles.MAX_PAGE_BYTES) {
                    isComplete = false
                    null
                } else {
                    resource.file.writeBytes(bytes)
                    storedBytes += bytes.size
                    resources[normalized] = resource
                    resource
                }
            }
        }

        private suspend fun markIncompleteAndReturnNull(): CachedResource? {
            markIncomplete()
            return null
        }

        private suspend fun markIncomplete() {
            stateMutex.withLock { isComplete = false }
        }
    }

    private data class FetchedResource(
        val bytes: ByteArray,
        val finalUrl: String,
        val contentType: String?
    )

    private suspend fun fetch(url: String, maximumBytes: Long): FetchedResource? {
        if (!remoteResourcePolicy.allows(url)) return null
        return try {
            withNetworkRetries {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    safeHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw HttpStatusException(response.code, response.header(RETRY_AFTER_HEADER))
                        }
                        if (!remoteResourcePolicy.allows(response.request.url.toString())) return@use null
                        val body = response.body
                        if (body.contentLength() > maximumBytes) return@use null
                        val bytes = readAtMost(body.byteStream(), maximumBytes) ?: return@use null
                        FetchedResource(
                            bytes = bytes,
                            finalUrl = response.request.url.toString(),
                            contentType = body.contentType()?.toString()
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
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
                ?.let { ".${it}" }.orEmpty()
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

    private fun isHttpUrl(url: String): Boolean = remoteResourcePolicy.allows(url)

    private fun isEmbeddedReference(value: String): Boolean =
        value.trim().startsWith("data:", ignoreCase = true) || value.trim().startsWith("#")

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val CSS_URL = Regex("""url\(\s*['\"]?([^'\")]+)['\"]?\s*\)""", RegexOption.IGNORE_CASE)
        val CSS_IMPORT = Regex("""@import\s+['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
    }
}
