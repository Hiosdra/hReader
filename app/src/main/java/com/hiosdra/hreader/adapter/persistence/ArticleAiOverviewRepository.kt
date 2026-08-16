package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiOverview
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class ArticleAiOverviewRepository(
    private val dao: ArticleAiOverviewDao
) : ArticleAiOverviewStore {
    companion object {
        private const val TAG = "ArticleAiOverviewRepo"
        private const val DELETE_CHUNK = 500
    }

    override suspend fun get(entryId: Long, content: String, modelId: String): String? = try {
        dao.get(entryId, modelId, content.sha256())?.overview
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read cached overview for entry $entryId", e)
        null
    }

    override suspend fun save(entryId: Long, content: String, modelId: String, overview: String) {
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

    override suspend fun cleanupOrphaned(currentEntryIds: Set<Long>) {
        val orphaned = dao.getAllEntryIds().filterNot(currentEntryIds::contains)
        orphaned.chunked(DELETE_CHUNK).forEach { chunk -> dao.deleteForEntries(chunk) }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
