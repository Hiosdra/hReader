package com.hiosdra.hreader.data.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterRequestSerializationTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun serializeRequest_containsExpectedFields() {
        val request = OpenRouterRequest(
            model = "test-model",
            messages = listOf(
                ChatMessage(role = "system", content = "sys"),
                ChatMessage(role = "user", content = "hello")
            ),
            maxTokens = 123,
            temperature = 0.5
        )
        val json = moshi.adapter(OpenRouterRequest::class.java).toJson(request)
        assertTrue(json.contains("\"model\":\"test-model\""))
        assertTrue(json.contains("\"messages\""))
        assertTrue(json.contains("\"max_tokens\":123"))
        assertTrue(json.contains("\"temperature\":0.5"))
    }
}