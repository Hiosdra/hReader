package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "article_ai_summaries",
    primaryKeys = ["entryId", "modelId"]
)
data class ArticleAiSummary(
    val entryId: Long,
    val summary: String,
    val modelId: String,
    val contentHash: String,
    val generatedAt: Instant
)
