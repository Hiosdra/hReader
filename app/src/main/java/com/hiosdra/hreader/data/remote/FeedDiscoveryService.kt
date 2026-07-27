package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.DiscoveredFeed
import okhttp3.OkHttpClient

private val FEED_MIME_TYPES = setOf(
    "application/rss+xml",
    "application/atom+xml",
    "application/feed+json",
    "application/json",
    "application/xml",
    "text/xml"
)

class FeedDiscoveryService(private val client: OkHttpClient) {
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> =
        client.fetchDocument(url)
            .select("link[rel~=(?i)alternate][href]")
            .filter { it.attr("type").lowercase() in FEED_MIME_TYPES }
            .map {
                DiscoveredFeed(
                    url = it.attr("abs:href"),
                    title = it.attr("title").takeIf { title -> title.isNotBlank() },
                    type = it.attr("type")
                )
            }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
}
