package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.image.isSafeRasterImage
import com.hiosdra.hreader.adapter.image.normalizedRasterImageContentType
import com.hiosdra.hreader.adapter.network.HttpStatusException
import com.hiosdra.hreader.adapter.network.NonRetryableNetworkException
import com.hiosdra.hreader.adapter.network.RETRY_AFTER_HEADER
import com.hiosdra.hreader.adapter.network.withNetworkRetries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

internal data class DownloadedArticleImage(
    val contentType: String,
    val fileSize: Long
)

internal class ArticleImageRemoteDownloader(
    httpClient: OkHttpClient,
    private val remoteResourcePolicy: RemoteResourcePolicyAdapter
) {
    private val safeHttpClient = httpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
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
                        val contentType = normalizedRasterImageContentType(body.contentType()?.toString())
                            ?: return@withNetworkRetries null
                        val declaredLength = body.contentLength()
                        if (declaredLength > MAX_IMAGE_BYTES) return@withNetworkRetries null
                        val bytes = body.byteStream().readAtMost(MAX_IMAGE_BYTES)
                            ?.takeIf { isSafeRasterImage(contentType, it) }
                            ?: return@withNetworkRetries null
                        staging.writeBytes(bytes)
                        val fileSize = bytes.size.toLong()
                        DownloadedArticleImage(contentType, fileSize)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun java.io.InputStream.readAtMost(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
    }
}
