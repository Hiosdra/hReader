package com.hiosdra.hreader.adapter.ai.openrouter

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterStreamingClientTest {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun collectsDeltasUntilDoneAndBuildsStreamingRequest() = runBlocking {
        val factory = FakeEventSourceFactory { listener, eventSource ->
            listener.onEvent(eventSource, null, null, chunk("Hello "))
            listener.onEvent(eventSource, null, null, chunk("world"))
            listener.onEvent(eventSource, null, null, "[DONE]")
        }
        val client = client(factory)
        val deltas = mutableListOf<String>()

        val result = client.stream("Bearer key", request()) { deltas += it }

        assertEquals("Hello world", result)
        assertEquals(listOf("Hello ", "world"), deltas)
        assertEquals("Bearer key", factory.request.header("Authorization"))
        assertEquals("text/event-stream", factory.request.header("Accept"))
        val requestBody = Buffer().also { factory.request.body!!.writeTo(it) }.readUtf8()
        assertTrue(requestBody.contains("\"stream\":true"))
        assertTrue(factory.eventSource.cancelled)
    }

    @Test
    fun exposesStructuredHttpErrors() = runBlocking {
        val factory = FakeEventSourceFactory { listener, eventSource ->
            listener.onFailure(
                eventSource,
                null,
                Response.Builder()
                    .request(eventSource.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("{\"error\":{\"message\":\"rate limited\"}}".toResponseBody("application/json".toMediaType()))
                    .build()
            )
        }
        val error = runCatching { client(factory).stream("Bearer key", request()) {} }.exceptionOrNull()

        assertTrue(error is OpenRouterException)
        assertEquals(429, (error as OpenRouterException).code)
        assertTrue(error.message.orEmpty().contains("rate limited"))
        assertTrue(factory.eventSource.cancelled)
    }

    @Test
    fun cancelsEventSourceWhenTheCallerCancels() = runBlocking {
        val created = CompletableDeferred<Unit>()
        val factory = FakeEventSourceFactory { _, _ -> created.complete(Unit) }
        val job = launch {
            runCatching { client(factory).stream("Bearer key", request()) {} }
        }

        created.await()
        job.cancelAndJoin()

        assertTrue(factory.eventSource.cancelled)
    }

    private fun client(factory: FakeEventSourceFactory) = OkHttpOpenRouterStreamingClient(
        eventSourceFactory = factory,
        moshi = moshi,
        endpoint = "https://example.com/chat/completions"
    )

    private fun request() = OpenRouterRequest(
        model = "test/model",
        messages = listOf(ChatMessage("user", "Hello")),
        maxTokens = 100,
        temperature = 0.2
    )

    private fun chunk(content: String) =
        "{\"choices\":[{\"delta\":{\"content\":\"$content\"}}]}"
}

private class FakeEventSourceFactory(
    private val onCreate: (EventSourceListener, FakeEventSource) -> Unit
) : EventSource.Factory {
    lateinit var request: Request
    lateinit var eventSource: FakeEventSource

    override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
        this.request = request
        eventSource = FakeEventSource(request)
        onCreate(listener, eventSource)
        return eventSource
    }
}

private class FakeEventSource(
    private val requestValue: Request
) : EventSource {
    var cancelled = false

    override fun request(): Request = requestValue

    override fun cancel() {
        cancelled = true
    }
}
