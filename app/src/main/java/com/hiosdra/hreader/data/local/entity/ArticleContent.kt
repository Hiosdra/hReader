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
    val url: String
)
