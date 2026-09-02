package com.hiosdra.hreader.adapter.ai

import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaArticleAiService
import com.hiosdra.hreader.adapter.ai.openrouter.ArticleAiService
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleAiGatewayRouterTest {
    private val openRouter = mockk<ArticleAiService>()
    private val gemma = mockk<GemmaArticleAiService>()
    private val errorReporter = mockk<ErrorReporter>(relaxed = true)
    private val router = ArticleAiGatewayRouter(openRouter, gemma, errorReporter)

    @Test
    fun localModelUsesGemmaGateway() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        coEvery {
            gemma.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress)
        } returns Result.success("local")

        assertEquals(
            "local",
            router.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress).getOrThrow()
        )
        coVerify(exactly = 0) { openRouter.generateArticleOverview(any(), any(), any(), any()) }
    }

    @Test
    fun remoteModelUsesOpenRouterGateway() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        coEvery {
            openRouter.generateArticleOverview("Title", "Body", "vendor/model", progress)
        } returns Result.success("remote")

        assertEquals(
            "remote",
            router.generateArticleOverview("Title", "Body", "vendor/model", progress).getOrThrow()
        )
        coVerify(exactly = 0) { gemma.generateArticleOverview(any(), any(), any(), any()) }
    }

    @Test
    fun reportsRemoteSummaryFailures() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        val failure = IllegalStateException("remote failure")
        coEvery {
            openRouter.generateArticleOverview("Title", "Body", "vendor/model", progress)
        } returns Result.failure(failure)

        val result = router.generateArticleOverview("Title", "Body", "vendor/model", progress)

        assertEquals(failure, result.exceptionOrNull())
        verify(exactly = 1) { errorReporter.captureException(failure, "ai_summary") }
    }

    @Test
    fun reportsLocalSummaryExceptionsAsFailures() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        val failure = IllegalStateException("local failure")
        coEvery {
            gemma.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress)
        } throws failure

        val result = router.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress)

        assertEquals(failure, result.exceptionOrNull())
        verify(exactly = 1) { errorReporter.captureException(failure, "ai_summary") }
    }

    @Test
    fun doesNotReportExpectedSummaryInputFailures() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        coEvery {
            openRouter.generateArticleOverview("Title", "Body", "vendor/model", progress)
        } returns Result.failure(EmptyAiContentException())

        router.generateArticleOverview("Title", "Body", "vendor/model", progress)

        verify(exactly = 0) { errorReporter.captureException(any(), "ai_summary") }
    }

    @Test
    fun doesNotReportMissingLocalModel() = runBlocking {
        val progress: suspend (ArticleAiProgress) -> Unit = {}
        coEvery {
            gemma.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress)
        } returns Result.failure(GemmaModelNotInstalledException())

        router.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID, progress)

        verify(exactly = 0) { errorReporter.captureException(any(), "ai_summary") }
    }
}
