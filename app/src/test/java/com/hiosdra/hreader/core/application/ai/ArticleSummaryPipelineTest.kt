package com.hiosdra.hreader.core.application.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSummaryPipelineTest {
    @Test
    fun reusesCompletedCompactionPartsOnRetry() = runBlocking {
        val pipeline = ArticleSummaryPipeline()
        val content = (1..250).joinToString(" ") { "Fact $it." }
        val progress = mutableListOf<ArticleAiProgress>()
        var inferenceCalls = 0

        val first = pipeline.generate(
            title = "Title",
            content = content,
            modelId = "test/model",
            contextLength = 1_024,
            onProgress = { progress += it }
        ) { _, onDelta ->
            inferenceCalls++
            val summary = "Summary $inferenceCalls"
            onDelta(summary)
            Result.success(summary)
        }.getOrThrow()

        val callsAfterFirstRun = inferenceCalls
        val second = pipeline.generate(
            title = "Title",
            content = content,
            modelId = "test/model",
            contextLength = 1_024,
            onProgress = { progress += it }
        ) { _, _ ->
            inferenceCalls++
            Result.success("unexpected")
        }.getOrThrow()

        assertTrue(callsAfterFirstRun > 1)
        assertEquals(callsAfterFirstRun, inferenceCalls)
        assertEquals(first, second)
        assertTrue(
            progress
                .filter { it.phase == ArticleAiPhase.STREAMING }
                .all { it.part == it.totalParts }
        )
        assertTrue(progress.any { it.phase == ArticleAiPhase.STREAMING })
        assertTrue(progress.any { it.phase == ArticleAiPhase.FINALIZING })
    }

    @Test
    fun finalPartIsNotBoundToTheWorkingSummaryLimit() = runBlocking {
        val pipeline = ArticleSummaryPipeline()
        val content = (1..250).joinToString(" ") { "Fact $it." }
        val finalOverview = ("Final overview. " + "Important conclusion. ".repeat(40)).trim()

        val result = pipeline.generate(
            title = "Title",
            content = content,
            modelId = "test/final-limit",
            contextLength = 1_024,
            onProgress = {}
        ) { part, _ ->
            if (part.isFinalPart) Result.success(finalOverview) else Result.success("Working summary")
        }.getOrThrow()

        assertEquals(finalOverview, result)
        assertTrue(
            result.length > ArticleSummaryPlanner.plan(content, 1_024).workingSummaryCharacterLimit
        )
    }
}
