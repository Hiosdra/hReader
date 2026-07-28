package com.hiosdra.hreader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations rather than a destructive fallback: the cache holds articles, full content and
 * downloaded images that only exist offline, and dropping it on an upgrade would take unsynced
 * read states with it.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
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

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6)
