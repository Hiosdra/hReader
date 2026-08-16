package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "article_images",
    indices = [Index("entryId")],
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArticleImage(
    @PrimaryKey val id: String, // Generated from entryId + originalUrl hash
    val entryId: Long,
    val originalUrl: String,
    val localFilePath: String,
    val mimeType: String?,
    val downloadedAt: Instant,
    val fileSize: Long?
)
