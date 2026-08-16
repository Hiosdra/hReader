package com.hiosdra.hreader.adapter.backend.miniflux.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateFeedRequest(
    val title: String
)
