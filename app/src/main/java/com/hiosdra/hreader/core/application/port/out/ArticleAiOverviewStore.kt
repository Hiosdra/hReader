package com.hiosdra.hreader.core.application.port.out

interface ArticleAiOverviewStore {
    suspend fun get(entryId: Long, content: String, modelId: String): String?
    suspend fun save(entryId: Long, content: String, modelId: String, overview: String)
    suspend fun cleanupOrphaned(currentEntryIds: Set<Long>)
}
