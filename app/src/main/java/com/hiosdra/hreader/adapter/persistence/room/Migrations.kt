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
