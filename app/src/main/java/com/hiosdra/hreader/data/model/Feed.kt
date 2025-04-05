package com.hiosdra.hreader.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Feed(
    val id: Long,
    val title: String,
    @Json(name = "site_url") val siteUrl: String?,
    @Json(name = "feed_url") val feedUrl: String,
    val category: Category
)
