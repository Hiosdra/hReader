package com.hiosdra.hreader.core.domain.model

data class Feed(
    val id: Long,
    val title: String,
    val siteUrl: String?,
    val feedUrl: String,
    val preloadAiOverview: Boolean = false
)
