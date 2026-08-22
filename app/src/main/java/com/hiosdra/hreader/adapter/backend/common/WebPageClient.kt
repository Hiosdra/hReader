package com.hiosdra.hreader.adapter.backend.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.text.Charsets.UTF_8

internal const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

internal const val MAX_REMOTE_BODY_BYTES = 100L * 1024L * 1024L

internal suspend fun OkHttpClient.fetchHtml(
    url: String,
    maxBytes: Long = MAX_REMOTE_BODY_BYTES
): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", WEB_USER_AGENT)
        .build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Web request failed with HTTP " + response.code)
        val body = response.body
        readBoundedBody(body, maxBytes)
    }
}

internal fun readBoundedBody(body: ResponseBody, maxBytes: Long): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    if (body.contentLength() > maxBytes) {
        throw IOException("Remote response exceeds the ${maxBytes}-byte limit")
    }
    val source = body.source()
    val buffer = Buffer()
    var totalBytes = 0L
    while (totalBytes <= maxBytes) {
        val bytesToRead = (maxBytes - totalBytes + 1).coerceAtMost(8_192L)
        val read = source.read(buffer, bytesToRead)
        if (read == -1L) break
        totalBytes += read
    }
    if (totalBytes > maxBytes) {
        throw IOException("Remote response exceeds the ${maxBytes}-byte limit")
    }
    return buffer.readByteArray().toString(UTF_8)
}
