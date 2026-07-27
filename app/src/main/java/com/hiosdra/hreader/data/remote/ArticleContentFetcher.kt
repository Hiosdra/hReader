package com.hiosdra.hreader.data.remote

import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient

class ArticleContentFetcher(private val client: OkHttpClient) {
    suspend fun fetchReadableContent(url: String): String {
        val html = client.fetchHtml(url)
        return Readability4J(url, html).parse().contentWithUtf8Encoding.orEmpty()
    }
}
