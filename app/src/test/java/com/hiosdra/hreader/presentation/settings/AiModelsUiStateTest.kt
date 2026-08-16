package com.hiosdra.hreader.presentation.settings

import com.hiosdra.hreader.core.application.ai.AiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelsUiStateTest {

    private val freeModel = model("openai/gpt-oss-20b:free", "gpt-oss-20b", isFree = true)
    private val paidModel = model("anthropic/claude-opus", "Claude Opus", isFree = false)
    private val otherFreeModel = model("google/gemma-4-31b-it:free", "Gemma 4 31B", isFree = true)

    private val allModels = listOf(freeModel, paidModel, otherFreeModel)

    @Test
    fun `free filter hides paid models`() {
        val state = AiModelsUiState(models = allModels, freeOnly = true)

        assertEquals(listOf(freeModel, otherFreeModel), state.visibleModels)
    }

    @Test
    fun `disabling the free filter shows every model`() {
        val state = AiModelsUiState(models = allModels, freeOnly = false)

        assertEquals(3, state.visibleModels.size)
    }

    @Test
    fun `search narrows the list within the active filter`() {
        val state = AiModelsUiState(models = allModels, freeOnly = true, searchQuery = "gemma")

        assertEquals(listOf(otherFreeModel), state.visibleModels)
    }

    @Test
    fun `search does not reach past the free filter`() {
        val state = AiModelsUiState(models = allModels, freeOnly = true, searchQuery = "claude")

        assertTrue(state.visibleModels.isEmpty())
    }

    @Test
    fun `a selected model missing from the list is reported`() {
        val state = AiModelsUiState(models = allModels, selectedModelId = "qwen/qwen3-8b:free")

        assertTrue(state.selectedModelIsMissing)
    }

    @Test
    fun `a selected model present in the list is not reported`() {
        val state = AiModelsUiState(models = allModels, selectedModelId = paidModel.id)

        assertFalse(state.selectedModelIsMissing)
    }

    @Test
    fun `nothing is reported while the list is still empty`() {
        val state = AiModelsUiState(models = emptyList(), selectedModelId = "qwen/qwen3-8b:free")

        assertFalse(state.selectedModelIsMissing)
    }

    private fun model(id: String, displayName: String, isFree: Boolean) =
        AiModel(id = id, displayName = displayName, description = "", contextLength = 128000, isFree = isFree)
}
