package com.hiosdra.hreader.adapter.image

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val MAX_SHARED_IMAGE_BYTES = 16L * 1024 * 1024
private const val MAX_SHARED_IMAGE_FILES = 8

class ArticleImageShareService(context: Context) : ArticleImageSharer {
    private val appContext = context.applicationContext
    private val imageShareClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun share(title: String?, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            imageShareClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body
                val contentType = body.contentType()?.toString() ?: "image/jpeg"
                val bytes = body.byteStream().readAtMost(MAX_SHARED_IMAGE_BYTES) ?: return@withContext false
                val extension = contentType.imageExtension()
                val directory = File(appContext.cacheDir, "shared_images").apply { mkdirs() }
                pruneSharedImages(directory)
                val outputFile = File(directory, safeImageFileName(url, extension))
                FileOutputStream(outputFile).use { it.write(bytes) }

                val uri = FileProvider.getUriForFile(
                    appContext,
                    appContext.packageName + ".fileprovider",
                    outputFile
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = contentType
                        putExtra(Intent.EXTRA_SUBJECT, title ?: appContext.getString(R.string.article_image))
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    appContext.startActivity(
                        Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

private fun String.imageExtension(): String = when {
    contains("png") -> ".png"
    contains("webp") -> ".webp"
    contains("gif") -> ".gif"
    contains("svg") -> ".svg"
    contains("jpeg") || contains("jpg") -> ".jpg"
    else -> ".img"
}

private fun safeImageFileName(url: String, extension: String): String {
    val segment = runCatching { url.toUri().lastPathSegment }.getOrNull().orEmpty()
    val sanitized = segment
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
        .take(80)
    val base = sanitized.ifBlank { "image_${System.currentTimeMillis()}" }
    return if (base.endsWith(extension, ignoreCase = true)) base else base + extension
}

private fun InputStream.readAtMost(limit: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    use { input ->
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
    }
    return buffer.toByteArray()
}

private fun pruneSharedImages(directory: File) {
    val files = directory.listFiles()?.sortedByDescending { it.lastModified() } ?: return
    files.drop(MAX_SHARED_IMAGE_FILES - 1).forEach { it.delete() }
}
