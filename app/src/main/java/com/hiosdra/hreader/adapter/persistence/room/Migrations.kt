package com.hiosdra.hreader.adapter.persistence.room

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

/**
 * Article text is now prepared for rendering once, where it is stored, rather than on every
 * reading. What is already cached stays: it is marked unprepared and upgraded the next time it is
 * opened, so nobody's offline backlog has to be downloaded again.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `article_contents` ADD COLUMN `isPrepared` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `article_contents` ADD COLUMN `leadImageUrl` TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `fullContent` TEXT")
        db.execSQL(
            "ALTER TABLE `article_contents` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'FEED_FALLBACK'"
        )
        db.execSQL("ALTER TABLE `article_contents` ADD COLUMN `imageUrls` TEXT NOT NULL DEFAULT ''")

        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT")
        db.execSQL("DROP TABLE IF EXISTS `articles_fts`")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `author` TEXT, `content` TEXT, `fullContent` TEXT, " +
                "content=`articles`)"
        )
        FTS_CONTENT_SYNC_TRIGGERS_WITH_FULL_CONTENT.forEach(db::execSQL)
        db.execSQL("INSERT INTO `articles_fts`(`articles_fts`) VALUES('rebuild')")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `article_ai_overviews` (" +
                "`entryId` INTEGER NOT NULL, `overview` TEXT NOT NULL, `modelId` TEXT NOT NULL, " +
                "`contentHash` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entryId`))"
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `article_image_manifest` (" +
                "`entryId` INTEGER NOT NULL, `originalUrl` TEXT NOT NULL, " +
                "PRIMARY KEY(`entryId`, `originalUrl`))"
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `article_page_snapshots` (" +
                "`entryId` INTEGER NOT NULL, `originalUrl` TEXT NOT NULL, " +
                "`finalUrl` TEXT NOT NULL, `directoryPath` TEXT NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, `byteSize` INTEGER NOT NULL, " +
                "`isComplete` INTEGER NOT NULL, PRIMARY KEY(`entryId`))"
        )
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `article_reading_positions` (" +
                "`articleId` TEXT NOT NULL, `progress` REAL NOT NULL, " +
                "PRIMARY KEY(`articleId`), " +
                "FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT")
        db.execSQL("DROP TABLE IF EXISTS `articles_fts`")

        ARTICLE_INDEXES.forEach { index ->
            db.execSQL("DROP INDEX IF EXISTS `$index`")
        }

        ARTICLE_CHILD_TABLES.forEach { table ->
            db.execSQL("ALTER TABLE `$table` RENAME TO `${table}_old`")
        }
        db.execSQL("ALTER TABLE `articles` RENAME TO `articles_old`")

        db.execSQL(
            """
            CREATE TABLE `articles` (
                `id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `url` TEXT NOT NULL,
                `publishedAt` INTEGER NOT NULL,
                `content` TEXT,
                `fullContent` TEXT,
                `preview` TEXT,
                `feedId` INTEGER NOT NULL,
                `readingTime` INTEGER,
                `enclosures` TEXT NOT NULL,
                `status` TEXT,
                `starred` INTEGER NOT NULL,
                `starredPendingSync` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL,
                `readAt` INTEGER,
                `backlogFetchedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_articles_feedId` ON `articles` (`feedId`)")
        db.execSQL("CREATE INDEX `index_articles_status` ON `articles` (`status`)")
        db.execSQL("CREATE INDEX `index_articles_publishedAt` ON `articles` (`publishedAt`)")
        db.execSQL("CREATE INDEX `index_articles_pendingSync` ON `articles` (`pendingSync`)")
        db.execSQL("CREATE INDEX `index_articles_starredPendingSync` ON `articles` (`starredPendingSync`)")

        db.execSQL(
            "CREATE TEMP TABLE `article_id_migration` (" +
                "`oldId` TEXT NOT NULL PRIMARY KEY, `id` INTEGER NOT NULL UNIQUE)"
        )
        db.execSQL(
            "INSERT INTO `article_id_migration`(`oldId`, `id`) " +
                "SELECT `id`, CAST(`id` AS INTEGER) FROM `articles_old` " +
                "WHERE `id` != '' AND `id` = CAST(CAST(`id` AS INTEGER) AS TEXT)"
        )
        db.execSQL(
            "INSERT INTO `articles`(" +
                "`id`, `title`, `author`, `url`, `publishedAt`, `content`, `fullContent`, " +
                "`preview`, `feedId`, `readingTime`, `enclosures`, `status`, `starred`, " +
                "`starredPendingSync`, `pendingSync`, `readAt`, `backlogFetchedAt`) " +
                "SELECT m.`id`, a.`title`, a.`author`, a.`url`, a.`publishedAt`, a.`content`, " +
                "a.`fullContent`, a.`preview`, a.`feedId`, a.`readingTime`, a.`enclosures`, " +
                "a.`status`, a.`starred`, a.`starredPendingSync`, a.`pendingSync`, a.`readAt`, " +
                "a.`backlogFetchedAt` FROM `articles_old` a " +
                "INNER JOIN `article_id_migration` m ON m.`oldId` = a.`id`"
        )

        db.execSQL(
            """
            CREATE TABLE `article_contents` (
                `entryId` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                `url` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `isPrepared` INTEGER NOT NULL,
                `leadImageUrl` TEXT,
                `imageUrls` TEXT NOT NULL,
                PRIMARY KEY(`entryId`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_contents`
            SELECT c.* FROM `article_contents_old` c
            INNER JOIN `articles` a ON a.`id` = c.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_images` (
                `id` TEXT NOT NULL,
                `entryId` INTEGER NOT NULL,
                `originalUrl` TEXT NOT NULL,
                `localFilePath` TEXT NOT NULL,
                `mimeType` TEXT,
                `downloadedAt` INTEGER NOT NULL,
                `fileSize` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_article_images_entryId` ON `article_images` (`entryId`)")
        db.execSQL(
            """
            INSERT INTO `article_images`
            SELECT i.* FROM `article_images_old` i
            INNER JOIN `articles` a ON a.`id` = i.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_image_manifest` (
                `entryId` INTEGER NOT NULL,
                `originalUrl` TEXT NOT NULL,
                PRIMARY KEY(`entryId`, `originalUrl`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_image_manifest`
            SELECT m.* FROM `article_image_manifest_old` m
            INNER JOIN `articles` a ON a.`id` = m.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_credibility` (
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
                PRIMARY KEY(`entryId`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_credibility`
            SELECT c.* FROM `article_credibility_old` c
            INNER JOIN `articles` a ON a.`id` = c.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_ai_overviews` (
                `entryId` INTEGER NOT NULL,
                `overview` TEXT NOT NULL,
                `modelId` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`entryId`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_ai_overviews`
            SELECT o.* FROM `article_ai_overviews_old` o
            INNER JOIN `articles` a ON a.`id` = o.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_page_snapshots` (
                `entryId` INTEGER NOT NULL,
                `originalUrl` TEXT NOT NULL,
                `finalUrl` TEXT NOT NULL,
                `directoryPath` TEXT NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                `byteSize` INTEGER NOT NULL,
                `isComplete` INTEGER NOT NULL,
                PRIMARY KEY(`entryId`),
                FOREIGN KEY(`entryId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_page_snapshots`
            SELECT p.* FROM `article_page_snapshots_old` p
            INNER JOIN `articles` a ON a.`id` = p.`entryId`
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE `article_reading_positions` (
                `articleId` INTEGER NOT NULL,
                `progress` REAL NOT NULL,
                PRIMARY KEY(`articleId`),
                FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_reading_positions`(`articleId`, `progress`)
            SELECT m.`id`, p.`progress` FROM `article_reading_positions_old` p
            INNER JOIN `article_id_migration` m ON m.`oldId` = p.`articleId`
            INNER JOIN `articles` a ON a.`id` = m.`id`
            """.trimIndent()
        )

        ARTICLE_CHILD_TABLES.forEach { table ->
            db.execSQL("DROP TABLE `${table}_old`")
        }
        db.execSQL("DROP TABLE `articles_old`")
        db.execSQL("DROP TABLE `article_id_migration`")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `author` TEXT, `content` TEXT, `fullContent` TEXT, " +
                "content=`articles`)"
        )
        FTS_CONTENT_SYNC_TRIGGERS_WITH_FULL_CONTENT.forEach(db::execSQL)
        db.execSQL("INSERT INTO `articles_fts`(`articles_fts`) VALUES('rebuild')")
    }
}

private val ARTICLE_CHILD_TABLES = listOf(
    "article_contents",
    "article_images",
    "article_image_manifest",
    "article_credibility",
    "article_ai_overviews",
    "article_page_snapshots",
    "article_reading_positions"
)

private val ARTICLE_INDEXES = listOf(
    "index_articles_feedId",
    "index_articles_status",
    "index_articles_publishedAt",
    "index_articles_pendingSync",
    "index_articles_starredPendingSync"
)

private val FTS_CONTENT_SYNC_TRIGGERS_WITH_FULL_CONTENT = listOf(
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE " +
        "BEFORE UPDATE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE " +
        "BEFORE DELETE ON `articles` BEGIN DELETE FROM `articles_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE " +
        "AFTER UPDATE ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `title`, `author`, " +
        "`content`, `fullContent`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`, " +
        "NEW.`fullContent`); END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT " +
        "AFTER INSERT ON `articles` BEGIN INSERT INTO `articles_fts`(`docid`, `title`, `author`, " +
        "`content`, `fullContent`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`, " +
        "NEW.`fullContent`); END"
)

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16
)
