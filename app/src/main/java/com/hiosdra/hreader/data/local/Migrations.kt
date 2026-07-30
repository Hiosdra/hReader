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

/**
 * Stars, the stored article preview and the full-text index.
 *
 * [preview] is left null for articles already in the cache: it is derived from the body by an HTML
 * parser, which SQL cannot run. `ArticleContentSyncWorker` fills the gap in the background, and a
 * row without one simply shows no preview until it does.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN preview TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN starredPendingSync INTEGER NOT NULL DEFAULT 0")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_status` ON `articles` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_publishedAt` ON `articles` (`publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_pendingSync` ON `articles` (`pendingSync`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_starredPendingSync` " +
                "ON `articles` (`starredPendingSync`)"
        )

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `author` TEXT, `content` TEXT, content=`articles`)"
        )
        FTS_CONTENT_SYNC_TRIGGERS.forEach(db::execSQL)
        // External-content FTS keeps only the index, so it starts empty and has to be told to read
        // the rows that were already there.
        db.execSQL("INSERT INTO `articles_fts`(`articles_fts`) VALUES('rebuild')")
    }
}

/**
 * What Room emits for an `@Fts4(contentEntity = …)` table: the index has no rows of its own, so
 * every write to `articles` has to be mirrored into it.
 */
private val FTS_CONTENT_SYNC_TRIGGERS = listOf(
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE " +
        "BEFORE UPDATE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE " +
        "BEFORE DELETE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE " +
        "AFTER UPDATE ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `title`, `author`, " +
        "`content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`); END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT " +
        "AFTER INSERT ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `title`, `author`, " +
        "`content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`); END"
)

/**
 * Rebuilds the search index. Articles used to be stored with an insert-or-replace, which gave a
 * re-synced article a new rowid without removing the index entry filed under the old one, so every
 * cache carries orphaned entries proportional to how long it has been syncing. The writes upsert
 * now; this clears out what the old ones left behind.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `articles_fts`(`articles_fts`) VALUES('rebuild')")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
