package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import java.time.Instant

@Entity(tableName = "article_contents")
data class ArticleContent(
    @PrimaryKey
    val entryId: Long,
    val content: String,
    val fetchedAt: Instant,
    val url: String,
    val source: ArticleContentSource = ArticleContentSource.FULL,
    val isPrepared: Boolean = false,
    val leadImageUrl: String? = null,
    val imageUrls: String = "",
    val allImagesPrepared: Boolean = false
)
