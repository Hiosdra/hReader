package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "article_image_manifest",
    primaryKeys = ["entryId", "originalUrl"],
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArticleImageManifest(
    val entryId: Long,
    val originalUrl: String
)
