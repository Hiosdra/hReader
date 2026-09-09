package com.hiosdra.hreader.adapter.persistence.room.entity

import androidx.room.Entity

@Entity(
    tableName = "full_sync_seen",
    primaryKeys = ["runId", "articleId"]
)
data class FullSyncSeenEntity(
    val runId: String,
    val articleId: String
)
