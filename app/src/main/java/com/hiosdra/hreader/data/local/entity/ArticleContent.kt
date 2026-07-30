package com.hiosdra.hreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "article_contents")
data class ArticleContent(
    @PrimaryKey
    val entryId: Long,
    val content: String,
    val fetchedAt: Instant,
    val url: String,
    /**
     * Whether [content] is ready to render and [leadImageUrl] has been worked out. Rows written
     * before the text was prepared here are not, and are prepared and written back when read.
     */
    val isPrepared: Boolean = false,
    /** The picture to show above the article, null when the body already carries it. */
    val leadImageUrl: String? = null
)
