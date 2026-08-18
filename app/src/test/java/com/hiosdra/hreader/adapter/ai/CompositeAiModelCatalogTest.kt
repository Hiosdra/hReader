package com.hiosdra.hreader.adapter.ai

import android.content.Context
import com.hiosdra.hreader.R
import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaModelManager
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class CompositeAiModelCatalogTest {
    private val context = mockk<Context>()
    private val openRouter = mockk<AiModelCatalog>()
    private val gemma = mockk<GemmaModelManager>()
    private val preferences = mockk<AiPreferences>()
    private val catalog = CompositeAiModelCatalog(context, openRouter, gemma, preferences)

    init {
        every { context.getString(R.string.ai_gemma_model_name) } returns "Gemma 4 E2B"
        every { context.getString(R.string.ai_gemma_model_description) } returns "On device"
    }

    @Test
    fun localModelWinsIfRemoteCatalogueContainsTheSameId() = runBlocking {
        coEvery { openRouter.getModels(false) } returns listOf(
            model(Gemma4E2bModel.MODEL_ID, "Remote duplicate"),
            model("vendor/other", "Other")
        )

        val models = catalog.getModels()

        assertEquals(
            listOf(Gemma4E2bModel.MODEL_ID, "vendor/other"),
            models.map(AiModel::id)
        )
        assertEquals("Gemma 4 E2B", models.first().displayName)
    }

    @Test
    fun localModelRemainsAvailableWhenRemoteCatalogueFails() = runBlocking {
        coEvery { openRouter.getModels(false) } throws IOException("offline")

        assertEquals(
            listOf(Gemma4E2bModel.MODEL_ID),
            catalog.getModels().map(AiModel::id)
        )
    }

    private fun model(id: String, displayName: String) = AiModel(
        id = id,
        displayName = displayName,
        description = "",
        contextLength = 0,
        isFree = true
    )
}
