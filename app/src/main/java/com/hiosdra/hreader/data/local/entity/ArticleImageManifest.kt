package com.hiosdra.hreader.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "article_image_manifest",
    primaryKeys = ["entryId", "originalUrl"]
)
data class ArticleImageManifest(
    val entryId: Long,
    val originalUrl: String
)
