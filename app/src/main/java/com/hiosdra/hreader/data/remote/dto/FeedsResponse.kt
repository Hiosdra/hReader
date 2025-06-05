package com.hiosdra.hreader.data.remote.dto

import com.hiosdra.hreader.data.model.Feed
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FeedsResponse(
    @Json(name = "feeds") val feeds: List<Feed>
)
