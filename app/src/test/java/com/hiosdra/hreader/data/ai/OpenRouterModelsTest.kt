package com.hiosdra.hreader.data.ai

import org.junit.Test
import org.junit.Assert.*

class OpenRouterModelsTest {
    
    @Test
    fun testOpenRouterRequestCreation() {
        val messages = listOf(
            ChatMessage("system", "You are a helpful assistant"),
            ChatMessage("user", "Summarize this article")
        )
        
        val request = OpenRouterRequest(
            model = "test-model",
            messages = messages,
            maxTokens = 100,
            temperature = 0.5
        )
        
        assertEquals("test-model", request.model)
        assertEquals(2, request.messages.size)
        assertEquals(100, request.maxTokens)
        assertEquals(0.5, request.temperature, 0.01)
    }
    
    @Test
    fun testChatMessageToMap() {
        val message = ChatMessage("user", "Test content")
        // Test basic ChatMessage functionality
        assertEquals("user", message.role)
        assertEquals("Test content", message.content)
    }
    
    @Test
    fun testOpenRouterResponseStructure() {
        val message = ChatMessage("assistant", "This is a summary")
        val choice = Choice(
            message = message,
            index = 0,
            finishReason = "stop"
        )
        
        val response = OpenRouterResponse(
            choices = listOf(choice),
            usage = Usage(10, 20, 30)
        )
        
        assertEquals(1, response.choices.size)
        assertEquals("This is a summary", response.choices[0].message.content)
        assertEquals(30, response.usage?.totalTokens)
        assertNull(response.error)
    }
    
    @Test
    fun testErrorResponse() {
        val error = ErrorDetail(
            message = "Rate limit exceeded",
            type = "rate_limit_error",
            code = "429"
        )
        
        val response = OpenRouterResponse(
            choices = emptyList(),
            error = error
        )
        
        assertEquals("Rate limit exceeded", response.error?.message)
        assertEquals("rate_limit_error", response.error?.type)
        assertTrue(response.choices.isEmpty())
    }
}