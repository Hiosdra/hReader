package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleCredibilityDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleCredibility
import com.hiosdra.hreader.core.domain.model.CredibilityConfidence
import com.hiosdra.hreader.core.domain.model.CredibilityFactor
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import kotlinx.coroutines.CancellationException

private const val TAG = "CredibilityRepo"
private const val LINE_SEPARATOR = "\n"

/** Below SQLite's 999 bound-variable ceiling on Android. */
private const val DELETE_CHUNK = 500
private const val FIELD_SEPARATOR = "\u001f"

class CredibilityRepository(
    private val articleCredibilityDao: ArticleCredibilityDao,
    private val articleAiGateway: ArticleAiGateway
) : CredibilityStore {
    override suspend fun getCached(entryId: Long, modelId: String): CredibilityReport? =
        articleCredibilityDao.getForEntry(entryId, modelId)?.toDomain()

    override suspend fun getCached(entryIds: List<Long>, modelId: String): Map<Long, CredibilityReport> {
        if (entryIds.isEmpty()) return emptyMap()
        return articleCredibilityDao.getForEntries(entryIds, modelId)
            .associate { it.entryId to it.toDomain() }
    }

    override suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        forceRefresh: Boolean
    ): Result<CredibilityReport> {
        if (!forceRefresh) {
            getCached(entryId, modelId)?.let { return Result.success(it) }
        }

        return articleAiGateway.analyzeCredibility(source, modelId)
            .onSuccess { report ->
                try {
                    articleCredibilityDao.upsert(report.toEntity(entryId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cache credibility for entry $entryId", e)
                }
            }
    }

    /**
     * An empty [currentEntryIds] is not treated as suspicious. It used to be — nothing deleted
     * articles then, so an empty table could only mean something had gone wrong. Retention and
     * full-sync reconciliation delete them routinely now, which makes "no articles left" a normal
     * state and exactly the point at which every stored report has become an orphan.
     */
    override suspend fun cleanupOrphanedReports(currentEntryIds: Set<Long>) {
        val stored = articleCredibilityDao.getAllEntryIds()
        val orphaned = stored.filterNot { currentEntryIds.contains(it) }
        if (orphaned.isEmpty()) return
        // Chunked below SQLite's 999 bound-variable ceiling: a prune can orphan thousands at once.
        orphaned.chunked(DELETE_CHUNK).forEach { articleCredibilityDao.deleteAll(it) }
    }

    suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = analyze(entryId, source, modelId, forceRefresh = false)

    private fun CredibilityReport.toEntity(entryId: Long) = ArticleCredibility(
        entryId = entryId,
        score = score,
        confidence = confidence.name,
        summary = summary,
        reasons = reasons.toStorage(),
        redFlags = redFlags.toStorage(),
        factors = factors.joinToString(LINE_SEPARATOR) { "${it.name.toSingleLine()}$FIELD_SEPARATOR${it.score}" },
        modelId = modelId,
        analyzedAt = analyzedAt,
        contentTruncated = contentTruncated
    )

    private fun ArticleCredibility.toDomain() = CredibilityReport(
        score = score,
        confidence = CredibilityConfidence.entries.find { it.name == confidence }
            ?: CredibilityConfidence.MEDIUM,
        summary = summary,
        reasons = reasons.toLines(),
        redFlags = redFlags.toLines(),
        factors = factors.toLines().mapNotNull { line ->
            val name = line.substringBefore(FIELD_SEPARATOR).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val value = line.substringAfter(FIELD_SEPARATOR, "").toFloatOrNull()
                ?: return@mapNotNull null
            CredibilityFactor(name, value)
        },
        modelId = modelId,
        analyzedAt = analyzedAt,
        contentTruncated = contentTruncated
    )

    private fun String.toLines(): List<String> =
        split(LINE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private fun List<String>.toStorage(): String =
        joinToString(LINE_SEPARATOR) { it.toSingleLine() }

    private fun String.toSingleLine(): String =
        replace(FIELD_SEPARATOR, " ").replace(Regex("\\s+"), " ").trim()
}
