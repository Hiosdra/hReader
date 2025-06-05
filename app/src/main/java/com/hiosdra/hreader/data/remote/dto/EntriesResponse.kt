package com.hiosdra.hreader.data.remote.dto

import com.hiosdra.hreader.data.model.Entry
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EntriesResponse(
    @Json(name = "total") val total: Int,
    @Json(name = "entries") val entries: List<Entry>
)
