package com.hiosdra.hreader.data.remote.freshrss.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnreadCountResponse(
    val max: Int = 0,
    @Json(name = "unreadcounts") val unreadCounts: List<UnreadCount> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UnreadCount(
    val id: String,
    val count: Int = 0
)
