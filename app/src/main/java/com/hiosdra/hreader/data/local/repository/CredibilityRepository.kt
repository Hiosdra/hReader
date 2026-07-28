package com.hiosdra.hreader.data.local.repository

import android.util.Log
import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.dao.ArticleCredibilityDao
import com.hiosdra.hreader.data.local.entity.ArticleCredibility
import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.hiosdra.hreader.data.model.CredibilityFactor
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource

private const val TAG = "CredibilityRepo"
private const val LINE_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = "\u001f"

class CredibilityRepository(
    private val articleCredibilityDao: ArticleCredibilityDao,
    private val articleAiService: ArticleAiService
) {
    suspend fun getCached(entryId: Long): CredibilityReport? =
        articleCredibilityDao.getForEntry(entryId)?.toDomain()

    suspend fun getCached(entryIds: List<Long>): Map<Long, CredibilityReport> {
        if (entryIds.isEmpty()) return emptyMap()
        return articleCredibilityDao.getForEntries(entryIds).associate { it.entryId to it.toDomain() }
    }

    suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        forceRefresh: Boolean = false
    ): Result<CredibilityReport> {
        if (!forceRefresh) {
            getCached(entryId)?.let { return Result.success(it) }
        }

        return articleAiService.analyzeCredibility(source, modelId)
            .onSuccess { report ->
                runCatching { articleCredibilityDao.upsert(report.toEntity(entryId)) }
                    .onFailure { Log.e(TAG, "Failed to cache credibility for entry $entryId", it) }
            }
    }

    suspend fun cleanupOrphanedReports(currentEntryIds: Set<Long>) {
        if (currentEntryIds.isEmpty()) return
        val stored = articleCredibilityDao.getAllEntryIds()
        val orphaned = stored.filterNot { currentEntryIds.contains(it) }
        if (orphaned.isEmpty()) return
        articleCredibilityDao.deleteAll(orphaned)
    }

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
