package com.hiosdra.hreader.adapter.backend.miniflux.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FeedCountersResponse(
    val reads: Map<String, Int>,
    val unreads: Map<String, Int>
)
