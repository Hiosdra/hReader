package com.hiosdra.hreader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations rather than a destructive fallback: the cache holds articles, full content and
 * downloaded images that only exist offline, and dropping it on an upgrade would take unsynced
 * read states with it.
 */
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

/** Columns backing the read-state sync queue and retention. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
        // No DEFAULT clause: the entity declares none either, so this keeps a migrated table
        // identical to one created fresh. A nullable added column is NULL for existing rows anyway.
        db.execSQL("ALTER TABLE articles ADD COLUMN readAt INTEGER")
        // Already-read articles have no recorded read time. Backfilling with the upgrade time
        // starts their retention window now instead of leaving them un-prunable forever.
        db.execSQL(
            "UPDATE articles SET readAt = ? WHERE status = 'READ'",
            arrayOf<Any>(System.currentTimeMillis())
        )
    }
}

/** Marks articles downloaded as offline backlog rather than because they were unread. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN backlogFetchedAt INTEGER")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
