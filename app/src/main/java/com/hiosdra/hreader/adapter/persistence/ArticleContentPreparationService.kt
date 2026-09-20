package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.core.application.content.ArticleHtmlTransformer
import com.hiosdra.hreader.core.application.content.leadImageUrl
import com.hiosdra.hreader.core.application.content.prepareArticleImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PreparedArticle(
    val html: String,
    val imageUrls: List<String>,
    val leadImageUrl: String?
)

internal object ArticleContentImageManifest {
    private const val IMAGE_URL_SEPARATOR = "\u001e"
    private const val EMPTY_IMAGE_MANIFEST = "\u0000"

    fun decode(manifest: String): List<String> = manifest
        .takeUnless { it == EMPTY_IMAGE_MANIFEST }
        ?.split(IMAGE_URL_SEPARATOR)
        ?.filter { it.isNotBlank() }
        .orEmpty()

    fun encode(imageUrls: List<String>): String = imageUrls
        .joinToString(IMAGE_URL_SEPARATOR)
        .ifEmpty { EMPTY_IMAGE_MANIFEST }
}

internal class ArticleContentPreparationService(
    private val embeddedMediaLabel: () -> String,
    private val articleRecordDao: ArticleRecordDao
) {
    suspend fun prepare(entryId: Long, content: String, baseUrl: String): PreparedArticle {
        val article = articleRecordDao.getArticlesImmediate(listOf(entryId.toString())).firstOrNull()
        return withContext(Dispatchers.Default) {
            val images = prepareArticleImages(
                content,
                baseUrl,
                embeddedMediaLabel(),
                article?.title.orEmpty()
            )
            PreparedArticle(
                html = images.html,
                imageUrls = images.imageUrls,
                leadImageUrl = leadImageUrl(
                    enclosureUrl = article?.enclosures?.firstOrNull { it.isImage }?.url,
                    feedContent = article?.content,
                    bodyImageUrls = images.imageUrls,
                    baseUri = baseUrl
                )
            )
        }
    }

    suspend fun prepareStored(entryId: Long, stored: ArticleContent): PreparedArticle {
        val storedImageUrls = ArticleContentImageManifest.decode(stored.imageUrls)
        if (!stored.isPrepared || stored.imageUrls.isEmpty()) {
            return prepare(entryId, stored.content, stored.url)
        }

        val articleTitle = articleRecordDao.getArticlesImmediate(listOf(entryId.toString()))
            .firstOrNull()
            ?.title
            .orEmpty()
        val normalizedContent = withContext(Dispatchers.Default) {
            ArticleHtmlTransformer.transform(
                html = stored.content,
                baseUrl = stored.url,
                articleTitle = articleTitle,
                embeddedMediaLabel = embeddedMediaLabel()
            )
        }
        return PreparedArticle(normalizedContent, storedImageUrls, stored.leadImageUrl)
    }
}
