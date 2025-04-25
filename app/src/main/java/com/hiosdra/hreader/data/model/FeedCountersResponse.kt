package com.hiosdra.hreader.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FeedCountersResponse(
    val reads: Map<String, Int>,
    val unreads: Map<String, Int>
)
