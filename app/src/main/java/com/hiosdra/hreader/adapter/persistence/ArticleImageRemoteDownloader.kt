package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.network.HttpStatusException
import com.hiosdra.hreader.adapter.network.NonRetryableNetworkException
import com.hiosdra.hreader.adapter.network.RETRY_AFTER_HEADER
import com.hiosdra.hreader.adapter.network.withNetworkRetries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal data class DownloadedArticleImage(
    val contentType: String,
    val fileSize: Long
)

internal class ArticleImageRemoteDownloader(
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

    suspend fun download(imageUrl: String, staging: File): DownloadedArticleImage? =
        withContext(Dispatchers.IO) {
            if (!remoteResourcePolicy.allows(imageUrl)) return@withContext null
            try {
                withNetworkRetries {
                    val request = Request.Builder().url(imageUrl).build()
                    safeHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw HttpStatusException(response.code, response.header(RETRY_AFTER_HEADER))
                        }
                        if (!remoteResourcePolicy.allows(response.request.url.toString())) {
                            return@withNetworkRetries null
                        }
                        val body = response.body
                        val contentType = body.contentType()?.toString()
                            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                            ?: return@withNetworkRetries null
                        val declaredLength = body.contentLength()
                        if (declaredLength > MAX_IMAGE_BYTES) return@withNetworkRetries null
                        val fileSize = copyAtMost(body.byteStream(), staging, MAX_IMAGE_BYTES)
                            ?: return@withNetworkRetries null
                        DownloadedArticleImage(contentType, fileSize)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun copyAtMost(input: InputStream, target: File, limit: Long): Long? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        FileOutputStream(target).use { output ->
            input.use { source ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > limit) return null
                    output.write(buffer, 0, read)
                }
            }
        }
        return written
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
    }
}
