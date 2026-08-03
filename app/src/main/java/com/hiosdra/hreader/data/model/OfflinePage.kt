package com.hiosdra.hreader.data.model

data class OfflinePage(
    val entryId: Long,
    val originalUrl: String,
    val baseUrl: String,
    val html: String,
    val resourceDirectory: String,
    val isComplete: Boolean
)
