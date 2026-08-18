package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "article_credibility",
    primaryKeys = ["entryId", "modelId"]
)
data class ArticleCredibility(
    val entryId: Long,
    val score: Float,
    val confidence: String,
    val summary: String,
    val reasons: String,
    val redFlags: String,
    val factors: String,
    val modelId: String,
    val analyzedAt: Instant,
    val contentTruncated: Boolean
)
