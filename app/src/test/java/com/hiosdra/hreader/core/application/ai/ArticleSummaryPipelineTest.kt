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
}
