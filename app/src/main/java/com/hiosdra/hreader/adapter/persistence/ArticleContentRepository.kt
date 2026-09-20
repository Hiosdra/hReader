package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleText

internal class ArticleContentRepository(
    private val contentService: ArticleContentService,
    private val prefetchService: ArticleContentPrefetchService,
    private val articleContentDao: ArticleContentDao
) : ArticleContentStore {
    override suspend fun getArticleContent(
        entryId: Long,
        url: String,
        allowNetwork: Boolean
    ): ArticleText = contentService.getArticleContent(
        entryId = entryId,
        url = url,
        allowNetwork = allowNetwork
    )

    suspend fun getArticleContent(entryId: Long, url: String): ArticleText =
        getArticleContent(entryId, url, allowNetwork = true)

    override suspend fun entriesMissingContent(entries: List<Pair<Long, String>>): List<Pair<Long, String>> {
        val full = articleContentDao.getContentEntryIds(ArticleContentSource.FULL).toHashSet()
        return entries.filterNot { (entryId, _) -> entryId in full }
    }

    override suspend fun entriesMissingFullOfflinePreparation(
        entries: List<Pair<Long, String>>
    ): List<Pair<Long, String>> {
        val prepared = entries
            .chunked(DELETE_CHUNK)
            .flatMap { chunk ->
                articleContentDao.getFullyImagePreparedEntryIds(chunk.map { it.first })
            }
            .toHashSet()
        return entries.filterNot { (entryId, _) -> entryId in prepared }
    }

    override suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        downloadAllImages: Boolean,
        syncMode: SyncMode,
        onProgress: (done: Int, total: Int) -> Unit
    ) = prefetchService.prefetchArticleContent(
        entries = entries,
        limit = limit,
        downloadAllImages = downloadAllImages,
        syncMode = syncMode,
        onProgress = onProgress
    )

    override suspend fun downloadEnclosureImages(
        entries: List<Pair<Long, List<String>>>,
        syncMode: SyncMode
    ) = prefetchService.downloadEnclosureImages(entries, syncMode)

    private companion object {
        const val DELETE_CHUNK = 500
    }
}
