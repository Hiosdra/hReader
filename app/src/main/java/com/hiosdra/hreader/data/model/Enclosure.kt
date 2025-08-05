package com.hiosdra.hreader.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Enclosure(
    val id: Long,
    @Json(name = "user_id") val userId: Long,
    @Json(name = "entry_id") val entryId: Long,
    val url: String,
    @Json(name = "mime_type") val mimeType: String?,
    val size: Long?,
    @Json(name = "media_progression") val mediaProgression: Int?
)
