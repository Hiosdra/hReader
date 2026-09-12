package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleCredibilityDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleCredibility
import com.hiosdra.hreader.core.domain.model.CredibilityConfidence
import com.hiosdra.hreader.core.domain.model.CredibilityFactor
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.application.ai.credibilityInputFingerprint
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate

private const val TAG = "CredibilityRepo"
private const val LINE_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = "\u001f"

/** Below SQLite's 999 bound-variable ceiling on Android. */
private const val DELETE_CHUNK = 500

class CredibilityRepository(
    private val articleCredibilityDao: ArticleCredibilityDao,
    private val articleAiGateway: ArticleAiGateway,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) : CredibilityStore {
    override suspend fun invalidateForEntries(entryIds: List<Long>, session: SyncSession?) {
        if (entryIds.isEmpty()) return
        val activeSession = session ?: sessionGate.currentSession()
        sessionGate.withSession(activeSession) {
            entryIds.chunked(DELETE_CHUNK).forEach { chunk ->
                articleCredibilityDao.deleteAll(chunk)
            }
        }
    }

    override suspend fun getCached(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        session: SyncSession?
    ): CredibilityReport? {
        val activeSession = session ?: sessionGate.currentSession()
        return sessionGate.withSession(activeSession) {
            articleCredibilityDao
                .getForEntry(entryId, modelId, fingerprint(source))
                ?.toDomain()
        }
    }

    override suspend fun getCached(
        sources: Map<Long, CredibilitySource>,
        modelId: String,
        session: SyncSession?
    ): Map<Long, CredibilityReport> {
        if (sources.isEmpty()) return emptyMap()
        val activeSession = session ?: sessionGate.currentSession()
        return sessionGate.withSession(activeSession) {
            val currentDate = LocalDate.now(clock)
            val cached = articleCredibilityDao.getForEntries(sources.keys.toList(), modelId)
                .associateBy { it.entryId }
            sources.mapNotNull { (entryId, source) ->
                cached[entryId]
                    ?.takeIf {
                        it.contentFingerprint == credibilityInputFingerprint(source, currentDate)
                    }
                    ?.toDomain()
                    ?.let { entryId to it }
            }.toMap()
        }
    }

    override suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        forceRefresh: Boolean,
        session: SyncSession?
    ): Result<CredibilityReport> {
        val activeSession = session ?: sessionGate.currentSession()
        val contentFingerprint = fingerprint(source)
        if (!forceRefresh) {
            val cached = sessionGate.withSession(activeSession) {
                articleCredibilityDao.getForEntry(entryId, modelId, contentFingerprint)?.toDomain()
            }
            cached?.let { return Result.success(it) }
        }

        val result = articleAiGateway.analyzeCredibility(source, modelId)
        val report = result.getOrNull() ?: return result
        try {
            sessionGate.withSession(activeSession) {
                articleCredibilityDao.upsert(report.toEntity(entryId, contentFingerprint))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache credibility for entry $entryId", e)
        }
        return result
    }

    override suspend fun cleanupOrphanedReports(session: SyncSession?) {
        val activeSession = session ?: sessionGate.currentSession()
        sessionGate.withSession(activeSession) {
            while (true) {
                val orphaned = articleCredibilityDao.getOrphanedEntryIds(DELETE_CHUNK)
                if (orphaned.isEmpty()) break
                articleCredibilityDao.deleteAll(orphaned)
            }
        }
    }

    suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = analyze(entryId, source, modelId, forceRefresh = false)

    private fun CredibilityReport.toEntity(entryId: Long, contentFingerprint: String) = ArticleCredibility(
        entryId = entryId,
        score = score,
        confidence = confidence.name,
        summary = summary,
        reasons = reasons.toStorage(),
        redFlags = redFlags.toStorage(),
        factors = factors.joinToString(LINE_SEPARATOR) { "${it.name.toSingleLine()}$FIELD_SEPARATOR${it.score}" },
        modelId = modelId,
        analyzedAt = analyzedAt,
        contentTruncated = contentTruncated,
        contentFingerprint = contentFingerprint
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

    private fun fingerprint(source: CredibilitySource): String =
        credibilityInputFingerprint(source, LocalDate.now(clock))

    private fun String.toLines(): List<String> =
        split(LINE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private fun List<String>.toStorage(): String =
        joinToString(LINE_SEPARATOR) { it.toSingleLine() }

    private fun String.toSingleLine(): String =
        replace(FIELD_SEPARATOR, " ").replace(Regex("\\s+"), " ").trim()
}
