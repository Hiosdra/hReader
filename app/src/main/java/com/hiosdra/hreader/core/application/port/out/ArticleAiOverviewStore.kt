package com.hiosdra.hreader.core.application.port.out

interface ArticleAiOverviewStore {
    suspend fun get(
        entryId: Long,
        content: String,
        modelId: String,
        session: SyncSession? = null
    ): String?

    suspend fun save(
        entryId: Long,
        content: String,
        modelId: String,
        overview: String,
        session: SyncSession? = null
    )

    suspend fun cleanupOrphaned(session: SyncSession? = null)
}
