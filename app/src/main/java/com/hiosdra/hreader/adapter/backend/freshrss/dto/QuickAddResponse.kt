package com.hiosdra.hreader.adapter.backend.freshrss.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuickAddResponse(
    val numResults: Int = 0,
    val streamId: String? = null,
    val error: String? = null
)
