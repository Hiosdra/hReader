package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "article_ai_overviews")
data class ArticleAiOverview(
    @PrimaryKey val entryId: Long,
    val overview: String,
    val modelId: String,
    val contentHash: String,
    val generatedAt: Instant
)
