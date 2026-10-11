package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleAiSummaryDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiSummary
import com.hiosdra.hreader.core.application.port.out.ArticleAiSummaryStore
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class ArticleAiSummaryRepository(
    private val dao: ArticleAiSummaryDao
) : ArticleAiSummaryStore {
    companion object {
        private const val TAG = "ArticleAiSummaryRepo"
        private const val DELETE_CHUNK = 500
        private const val PROMPT_REVISION = "full-article-summary-v2"
    }

    override suspend fun get(entryId: Long, content: String, modelId: String): String? = try {
        dao.get(entryId, modelId, content.cacheHash())?.summary
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read cached summary for entry $entryId", e)
        null
    }

    override suspend fun save(entryId: Long, content: String, modelId: String, summary: String) {
        try {
            dao.insert(
                ArticleAiSummary(
                    entryId = entryId,
                    summary = summary,
                    modelId = modelId,
                    contentHash = content.cacheHash(),
                    generatedAt = Instant.now()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache summary for entry $entryId", e)
        }
    }

    override suspend fun cleanupOrphaned(currentEntryIds: Set<Long>) {
        val orphaned = dao.getAllEntryIds().filterNot(currentEntryIds::contains)
        orphaned.chunked(DELETE_CHUNK).forEach { chunk -> dao.deleteForEntries(chunk) }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun String.cacheHash(): String = "$PROMPT_REVISION\u0000$this".sha256()
}
