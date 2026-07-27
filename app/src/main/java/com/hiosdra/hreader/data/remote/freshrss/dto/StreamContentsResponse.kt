package com.hiosdra.hreader.data.remote.freshrss.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StreamContentsResponse(
    val id: String? = null,
    val items: List<StreamItem> = emptyList(),
    val continuation: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamItem(
    val id: String,
    @Json(name = "frss:id") val numericId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val published: Long? = null,
    val canonical: List<StreamLink> = emptyList(),
    val alternate: List<StreamLink> = emptyList(),
    val summary: StreamContent? = null,
    val content: StreamContent? = null,
    val categories: List<String> = emptyList(),
    val origin: StreamOrigin? = null,
    val enclosure: List<StreamEnclosure> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StreamLink(
    val href: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamContent(
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamOrigin(
    val streamId: String? = null,
    val title: String? = null,
    val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamEnclosure(
    val href: String? = null,
    val type: String? = null
)
