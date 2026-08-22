package com.hiosdra.hreader.adapter.ai.openrouter

import android.util.Log
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import java.time.Clock

class ArticleAiServiceSummaryTest {
    private val api = mockk<OpenRouterApiService>()
    private val streamingClient = mockk<OpenRouterStreamingClient>()
    private val preferences = mockk<AiPreferences>()
    private val modelCatalog = mockk<AiModelCatalog>()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val service = ArticleAiService(
        openRouterApiService = api,
        streamingClient = streamingClient,
        preferencesManager = preferences,
        credibilityPromptBuilder = CredibilityPromptBuilder(Clock.systemUTC()),
        credibilityResponseParser = CredibilityResponseParser(moshi),
        credibilityReportFactory = CredibilityReportFactory(Clock.systemUTC()),
        aiModelCatalog = modelCatalog,
        moshi = moshi
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { preferences.getOpenRouterApiKey() } returns "test-key"
        coEvery { modelCatalog.getModels(any()) } returns listOf(
            AiModel(
                id = "test/model",
                displayName = "Test",
                description = "",
                contextLength = 1_024,
                isFree = true
            )
        )
    }

    @Test
    fun compactsLongArticlesAndStreamsEachPart() = runBlocking {
        val requests = slot<OpenRouterRequest>()
        val responses = ArrayDeque(listOf("first part", "final overview"))
        coEvery {
            streamingClient.stream(any(), capture(requests), any())
        } coAnswers {
            val response = responses.removeFirst()
            thirdArg<suspend (String) -> Unit>()(response)
            response
        }
        val progress = mutableListOf<com.hiosdra.hreader.core.application.ai.ArticleAiProgress>()
        val article = (1..250).joinToString(" ") { "Fact $it." }

        val result = service.generateArticleOverview(
            title = "Title",
            content = article,
            modelId = "test/model",
            onProgress = { progress += it }
        ).getOrThrow()

        assertEquals("final overview", result)
        coVerify(exactly = 2) { streamingClient.stream(any(), any(), any()) }
        assertTrue(requests.isCaptured)
        assertTrue(requests.captured.stream == true)
        assertTrue(requests.captured.messages.last().content.contains("first part"))
        assertTrue(progress.any { it.phase == ArticleAiPhase.COMPACTING && it.part == 2 })
        assertTrue(progress.any { it.phase == ArticleAiPhase.THINKING })
        assertTrue(
            progress
                .filter { it.phase == ArticleAiPhase.STREAMING }
                .all { it.part == it.totalParts }
        )
        assertTrue(progress.any { it.phase == ArticleAiPhase.STREAMING && it.draft.contains("final overview") })
        assertEquals(ArticleAiPhase.FINALIZING, progress.last().phase)
    }

    @Test
    fun exposesStructuredProviderErrors() = runBlocking {
        coEvery { streamingClient.stream(any(), any(), any()) } throws
            OpenRouterException(429, "API call failed: 429 - rate limited")

        val error = service.generateArticleOverview("Title", "Body", "test/model").exceptionOrNull()

        assertTrue(error is OpenRouterException)
        assertEquals(429, (error as OpenRouterException).code)
        assertTrue(error.message.orEmpty().contains("rate limited"))
    }
}
