package com.hiosdra.hreader.adapter.persistence.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `article_credibility_v16` (
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
                PRIMARY KEY(`entryId`, `modelId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_credibility_v16`(
                `entryId`, `score`, `confidence`, `summary`, `reasons`, `redFlags`, `factors`,
                `modelId`, `analyzedAt`, `contentTruncated`
            )
            SELECT `entryId`, `score`, `confidence`, `summary`, `reasons`, `redFlags`, `factors`,
                `modelId`, `analyzedAt`, `contentTruncated`
            FROM `article_credibility`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `article_credibility`")
        db.execSQL("ALTER TABLE `article_credibility_v16` RENAME TO `article_credibility`")

        db.execSQL(
            """
            CREATE TABLE `article_ai_overviews_v16` (
                `entryId` INTEGER NOT NULL,
                `overview` TEXT NOT NULL,
                `modelId` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`entryId`, `modelId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_ai_overviews_v16`(
                `entryId`, `overview`, `modelId`, `contentHash`, `generatedAt`
            )
            SELECT `entryId`, `overview`, `modelId`, `contentHash`, `generatedAt`
            FROM `article_ai_overviews`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `article_ai_overviews`")
        db.execSQL("ALTER TABLE `article_ai_overviews_v16` RENAME TO `article_ai_overviews`")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `leadImageUrl` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_feedId_publishedAt_id` " +
                "ON `articles` (`feedId`, `publishedAt`, `id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_starred_publishedAt_id` " +
                "ON `articles` (`starred`, `publishedAt`, `id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_status_publishedAt_id` " +
                "ON `articles` (`status`, `publishedAt`, `id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_article_images_entryId_originalUrl` " +
                "ON `article_images` (`entryId`, `originalUrl`)"
        )

        db.query("SELECT id, enclosures FROM articles").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val enclosuresIndex = cursor.getColumnIndexOrThrow("enclosures")
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val leadImageUrl = cursor.getString(enclosuresIndex)
                    .split(ENCLOSURE_RECORD_SEPARATOR)
                    .asSequence()
                    .map { record ->
                        record.substringBefore(ENCLOSURE_FIELD_SEPARATOR) to
                            record.substringAfter(ENCLOSURE_FIELD_SEPARATOR, "")
                    }
                    .firstOrNull { (url, mimeType) ->
                        url.isNotBlank() && mimeType.startsWith("image/", ignoreCase = true)
                    }
                    ?.first
                if (leadImageUrl != null) {
                    db.execSQL(
                        "UPDATE articles SET leadImageUrl = ? WHERE id = ?",
                        arrayOf(leadImageUrl, id)
                    )
                }
            }
        }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `article_contents` ADD COLUMN `allImagesPrepared` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE")
        db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT")

        db.execSQL(
            """
            CREATE TABLE `article_reading_positions_v19` (
                `articleId` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                PRIMARY KEY(`articleId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_reading_positions_v19`(`articleId`, `progress`)
            SELECT `articleId`, `progress` FROM `article_reading_positions`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `article_reading_positions`")

        db.execSQL(
            """
            CREATE TABLE `articles_v19` (
                `id` TEXT NOT NULL,
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
                `leadImageUrl` TEXT,
                `status` TEXT,
                `pendingSync` INTEGER NOT NULL,
                `readAt` INTEGER,
                `backlogFetchedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `articles_v19`(
                `rowid`, `id`, `title`, `author`, `url`, `publishedAt`, `content`, `fullContent`,
                `preview`, `feedId`, `readingTime`, `enclosures`, `leadImageUrl`, `status`,
                `pendingSync`, `readAt`, `backlogFetchedAt`
            )
            SELECT
                `rowid`, `id`, `title`, `author`, `url`, `publishedAt`, `content`, `fullContent`,
                `preview`, `feedId`, `readingTime`, `enclosures`, `leadImageUrl`, `status`,
                `pendingSync`, `readAt`, `backlogFetchedAt`
            FROM `articles`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `articles`")
        db.execSQL("ALTER TABLE `articles_v19` RENAME TO `articles`")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_status` ON `articles` (`status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_publishedAt` ON `articles` (`publishedAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_pendingSync` ON `articles` (`pendingSync`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_feedId_publishedAt_id` " +
                "ON `articles` (`feedId`, `publishedAt`, `id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_status_publishedAt_id` " +
                "ON `articles` (`status`, `publishedAt`, `id`)"
        )

        db.execSQL(
            """
            CREATE TABLE `article_reading_positions` (
                `articleId` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                PRIMARY KEY(`articleId`),
                FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `article_reading_positions`(`articleId`, `progress`)
            SELECT `articleId`, `progress` FROM `article_reading_positions_v19`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `article_reading_positions_v19`")

        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `articles` BEGIN DELETE FROM `articles_fts` " +
                "WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `articles` BEGIN DELETE FROM `articles_fts` " +
                "WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `articles` BEGIN INSERT INTO `articles_fts`" +
                "(`docid`, `title`, `author`, `content`, `fullContent`) VALUES " +
                "(NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`, NEW.`fullContent`); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_articles_fts_AFTER_INSERT " +
                "AFTER INSERT ON `articles` BEGIN INSERT INTO `articles_fts`" +
                "(`docid`, `title`, `author`, `content`, `fullContent`) VALUES " +
                "(NEW.`rowid`, NEW.`title`, NEW.`author`, NEW.`content`, NEW.`fullContent`); END"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `feeds` ADD COLUMN `preloadAiOverview` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val APP_MIGRATIONS = arrayOf(
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20
)

private const val ENCLOSURE_RECORD_SEPARATOR = "\u001e"
private const val ENCLOSURE_FIELD_SEPARATOR = "\u001f"
