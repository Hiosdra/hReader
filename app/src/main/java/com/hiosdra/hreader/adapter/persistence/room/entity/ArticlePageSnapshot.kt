package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "article_page_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArticlePageSnapshot(
    @PrimaryKey val entryId: Long,
    val originalUrl: String,
    val finalUrl: String,
    val directoryPath: String,
    val fetchedAt: Instant,
    val byteSize: Long,
    val isComplete: Boolean
)
