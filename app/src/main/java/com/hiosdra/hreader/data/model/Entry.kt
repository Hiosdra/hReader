package com.hiosdra.hreader.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Entry(
    val id: Long,
    val title: String,
    val author: String?,
    val url: String,
    @Json(name = "published_at") val publishedAt: String,
    val content: String?,
    val feed: Feed,
    @Json(name = "reading_time") val readingTime: Int?,
    @Json(name = "enclosures")
    val enclosures: List<Enclosure>? = null,
    @Json(name = "status") val status: ArticleStatus = ArticleStatus.UNREAD,
)
