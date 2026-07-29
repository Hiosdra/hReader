package com.hiosdra.hreader.data.remote.miniflux.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateFeedRequest(
    val title: String
)
