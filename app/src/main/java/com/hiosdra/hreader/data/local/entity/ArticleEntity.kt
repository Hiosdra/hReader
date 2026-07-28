package com.hiosdra.hreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Enclosure
import java.time.Instant

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val url: String,
    val publishedAt: Instant,
    val content: String?,
    val feedId: Long,
    val readingTime: Int?,
    val enclosures: List<Enclosure>,
    val status: ArticleStatus? = ArticleStatus.UNREAD,
    /**
     * A [status] change made locally that the backend has not accepted yet. It survives sync
     * reconciliation and is pushed again on the next run, so reading offline is not lost.
     */
    val pendingSync: Boolean = false,
    /**
     * When this article became [ArticleStatus.READ], and null while it is unread. Retention is
     * keyed on it rather than on [publishedAt] so catching up on an old article does not delete it
     * the moment it is read.
     */
    val readAt: Instant? = null
)
