package com.hiosdra.hreader.core.application.port.out

interface ArticleAiSummaryStore {
    suspend fun get(entryId: Long, content: String, modelId: String): String?
    suspend fun save(entryId: Long, content: String, modelId: String, summary: String)
    suspend fun cleanupOrphaned(currentEntryIds: Set<Long>)
}
