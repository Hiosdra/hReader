package com.hiosdra.hreader.data.ai

import org.junit.Test
import org.junit.Assert.*

class AiModelTest {
    
    @Test
    fun testDefaultModel() {
        val defaultModel = AiModel.getDefault()
        assertEquals(AiModel.GPT_OSS_20B, defaultModel)
    }
    
    @Test
    fun testModelProperties() {
        val model = AiModel.GPT_OSS_20B
        assertEquals("GPT OSS 20B", model.displayName)
        assertEquals("openai/gpt-oss-20b:free", model.modelId)
        assertTrue(model.description.isNotEmpty())
    }
    
    @Test
    fun testAllModelsHaveFreeTag() {
        AiModel.entries.forEach { model ->
            assertTrue("Model ${model.name} should have :free tag", 
                model.modelId.endsWith(":free"))
        }
    }
}