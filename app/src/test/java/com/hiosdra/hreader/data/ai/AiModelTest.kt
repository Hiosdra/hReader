package com.hiosdra.hreader.data.ai

import org.junit.Test
import org.junit.Assert.*

class AiModelTest {
    
    @Test
    fun testDefaultModel() {
        val defaultModel = AiModel.getDefault()
        assertEquals(AiModel.LLAMA_3_2_3B, defaultModel)
    }
    
    @Test
    fun testModelProperties() {
        val model = AiModel.LLAMA_3_2_3B
        assertEquals("Llama 3.2 3B", model.displayName)
        assertEquals("meta-llama/llama-3.2-3b-instruct:free", model.modelId)
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