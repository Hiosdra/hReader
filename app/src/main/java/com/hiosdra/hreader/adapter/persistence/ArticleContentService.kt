package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleText
import kotlinx.coroutines.sync.Semaphore
import java.time.Instant

internal class ArticleContentService(
    private val reader: ArticleContentReader,
    private val preparation: ArticleContentPreparationService,
    private val articleContentDao: ArticleContentDao,
    private val articleRecordDao: ArticleRecordDao,
    private val articleImageStore: ArticleImageStore,
    private val credibilityStore: CredibilityStore,
    private val imageDownloads: ArticleImageDownloadCoordinator
) {
    suspend fun getArticleContent(
        entryId: Long,
        url: String,
        allowNetwork: Boolean,
        downloadAllImages: Boolean = true,
        imageLimiter: Semaphore? = null
    ): ArticleText = when (val selection = reader.read(entryId, url, allowNetwork)) {
        is ArticleContentSelection.Stored -> prepareStoredContent(
            entryId,
            selection.content,
            allowNetwork,
            downloadAllImages,
            imageLimiter
        )
        is ArticleContentSelection.Acquired -> storeContent(
            entryId,
            url,
            selection.content,
            selection.source,
            selection.delivery,
            allowNetwork,
            downloadAllImages,
            imageLimiter
        )
    }

    suspend fun markAllImagesPreparedIfComplete(entryId: Long) {
        val content = articleContentDao.getArticleContent(entryId) ?: return
        val expectedUrls = (
            ArticleContentImageManifest.decode(content.imageUrls) + listOfNotNull(content.leadImageUrl)
            ).distinct()
        val storedUrls = articleImageStore.getLocalImagePaths(entryId).keys
        if (expectedUrls.all { it in storedUrls }) {
            articleContentDao.markAllImagesPrepared(entryId)
        }
    }

    private suspend fun prepareStoredContent(
        entryId: Long,
        stored: ArticleContent,
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        imageLimiter: Semaphore?
    ): ArticleText {
        val prepared = preparation.prepareStored(entryId, stored)
        storeExpectedImagesAndScheduleDownloads(
            entryId,
            prepared,
            allowNetwork,
            downloadAllImages,
            imageLimiter
        )
        val updated = stored.copy(
            content = prepared.html,
            isPrepared = true,
            leadImageUrl = prepared.leadImageUrl,
            imageUrls = ArticleContentImageManifest.encode(prepared.imageUrls)
        )
        if (updated != stored) articleContentDao.insertArticleContent(updated)
        return ArticleText(
            html = prepared.html,
            leadImageUrl = prepared.leadImageUrl,
            source = stored.source,
            fetchedAt = stored.fetchedAt,
            sourceUrl = stored.url,
            delivery = ArticleContentDelivery.LOCAL_STORAGE
        )
    }

    private suspend fun storeContent(
        entryId: Long,
        url: String,
        sourceContent: String,
        source: ArticleContentSource,
        delivery: ArticleContentDelivery,
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        imageLimiter: Semaphore?
    ): ArticleText {
        credibilityStore.invalidateForEntries(listOf(entryId))
        val prepared = preparation.prepare(entryId, sourceContent, url)
        val fetchedAt = Instant.now()
        storeExpectedImagesAndScheduleDownloads(
            entryId,
            prepared,
            allowNetwork,
            downloadAllImages,
            imageLimiter
        )
        articleContentDao.insertArticleContent(
            ArticleContent(
                entryId = entryId,
                content = prepared.html,
                fetchedAt = fetchedAt,
                url = url,
                source = source,
                isPrepared = true,
                leadImageUrl = prepared.leadImageUrl,
                imageUrls = ArticleContentImageManifest.encode(prepared.imageUrls)
            )
        )
        if (source == ArticleContentSource.FULL) {
            articleRecordDao.setFullContent(entryId.toString(), sourceContent)
        }
        return ArticleText(
            html = prepared.html,
            leadImageUrl = prepared.leadImageUrl,
            source = source,
            fetchedAt = fetchedAt,
            sourceUrl = url,
            delivery = delivery
        )
    }

    private suspend fun storeExpectedImagesAndScheduleDownloads(
        entryId: Long,
        prepared: PreparedArticle,
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        imageLimiter: Semaphore?
    ) {
        val expectedImages = if (downloadAllImages) {
            (listOfNotNull(prepared.leadImageUrl) + prepared.imageUrls).distinct()
        } else {
            listOfNotNull(prepared.leadImageUrl)
        }
        articleImageStore.setExpectedImages(entryId, expectedImages)
        if (allowNetwork) {
            imageDownloads.schedule(
                entryId,
                prepared.imageUrls,
                prepared.leadImageUrl,
                downloadAllImages,
                imageLimiter
            )
        }
    }
}
