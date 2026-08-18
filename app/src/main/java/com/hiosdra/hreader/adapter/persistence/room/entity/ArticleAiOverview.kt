package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "article_ai_overviews",
    primaryKeys = ["entryId", "modelId"]
)
data class ArticleAiOverview(
    val entryId: Long,
    val overview: String,
    val modelId: String,
    val contentHash: String,
    val generatedAt: Instant
)
