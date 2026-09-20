package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageFile
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageManifest
import kotlinx.coroutines.flow.Flow

internal class ArticleImageIndex(
    private val dao: ArticleImageDao
) {
    suspend fun getImageForArticleByUrl(entryId: Long, imageUrl: String): ArticleImage? =
        dao.getImageForArticleByUrl(entryId, imageUrl)

    suspend fun getImagesForArticle(entryId: Long): List<ArticleImage> =
        dao.getImagesForArticle(entryId)

    suspend fun getImagesForArticles(entryIds: List<Long>): List<ArticleImage> =
        dao.getImagesForArticles(entryIds)

    fun observeImagesForArticle(entryId: Long): Flow<List<ArticleImage>> =
        dao.observeImagesForArticle(entryId)

    suspend fun insert(image: ArticleImage) {
        dao.insertArticleImage(image)
    }

    suspend fun delete(image: ArticleImage) {
        dao.deleteArticleImage(image)
    }

    suspend fun deleteByIds(ids: List<String>) {
        dao.deleteByIds(ids)
    }

    suspend fun setExpectedImages(entryId: Long, imageUrls: List<String>) {
        dao.deleteExpectedImagesForArticle(entryId)
        val expected = imageUrls.distinct().map { url -> ArticleImageManifest(entryId, url) }
        if (expected.isNotEmpty()) dao.insertExpectedImages(expected)
    }

    suspend fun deleteImagesForArticles(entryIds: List<Long>) {
        dao.deleteImagesForArticles(entryIds)
    }

    suspend fun deleteExpectedImagesForArticles(entryIds: List<Long>) {
        dao.deleteExpectedImagesForArticles(entryIds)
    }

    suspend fun clearAll() {
        dao.clearAll()
        dao.clearExpectedImages()
    }

    suspend fun getOrphanedImageEntryIds(limit: Int): List<Long> =
        dao.getOrphanedImageEntryIds(limit)

    suspend fun getOrphanedExpectedEntryIds(limit: Int): List<Long> =
        dao.getOrphanedExpectedEntryIds(limit)

    suspend fun getImagePathsForArticles(entryIds: List<Long>): List<String> =
        dao.getImagePathsForArticles(entryIds)

    suspend fun getImageFilesAfterId(afterId: String, limit: Int): List<ArticleImageFile> =
        dao.getImageFilesAfterId(afterId, limit)

    suspend fun getTotalImageBytes(): Long = dao.getTotalImageBytes()

    suspend fun getImagesOldestFirst(limit: Int): List<ArticleImage> =
        dao.getImagesOldestFirst(limit)
}
