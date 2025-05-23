package com.hiosdra.hreader.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OriginalContentResponse(
    val content: String
)
