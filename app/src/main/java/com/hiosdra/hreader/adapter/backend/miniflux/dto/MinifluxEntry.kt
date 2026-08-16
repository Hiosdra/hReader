package com.hiosdra.hreader.adapter.backend.miniflux.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MinifluxEntriesResponse(
    val total: Int = 0,
    val entries: List<MinifluxEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MinifluxEntry(
    val id: Long,
    val title: String? = null,
    val author: String? = null,
    val url: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    val content: String? = null,
    val feed: MinifluxFeed? = null,
    @Json(name = "reading_time") val readingTime: Int? = null,
    val enclosures: List<MinifluxEnclosure> = emptyList(),
    val status: String? = null,
    val starred: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MinifluxFeed(
    val id: Long,
    val title: String? = null,
    @Json(name = "site_url") val siteUrl: String? = null,
    @Json(name = "feed_url") val feedUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class MinifluxEnclosure(
    val url: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null
)
