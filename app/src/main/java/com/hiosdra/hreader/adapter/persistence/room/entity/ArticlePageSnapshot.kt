package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "article_page_snapshots")
data class ArticlePageSnapshot(
    @PrimaryKey val entryId: Long,
    val originalUrl: String,
    val finalUrl: String,
    val directoryPath: String,
    val fetchedAt: Instant,
    val byteSize: Long,
    val isComplete: Boolean
)
