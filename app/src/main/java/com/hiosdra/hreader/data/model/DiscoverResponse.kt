package com.hiosdra.hreader.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiscoverResponse(
    val url: String,
    val title: String?,
    val type: String?
)
