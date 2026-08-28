package com.hiosdra.hreader.adapter.image

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

private const val MAX_DOWNLOAD_BYTES = 16L * 1024 * 1024

class ArticleImageDownloadService(
    context: Context,
    httpClient: OkHttpClient,
    private val remoteResourcePolicy: RemoteResourcePolicy
) : ArticleImageDownloader {
    private val appContext = context.applicationContext
    private val imageDownloadClient = httpClient.newBuilder()
        .apply { interceptors().clear() }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun download(url: String): Boolean = withContext(Dispatchers.IO) {
        if (!remoteResourcePolicy.allows(url)) return@withContext false

        try {
            val request = Request.Builder().url(url).build()
            imageDownloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || !remoteResourcePolicy.allows(response.request.url.toString())) {
                    return@withContext false
                }
                val body = response.body
                val contentType = body.contentType()?.toString()
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                    ?: return@withContext false
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withContext false

                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeImageFileName(url, contentType))
                    put(MediaStore.Downloads.MIME_TYPE, contentType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                var completed = false
                try {
                    val copied = resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input -> copyAtMost(input, output, MAX_DOWNLOAD_BYTES) }
                    }
                    if (copied == null) return@withContext false
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                        null,
                        null
                    )
                    completed = true
                    true
                } finally {
                    if (!completed) resolver.delete(uri, null, null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

private fun safeImageFileName(url: String, contentType: String): String {
    val segment = runCatching { url.toUri().lastPathSegment }.getOrNull().orEmpty()
    val sanitized = segment
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
        .take(80)
    val base = sanitized.ifBlank { "image_${System.currentTimeMillis()}" }
    val extension = when {
        contentType.contains("png", ignoreCase = true) -> ".png"
        contentType.contains("webp", ignoreCase = true) -> ".webp"
        contentType.contains("gif", ignoreCase = true) -> ".gif"
        contentType.contains("svg", ignoreCase = true) -> ".svg"
        else -> ".jpg"
    }
    return if (base.endsWith(extension, ignoreCase = true)) base else base + extension
}

private fun copyAtMost(input: InputStream, output: OutputStream, limit: Long): Long? {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return copied
        copied += read
        if (copied > limit) return null
        output.write(buffer, 0, read)
    }
}
