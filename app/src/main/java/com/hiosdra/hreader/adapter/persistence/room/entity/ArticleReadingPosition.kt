package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_reading_positions",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArticleReadingPosition(
    @PrimaryKey val articleId: String,
    val progress: Float
)
