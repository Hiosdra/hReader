package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleText

interface ArticleContentStore {
    suspend fun getArticleContent(entryId: Long, url: String, allowNetwork: Boolean = true): ArticleText
    suspend fun entriesMissingContent(entries: List<Pair<Long, String>>): List<Pair<Long, String>>
    suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int? = 50,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    )
    suspend fun downloadEnclosureImages(entries: List<Pair<Long, List<String>>>)
    suspend fun cleanupOrphanedContent()
}
