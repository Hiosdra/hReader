package com.hiosdra.hreader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

internal suspend fun OkHttpClient.fetchHtml(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", WEB_USER_AGENT)
        .build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Request to $url failed with HTTP ${response.code}")
        response.body.string()
    }
}
