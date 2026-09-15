package com.hiosdra.hreader.adapter.persistence.room

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = MigrationsTestApplication::class, sdk = [35])
class MigrationsTest {
    private val schemaArchive = createSchemaArchive()
    private val schemaContext = SchemaContext(RuntimeEnvironment.getApplication(), schemaArchive)

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        TestInstrumentation(schemaContext),
        AppDatabase::class.java
    )

    @After
    fun tearDown() {
        schemaArchive.delete()
    }

    @Test
    fun migrationsFormAContinuousChainToTheCurrentSchema() {
        assertEquals(listOf(15, 16, 17, 18, 19, 20, 21, 22), APP_MIGRATIONS.map { it.startVersion })
        assertEquals(listOf(16, 17, 18, 19, 20, 21, 22, 23), APP_MIGRATIONS.map { it.endVersion })
        APP_MIGRATIONS.asList().zipWithNext().forEach { (current, next) ->
            assertEquals(current.endVersion, next.startVersion)
        }
    }

    @Test
    fun everySupportedSchemaVersionMigratesToTheCurrentSchema() {
        (15..22).forEach { startVersion ->
            val databaseName = "migration-$startVersion-${System.nanoTime()}"
            migrationHelper.createDatabase(databaseName, startVersion).also { database ->
                seedDatabase(database, startVersion)
                database.close()
            }

            migrationHelper.runMigrationsAndValidate(databaseName, 23, true, *APP_MIGRATIONS).also {
                assertMigratedData(it, startVersion)
                it.close()
            }
        }
    }

    private fun seedDatabase(database: SupportSQLiteDatabase, version: Int) {
        database.insert(
            "feeds",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("id", 7L)
                put("title", "Legacy feed")
                put("siteUrl", "https://example.com")
                put("feedUrl", "https://example.com/feed.xml")
                if (version >= 20) put("preloadAiOverview", 1)
            }
        )
        database.insert(
            "articles",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("id", "42")
                put("title", "Legacy title")
                put("author", "Legacy author")
                put("url", "https://example.com/article")
                put("publishedAt", 1_700_000_000_000L)
                put("content", "Legacy body")
                put("fullContent", "Legacy full body")
                put("preview", "Legacy preview")
                put("feedId", 7L)
                put("readingTime", 4)
                put("enclosures", "https://example.com/image.jpg\u001fimage/jpeg")
                if (version >= 17) put("leadImageUrl", "https://example.com/image.jpg")
                put("status", "UNREAD")
                if (version < 19) {
                    put("starred", 1)
                    put("starredPendingSync", 1)
                }
                put("pendingSync", 1)
                put("readAt", 1_700_000_001_000L)
                putNull("backlogFetchedAt")
            }
        )
        database.insert(
            "article_contents",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("entryId", 42L)
                put("content", "Legacy stored content")
                put("fetchedAt", 1_700_000_002_000L)
                put("url", "https://example.com/article")
                put("source", "FULL")
                put("isPrepared", 1)
                put("leadImageUrl", "https://example.com/image.jpg")
                put("imageUrls", "https://example.com/image.jpg")
                if (version >= 18) put("allImagesPrepared", 1)
            }
        )
        database.insert(
            "article_images",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("id", "image-42")
                put("entryId", 42L)
                put("originalUrl", "https://example.com/image.jpg")
                put("localFilePath", "/data/user/0/com.hiosdra.hreader/files/image.jpg")
                put("mimeType", "image/jpeg")
                put("downloadedAt", 1_700_000_003_000L)
                put("fileSize", 128L)
            }
        )
        database.insert(
            "article_image_manifest",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("entryId", 42L)
                put("originalUrl", "https://example.com/image.jpg")
            }
        )
        database.insert(
            "article_credibility",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("entryId", 42L)
                put("score", 0.8)
                put("confidence", "HIGH")
                put("summary", "Legacy summary")
                put("reasons", "Legacy reasons")
                put("redFlags", "")
                put("factors", "")
                put("modelId", "legacy-model")
                put("analyzedAt", 1_700_000_004_000L)
                put("contentTruncated", 0)
                if (version >= 23) put("contentFingerprint", "legacy-fingerprint")
            }
        )
        database.insert(
            "article_ai_overviews",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("entryId", 42L)
                put("overview", "Legacy overview")
                put("modelId", "legacy-model")
                put("contentHash", "legacy-hash")
                put("generatedAt", 1_700_000_005_000L)
            }
        )
        database.insert(
            "article_page_snapshots",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("entryId", 42L)
                put("originalUrl", "https://example.com/article")
                put("finalUrl", "https://example.com/article")
                put("directoryPath", "/data/user/0/com.hiosdra.hreader/files/article_pages/42")
                put("fetchedAt", 1_700_000_006_000L)
                put("byteSize", 256L)
                put("isComplete", 1)
            }
        )
        database.insert(
            "article_reading_positions",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("articleId", "42")
                put("progress", 0.5)
            }
        )
        if (version >= 22) {
            database.insert(
                "full_sync_seen",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("runId", "legacy-run")
                    put("articleId", "42")
                }
            )
        }
    }

    private fun assertMigratedData(database: SupportSQLiteDatabase, startVersion: Int) {
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM articles WHERE id = '42'"))
        assertEquals("Legacy title", scalarString(database, "SELECT title FROM articles WHERE id = '42'"))
        assertEquals(1, scalarInt(database, "SELECT pendingSync FROM articles WHERE id = '42'"))
        assertEquals(
            "https://example.com/image.jpg",
            scalarString(database, "SELECT leadImageUrl FROM articles WHERE id = '42'")
        )
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM article_contents WHERE entryId = 42"))
        assertEquals(
            if (startVersion >= 18) 1 else 0,
            scalarInt(database, "SELECT allImagesPrepared FROM article_contents WHERE entryId = 42")
        )
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM article_images WHERE entryId = 42"))
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM article_image_manifest WHERE entryId = 42"))
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM article_page_snapshots WHERE entryId = 42"))
        assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM article_reading_positions WHERE articleId = '42'"))
        assertEquals("Legacy overview", scalarString(database, "SELECT overview FROM article_ai_overviews"))
        assertEquals(
            if (startVersion <= 20) 0 else 1,
            scalarInt(database, "SELECT COUNT(*) FROM article_credibility")
        )
        assertEquals(
            if (startVersion >= 20) 1 else 0,
            scalarInt(database, "SELECT preloadAiOverview FROM feeds WHERE id = 7")
        )
        assertEquals(
            if (startVersion >= 22) 1 else 0,
            scalarInt(database, "SELECT COUNT(*) FROM full_sync_seen")
        )
        assertEquals(
            1,
            scalarInt(database, "SELECT COUNT(*) FROM articles_fts WHERE articles_fts MATCH 'Legacy'")
        )
        assertTrue(tableColumns(database, "articles").none { it == "starred" || it == "starredPendingSync" })
        assertEquals("contentFingerprint", tableColumns(database, "article_credibility").last())
    }

    private fun scalarInt(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun scalarString(database: SupportSQLiteDatabase, sql: String): String =
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(1)
            names
        }

    private fun createSchemaArchive(): File {
        val schemaRoot = listOf(File("app/schemas"), File("schemas"))
            .firstOrNull(File::isDirectory)
            ?: error("Room schema directory not found")
        val archive = Files.createTempFile("hreader-room-schemas", ".zip").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            schemaRoot.walkTopDown()
                .filter(File::isFile)
                .forEach { file ->
                    val entryName = schemaRoot.toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry("assets/$entryName"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return archive
    }
}

private class TestInstrumentation(private val testContext: Context) : Instrumentation() {
    override fun getContext(): Context = testContext

    override fun getTargetContext(): Context = testContext
}

private class SchemaContext(base: Context, schemaArchive: File) : ContextWrapper(base) {
    private val schemaAssets = ReflectionHelpers.callConstructor(AssetManager::class.java).also {
        ReflectionHelpers.callInstanceMethod<Int>(
            it,
            "addAssetPath",
            ReflectionHelpers.ClassParameter.from(String::class.java, schemaArchive.absolutePath)
        )
    }

    override fun getAssets(): AssetManager = schemaAssets
}

private class MigrationsTestApplication : Application()
