package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.ai.ArticleAiService
import com.hiosdra.hreader.data.local.dao.ArticleCredibilityDao
import com.hiosdra.hreader.data.local.entity.ArticleCredibility
import com.hiosdra.hreader.data.local.repository.CredibilityRepository
import com.hiosdra.hreader.data.model.CredibilityConfidence
import com.hiosdra.hreader.data.model.CredibilityFactor
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant

@RunWith(JUnit4::class)
class CredibilityRepositoryTest {
    private val dao = mockk<ArticleCredibilityDao>()
    private val aiService = mockk<ArticleAiService>()
    private val repo = CredibilityRepository(dao, aiService)

    private val source = CredibilitySource(
        title = "Title",
        content = "Body",
        author = "Author",
        feedTitle = "Feed",
        url = "https://example.com/a",
        publishedAt = Instant.ofEpochSecond(1_700_000_000)
    )

    private val report = CredibilityReport(
        score = 0.62f,
        confidence = CredibilityConfidence.LOW,
        summary = "Mixed sourcing.",
        reasons = listOf("Quotes one named expert", "Links to a study"),
        redFlags = listOf("Headline overstates the finding"),
        factors = listOf(CredibilityFactor("sourcing", 0.7f), CredibilityFactor("tone", 0.4f)),
        modelId = "test/model",
        analyzedAt = Instant.ofEpochSecond(1_700_000_500),
        contentTruncated = true
    )

    @Before
    fun stubAndroidLog() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun analyze_storesReportAndReadsItBackUnchanged() = runBlocking {
        coEvery { dao.getForEntry(7L) } returns null
        coEvery { aiService.analyzeCredibility(source, "test/model") } returns Result.success(report)
        val stored = slot<ArticleCredibility>()
        coEvery { dao.upsert(capture(stored)) } returns Unit

        val analyzed = repo.analyze(7L, source, "test/model")
        assertEquals(report, analyzed.getOrNull())

        coEvery { dao.getForEntry(7L) } returns stored.captured
        assertEquals(report, repo.getCached(7L))
    }

    @Test
    fun analyze_servesCacheWithoutCallingTheModel() = runBlocking {
        coEvery { dao.getForEntry(7L) } returns entityFor(7L)

        val result = repo.analyze(7L, source, "test/model")

        assertEquals(0.62f, result.getOrNull()?.score)
        coVerify(exactly = 0) { aiService.analyzeCredibility(any(), any()) }
    }

    @Test
    fun analyze_bypassesCacheWhenForced() = runBlocking {
        coEvery { aiService.analyzeCredibility(source, "test/model") } returns Result.success(report)
        coEvery { dao.upsert(any()) } returns Unit

        repo.analyze(7L, source, "test/model", forceRefresh = true)

        coVerify(exactly = 0) { dao.getForEntry(any()) }
        coVerify(exactly = 1) { aiService.analyzeCredibility(source, "test/model") }
    }

    @Test
    fun analyze_stillReturnsTheReportWhenCachingFails() = runBlocking {
        coEvery { dao.getForEntry(7L) } returns null
        coEvery { aiService.analyzeCredibility(source, "test/model") } returns Result.success(report)
        coEvery { dao.upsert(any()) } throws IllegalStateException("disk full")

        assertEquals(report, repo.analyze(7L, source, "test/model").getOrNull())
    }

    @Test
    fun analyze_doesNotCacheFailures() = runBlocking {
        coEvery { dao.getForEntry(7L) } returns null
        coEvery { aiService.analyzeCredibility(source, "test/model") } returns
            Result.failure(IllegalStateException("no verdict"))

        assertTrue(repo.analyze(7L, source, "test/model").isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun multilineTextSurvivesTheRoundTripAsOneItem() = runBlocking {
        val noisy = report.copy(reasons = listOf("First line\nsecond line", "  padded  "))
        coEvery { dao.getForEntry(7L) } returns null
        coEvery { aiService.analyzeCredibility(source, "test/model") } returns Result.success(noisy)
        val stored = slot<ArticleCredibility>()
        coEvery { dao.upsert(capture(stored)) } returns Unit

        repo.analyze(7L, source, "test/model")
        coEvery { dao.getForEntry(7L) } returns stored.captured

        assertEquals(listOf("First line second line", "padded"), repo.getCached(7L)?.reasons)
    }

    @Test
    fun cleanupOrphanedReports_deletesOnlyUnknownEntries() = runBlocking {
        coEvery { dao.getAllEntryIds() } returns listOf(1L, 2L, 3L)
        val deleted = slot<List<Long>>()
        coEvery { dao.deleteAll(capture(deleted)) } returns Unit

        repo.cleanupOrphanedReports(setOf(2L))

        assertEquals(listOf(1L, 3L), deleted.captured)
    }

    @Test
    fun cleanupOrphanedReports_doesNothingWhenThereAreNoArticles() = runBlocking {
        repo.cleanupOrphanedReports(emptySet())

        coVerify(exactly = 0) { dao.getAllEntryIds() }
        coVerify(exactly = 0) { dao.deleteAll(any()) }
    }

    private fun entityFor(entryId: Long) = ArticleCredibility(
        entryId = entryId,
        score = 0.62f,
        confidence = "LOW",
        summary = "Mixed sourcing.",
        reasons = "Quotes one named expert",
        redFlags = "",
        factors = "",
        modelId = "test/model",
        analyzedAt = Instant.ofEpochSecond(1_700_000_500),
        contentTruncated = true
    )
}
