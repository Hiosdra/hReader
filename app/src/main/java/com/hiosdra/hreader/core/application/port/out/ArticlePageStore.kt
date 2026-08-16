package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.OfflinePage

interface ArticlePageStore {
    suspend fun getOfflinePage(entryId: Long, originalUrl: String): OfflinePage?
    suspend fun entriesMissingPages(entries: List<Pair<Long, String>>): List<Pair<Long, String>>
    suspend fun prefetchPages(
        entries: List<Pair<Long, String>>,
        limit: Int? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    )
    suspend fun cleanupOrphanedPages()
    suspend fun clearAll()
}
