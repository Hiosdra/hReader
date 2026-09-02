package com.hiosdra.hreader.adapter.ai.gemma

import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class GemmaArticleAiServiceTest {
    private val engine = mockk<GemmaInferenceEngine>()
    private val service = GemmaArticleAiService(
        engine = engine,
        credibilityPromptBuilder = CredibilityPromptBuilder(Clock.systemUTC()),
        credibilityResponseParser = CredibilityResponseParser(
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        ),
        credibilityReportFactory = CredibilityReportFactory(Clock.systemUTC())
    )

    @Test
    fun usesTheIncrementalPipelineForLocalSummaries() = runBlocking {
        val onDelta = slot<suspend (String) -> Unit>()
        coEvery {
            engine.generate(any(), any(), any(), any(), capture(onDelta))
        } returns Result.success("Local summary")
        val progress = mutableListOf<ArticleAiProgress>()

        val result = service.generateArticleOverview(
            title = "Title",
            content = "<p>Article body.</p>",
            modelId = Gemma4E2bModel.MODEL_ID,
            onProgress = { progress += it }
        ).getOrThrow()

        assertEquals("Local summary", result)
        coVerify(exactly = 1) { engine.generate(any(), any(), any(), any(), any()) }
        assertEquals(ArticleAiPhase.PREPARING, progress.first().phase)
        assertEquals(ArticleAiPhase.LOADING_MODEL, progress[1].phase)
        assertEquals(ArticleAiPhase.FINALIZING, progress.last().phase)
        assertTrue(progress.any { it.phase == ArticleAiPhase.THINKING })
        onDelta.captured("Local summary")
        assertTrue(progress.any { it.phase == ArticleAiPhase.STREAMING })
    }

    @Test
    fun splitsLongArticlesForTheLocalModelContext() = runBlocking {
        var inferenceCalls = 0
        coEvery {
            engine.generate(any(), any(), any(), any(), any())
        } answers {
            inferenceCalls++
            Result.success("Local summary")
        }

        service.generateArticleOverview(
            title = "Title",
            content = (1..6_000).joinToString(" ") { "Fact $it." },
            modelId = Gemma4E2bModel.MODEL_ID,
            onProgress = {}
        ).getOrThrow()

        assertTrue(inferenceCalls > 1)
    }

    @Test
    fun usesGroundedInstructionsForPromotionalArticleFooters() = runBlocking {
        val systemPrompt = slot<String>()
        val userPrompt = slot<String>()
        coEvery {
            engine.generate(capture(systemPrompt), capture(userPrompt), any(), any(), any())
        } returns Result.success("Local summary")

        service.generateArticleOverview(
            title = "Budget and public debt",
            content = "<p>The budget deficit grows.</p>",
            modelId = Gemma4E2bModel.MODEL_ID,
            onProgress = {}
        ).getOrThrow()

        assertTrue(systemPrompt.captured.contains("e-book promotions"))
        assertTrue(systemPrompt.captured.contains("late footer"))
        assertTrue(userPrompt.captured.contains("If this part is mostly a footer"))
    }
}
