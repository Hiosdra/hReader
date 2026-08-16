package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelTest {

    @Test
    fun `free tag in the id marks the model as free`() {
        assertTrue(openRouterModel(id = "openai/gpt-oss-20b:free").toAiModel().isFree)
    }

    @Test
    fun `zero prices mark the model as free even without the free tag`() {
        assertTrue(openRouterModel(id = "vendor/model", prompt = "0", completion = "0").toAiModel().isFree)
    }

    @Test
    fun `a priced model is not free`() {
        assertFalse(
            openRouterModel(id = "vendor/model", prompt = "0.0000015", completion = "0.000002").toAiModel().isFree
        )
    }

    @Test
    fun `variable pricing is not treated as free`() {
        assertFalse(
            openRouterModel(id = "openrouter/auto-beta", prompt = "-1", completion = "-1").toAiModel().isFree
        )
    }

    @Test
    fun `the free models router is detected as free through its pricing`() {
        val router = openRouterModel(
            id = AiModel.DEFAULT_ID,
            name = "Free Models Router",
            prompt = "0",
            completion = "0"
        ).toAiModel()

        assertTrue(router.isFree)
    }

    @Test
    fun `missing pricing is not treated as free`() {
        assertFalse(OpenRouterModel(id = "vendor/model", name = "Model").toAiModel().isFree)
    }

    @Test
    fun `the id is used when the model has no name`() {
        assertEquals("vendor/model", OpenRouterModel(id = "vendor/model").toAiModel().displayName)
    }

    @Test
    fun `only the first line of the description is kept`() {
        val model = OpenRouterModel(id = "vendor/model", description = "Short summary\n\nLong details").toAiModel()

        assertEquals("Short summary", model.description)
    }

    @Test
    fun `search matches the id and the display name case insensitively`() {
        val model = openRouterModel(id = "google/gemma-4-31b-it:free", name = "Google: Gemma 4 31B").toAiModel()

        assertTrue(model.matches("GEMMA"))
        assertTrue(model.matches("google/"))
        assertTrue(model.matches("31b"))
        assertFalse(model.matches("qwen"))
    }

    @Test
    fun `an empty search query matches every model`() {
        val model = openRouterModel(id = "vendor/model").toAiModel()

        assertTrue(model.matches(""))
        assertTrue(model.matches("   "))
    }

    private fun openRouterModel(
        id: String,
        name: String? = "Model",
        prompt: String? = null,
        completion: String? = null
    ) = OpenRouterModel(
        id = id,
        name = name,
        description = "A model",
        contextLength = 128000,
        pricing = OpenRouterPricing(prompt = prompt, completion = completion)
    )
}
