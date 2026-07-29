package com.hiosdra.hreader.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Search index over the article bodies. External content: the rows live in [ArticleEntity] and
 * SQLite only keeps the inverted index, so nothing is stored twice. Matching used to happen in the
 * view model, which meant every cached article body was held in memory and re-scanned on each
 * keystroke.
 */
@Fts4(contentEntity = ArticleEntity::class)
@Entity(tableName = "articles_fts")
data class ArticleFts(
    val title: String,
    val author: String?,
    val content: String?
)
