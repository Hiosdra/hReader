package com.hiosdra.hreader.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateFeedResponse(
    val feed_id: Long
)
