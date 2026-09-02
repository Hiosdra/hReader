package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val siteUrl: String?,
    val feedUrl: String,
    val preloadAiOverview: Boolean = false
)
