package com.hiosdra.hreader.adapter.ai

import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaArticleAiService
import com.hiosdra.hreader.adapter.ai.openrouter.ArticleAiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleAiGatewayRouterTest {
    private val openRouter = mockk<ArticleAiService>()
    private val gemma = mockk<GemmaArticleAiService>()
    private val router = ArticleAiGatewayRouter(openRouter, gemma)

    @Test
    fun localModelUsesGemmaGateway() = runBlocking {
        coEvery {
            gemma.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID)
        } returns Result.success("local")

        assertEquals(
            "local",
            router.generateArticleOverview("Title", "Body", Gemma4E2bModel.MODEL_ID).getOrThrow()
        )
        coVerify(exactly = 0) { openRouter.generateArticleOverview(any(), any(), any()) }
    }

    @Test
    fun remoteModelUsesOpenRouterGateway() = runBlocking {
        coEvery {
            openRouter.generateArticleOverview("Title", "Body", "vendor/model")
        } returns Result.success("remote")

        assertEquals(
            "remote",
            router.generateArticleOverview("Title", "Body", "vendor/model").getOrThrow()
        )
        coVerify(exactly = 0) { gemma.generateArticleOverview(any(), any(), any()) }
    }
}
