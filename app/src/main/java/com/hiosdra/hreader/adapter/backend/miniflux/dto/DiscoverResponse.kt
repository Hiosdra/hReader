package com.hiosdra.hreader.adapter.backend.miniflux.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiscoverResponse(
    val url: String,
    val title: String?,
    val type: String?
)
