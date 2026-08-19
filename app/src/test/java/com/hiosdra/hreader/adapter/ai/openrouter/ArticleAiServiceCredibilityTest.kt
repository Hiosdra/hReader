package com.hiosdra.hreader.adapter.ai.openrouter

import android.util.Log
import com.hiosdra.hreader.adapter.ai.common.CredibilityParseException
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.core.domain.model.CredibilityConfidence
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.adapter.preferences.PreferencesManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Clock
import java.time.Instant

class ArticleAiServiceCredibilityTest {
    private val api = mockk<OpenRouterApiService>()
    private val preferences = mockk<PreferencesManager>()
    private val service = ArticleAiService(
        openRouterApiService = api,
        preferencesManager = preferences,
        credibilityPromptBuilder = CredibilityPromptBuilder(Clock.systemDefaultZone()),
        credibilityResponseParser = CredibilityResponseParser(
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        ),
        credibilityReportFactory = CredibilityReportFactory(Clock.systemDefaultZone())
    )

    private val source = CredibilitySource(
        title = "Headline",
        content = "<p>An article body long enough to analyse.</p>",
        author = "Author",
        feedTitle = "Feed",
        url = "https://example.com/story",
        publishedAt = Instant.ofEpochSecond(1_700_000_000)
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { preferences.getOpenRouterApiKey() } returns "test-key"
    }

    private fun answer(content: String) = Response.success(
        OpenRouterResponse(
            choices = listOf(Choice(message = ChatMessage("assistant", content), index = 0))
        )
    )

    private fun httpError(code: Int, message: String) = Response.error<OpenRouterResponse>(
        code,
        message.toResponseBody("application/json".toMediaType())
    )

    @Test
    fun buildsAReportFromTheModelVerdict() = runBlocking {
        val request = slot<OpenRouterRequest>()
        coEvery { api.chatCompletion(any(), any(), any(), capture(request)) } returns answer(
            """{"score": 0.8, "confidence": "high", "summary": "Solid.", "reasons": ["Named sources"]}"""
        )

        val report = service.analyzeCredibility(source, "test/model").getOrThrow()

        assertEquals("test/model", request.captured.model)
        assertEquals(ResponseFormat.JsonObject, request.captured.responseFormat)
        assertEquals(1500, request.captured.maxTokens)
        assertEquals(0.2, request.captured.temperature, 0.0)
        assertEquals(0.8f, report.score, 0.0001f)
        assertEquals(CredibilityConfidence.HIGH, report.confidence)
        assertEquals(listOf("Named sources"), report.reasons)
        assertEquals("test/model", report.modelId)
    }

    @Test
    fun retriesWithoutResponseFormatWhenTheModelRejectsIt() = runBlocking {
        val requests = mutableListOf<OpenRouterRequest>()
        coEvery { api.chatCompletion(any(), any(), any(), capture(requests)) } returnsMany listOf(
            httpError(400, "response_format is not supported"),
            answer("""{"score": 0.4}""")
        )

        val report = service.analyzeCredibility(source, "test/model").getOrThrow()

        assertEquals(0.4f, report.score, 0.0001f)
        assertEquals(2, requests.size)
        assertEquals(ResponseFormat.JsonObject, requests.first().responseFormat)
        assertNull(requests.last().responseFormat)
    }

    @Test
    fun doesNotRetryOnAuthenticationFailures() = runBlocking {
        coEvery { api.chatCompletion(any(), any(), any(), any()) } returns httpError(401, "no credits")

        assertTrue(service.analyzeCredibility(source, "test/model").isFailure)
        coVerify(exactly = 1) { api.chatCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun failsInsteadOfInventingAScoreWhenTheAnswerIsNotJson() = runBlocking {
        coEvery { api.chatCompletion(any(), any(), any(), any()) } returns
            answer("I would say this article is fairly trustworthy.")

        val result = service.analyzeCredibility(source, "test/model")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CredibilityParseException)
    }

    @Test
    fun failsWhenTheApiKeyIsMissing() = runBlocking {
        every { preferences.getOpenRouterApiKey() } returns ""

        val result = service.analyzeCredibility(source, "test/model")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.chatCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun failsWhenTheArticleHasNoText() = runBlocking {
        val result = service.analyzeCredibility(source.copy(content = "  "), "test/model")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.chatCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun surfacesProviderErrorsCarriedInASuccessfulResponse() = runBlocking {
        coEvery { api.chatCompletion(any(), any(), any(), any()) } returns Response.success(
            OpenRouterResponse(choices = emptyList(), error = ErrorDetail("rate limited"))
        )

        val result = service.analyzeCredibility(source, "test/model")

        assertTrue(result.exceptionOrNull()?.message?.contains("rate limited") == true)
    }

    @Test
    fun marksTheReportTruncatedForLongArticles() = runBlocking {
        val request = slot<OpenRouterRequest>()
        coEvery { api.chatCompletion(any(), any(), any(), capture(request)) } returns answer("""{"score": 0.5}""")

        val report = service.analyzeCredibility(
            source.copy(content = "word ".repeat(5_000)),
            "test/model"
        ).getOrThrow()

        assertTrue(report.contentTruncated)
        assertTrue(request.captured.messages.last().content.contains("truncated"))
    }
}
