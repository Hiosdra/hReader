package com.hiosdra.hreader.core.domain.model

import java.time.Instant

data class OfflinePage(
    val entryId: Long,
    val originalUrl: String,
    val baseUrl: String,
    val html: String,
    val resourceDirectory: String,
    val isComplete: Boolean,
    val finalUrl: String = originalUrl,
    val fetchedAt: Instant? = null
)
