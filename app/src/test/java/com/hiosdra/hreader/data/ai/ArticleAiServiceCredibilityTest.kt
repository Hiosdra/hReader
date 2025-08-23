package com.hiosdra.hreader.data.ai

import org.junit.Test
import org.junit.Assert.*

class ArticleAiServiceCredibilityTest {

    @Test
    fun `parseCredibilityScore handles valid scores correctly`() {
        val service = ArticleAiService(MockOpenRouterApiService())
        
        // Test valid scores
        assertEquals(0.75f, parseCredibilityScore(service, "0.75"), 0.001f)
        assertEquals(0.0f, parseCredibilityScore(service, "0.0"), 0.001f)
        assertEquals(1.0f, parseCredibilityScore(service, "1.0"), 0.001f)
        assertEquals(0.42f, parseCredibilityScore(service, "0.42"), 0.001f)
    }

    @Test
    fun `parseCredibilityScore handles invalid scores correctly`() {
        val service = ArticleAiService(MockOpenRouterApiService())
        
        // Test invalid input - should return default 0.5f
        assertEquals(0.5f, parseCredibilityScore(service, "invalid"), 0.001f)
        assertEquals(0.5f, parseCredibilityScore(service, ""), 0.001f)
        assertEquals(0.5f, parseCredibilityScore(service, "abc"), 0.001f)
    }

    @Test
    fun `parseCredibilityScore clamps out of range scores`() {
        val service = ArticleAiService(MockOpenRouterApiService())
        
        // Test out of range values - should be clamped to 0.0-1.0
        assertEquals(0.0f, parseCredibilityScore(service, "-0.5"), 0.001f)
        assertEquals(1.0f, parseCredibilityScore(service, "1.5"), 0.001f)
        assertEquals(1.0f, parseCredibilityScore(service, "2.0"), 0.001f)
    }

    @Test
    fun `parseCredibilityScore handles mixed text with numbers`() {
        val service = ArticleAiService(MockOpenRouterApiService())
        
        // Test extracting numbers from mixed text
        assertEquals(0.73f, parseCredibilityScore(service, "The score is 0.73 based on analysis"), 0.001f)
        assertEquals(0.8f, parseCredibilityScore(service, "0.8"), 0.001f)
    }

    // Helper method to access private parseCredibilityScore method via reflection
    private fun parseCredibilityScore(service: ArticleAiService, scoreText: String): Float {
        val method = ArticleAiService::class.java.getDeclaredMethod("parseCredibilityScore", String::class.java)
        method.isAccessible = true
        return method.invoke(service, scoreText) as Float
    }

    // Mock implementation for testing
    private class MockOpenRouterApiService : OpenRouterApiService {
        override suspend fun chatCompletion(authorization: String, request: OpenRouterRequest): retrofit2.Response<OpenRouterResponse> {
            // Mock implementation - not used in these tests
            TODO("Not implemented for testing")
        }
    }
}