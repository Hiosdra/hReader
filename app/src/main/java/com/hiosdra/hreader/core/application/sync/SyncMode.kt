package com.hiosdra.hreader.core.application.sync

enum class SyncMode(
    val maxConcurrentArticleContent: Int,
    val maxConcurrentArticleImages: Int,
    val maxConcurrentFullPages: Int,
    val maxConcurrentPageResources: Int
) {
    SAFE(
        maxConcurrentArticleContent = 8,
        maxConcurrentArticleImages = 8,
        maxConcurrentFullPages = 4,
        maxConcurrentPageResources = 1
    ),
    FAST(
        maxConcurrentArticleContent = 100,
        maxConcurrentArticleImages = 25,
        maxConcurrentFullPages = 25,
        maxConcurrentPageResources = 4
    );

    val maxConcurrentNetworkRequests: Int
        get() = maxOf(
            maxConcurrentArticleContent,
            maxConcurrentArticleImages,
            maxConcurrentFullPages * maxConcurrentPageResources
        )

    companion object {
        fun fromName(name: String?): SyncMode = entries.find { it.name == name } ?: SAFE
    }
}
