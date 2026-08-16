package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "article_credibility",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArticleCredibility(
    @PrimaryKey
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
