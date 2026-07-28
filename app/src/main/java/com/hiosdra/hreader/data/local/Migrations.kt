package com.hiosdra.hreader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `article_credibility` (
                `entryId` INTEGER NOT NULL,
                `score` REAL NOT NULL,
                `confidence` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `reasons` TEXT NOT NULL,
                `redFlags` TEXT NOT NULL,
                `factors` TEXT NOT NULL,
                `modelId` TEXT NOT NULL,
                `analyzedAt` INTEGER NOT NULL,
                `contentTruncated` INTEGER NOT NULL,
                PRIMARY KEY(`entryId`)
            )
            """.trimIndent()
        )
    }
}
