package com.hiosdra.hreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hiosdra.hreader.data.model.Enclosure

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: String,
    val content: String?,
    val feedId: Long,
    val readingTime: Int?,
    val enclosures: List<Enclosure>?,
    val status: String?
)
