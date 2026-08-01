package com.hiosdra.hreader.data.local.repository

import android.util.Log
import com.hiosdra.hreader.data.local.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.data.local.entity.ArticleAiOverview
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class ArticleAiOverviewRepository(
    private val dao: ArticleAiOverviewDao
) {
    companion object {
        private const val TAG = "ArticleAiOverviewRepo"
        private const val DELETE_CHUNK = 500
    }

    suspend fun get(entryId: Long, content: String, modelId: String): String? = try {
        dao.get(entryId, modelId, content.sha256())?.overview
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read cached overview for entry $entryId", e)
        null
    }

    suspend fun save(entryId: Long, content: String, modelId: String, overview: String) {
        try {
            dao.insert(
                ArticleAiOverview(
                    entryId = entryId,
                    overview = overview,
                    modelId = modelId,
                    contentHash = content.sha256(),
                    generatedAt = Instant.now()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache overview for entry $entryId", e)
        }
    }

    suspend fun cleanupOrphaned(currentEntryIds: Set<Long>) {
        val orphaned = dao.getAllEntryIds().filterNot(currentEntryIds::contains)
        orphaned.chunked(DELETE_CHUNK).forEach { chunk -> dao.deleteForEntries(chunk) }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
