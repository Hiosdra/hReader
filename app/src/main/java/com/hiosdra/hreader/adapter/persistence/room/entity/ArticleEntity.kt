package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Enclosure
import java.time.Instant

@Entity(
    tableName = "articles",
    indices = [
        Index("feedId"),
        Index("status"),
        Index("publishedAt"),
        Index("pendingSync"),
        Index(value = ["feedId", "publishedAt", "id"]),
        Index(value = ["status", "publishedAt", "id"])
    ]
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val content: String?,
    val fullContent: String? = null,
    val preview: String? = null,
    val feedId: Long,
    val readingTime: Int?,
    val enclosures: List<Enclosure>,
    val leadImageUrl: String? = null,
    val status: ArticleStatus? = ArticleStatus.UNREAD,
    val pendingSync: Boolean = false,
    val readAt: Instant? = null,
    val backlogFetchedAt: Instant? = null
)
