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

private const val ENCLOSURE_RECORD_SEPARATOR = "\u001e"
private const val ENCLOSURE_FIELD_SEPARATOR = "\u001f"
