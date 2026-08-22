package com.hiosdra.hreader.adapter.ai.openrouter

import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

private const val OPENROUTER_REFERER = "https://hiosdra.com"
private const val OPENROUTER_TITLE = "hReader"
private const val EMPTY_STREAM_MESSAGE = "The model returned an empty response."
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

interface OpenRouterStreamingClient {
    suspend fun stream(
        authorization: String,
        request: OpenRouterRequest,
        onDelta: suspend (String) -> Unit
    ): String
}

class OkHttpOpenRouterStreamingClient(
    private val eventSourceFactory: EventSource.Factory,
    moshi: Moshi,
    private val endpoint: String
) : OpenRouterStreamingClient {
    constructor(client: OkHttpClient, moshi: Moshi) : this(
        eventSourceFactory = EventSources.createFactory(
            client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        ),
        moshi = moshi,
        endpoint = "${OPENROUTER_API_BASE_URL}chat/completions"
    )

    private val requestAdapter = moshi.adapter(OpenRouterRequest::class.java)
    private val streamAdapter = moshi.adapter(OpenRouterStreamResponse::class.java)
    private val errorAdapter = moshi.adapter(OpenRouterErrorEnvelope::class.java)

    override suspend fun stream(
        authorization: String,
        request: OpenRouterRequest,
        onDelta: suspend (String) -> Unit
    ): String {
        val content = StringBuilder()
        streamDeltas(authorization, request).collect { delta ->
            content.append(delta)
            onDelta(delta)
        }
        val result = content.toString()
        if (result.isBlank()) throw OpenRouterException(null, EMPTY_STREAM_MESSAGE)
        return result
    }

    private fun streamDeltas(
        authorization: String,
        request: OpenRouterRequest
    ): Flow<String> = callbackFlow {
        val body = requestAdapter.toJson(request.copy(stream = true))
            .toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", authorization)
            .header("HTTP-Referer", OPENROUTER_REFERER)
            .header("X-Title", OPENROUTER_TITLE)
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val eventSource = eventSourceFactory.newEventSource(
            httpRequest,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (data.trim() == "[DONE]") {
                        close()
                        return
                    }

                    val chunk = runCatching { streamAdapter.fromJson(data) }.getOrElse {
                        close(OpenRouterException(null, "The AI provider returned an invalid stream chunk."))
                        return
                    } ?: run {
                        close(OpenRouterException(null, "The AI provider returned an invalid stream chunk."))
                        return
                    }
                    chunk.error?.let { error ->
                        close(OpenRouterException(null, "AI Error: ${error.message}"))
                        return
                    }
                    chunk.choices
                        .firstOrNull()
                        ?.delta
                        ?.content
                        ?.takeIf(String::isNotEmpty)
                        ?.let { delta -> trySend(delta) }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val failure = response
                        ?.takeIf { !it.isSuccessful }
                        ?.let(::httpFailure)
                        ?: t
                        ?: OpenRouterException(null, "The AI stream failed.")
                    close(failure)
                }
            }
        )

        awaitClose { eventSource.cancel() }
    }

    private fun httpFailure(response: Response): OpenRouterException {
        val rawBody = runCatching { response.body.string() }.getOrDefault("")
        val providerMessage = providerErrorMessage(rawBody, errorAdapter).takeIf(String::isNotBlank)
        val message = "API call failed: ${response.code} - ${providerMessage ?: response.message}"
        return OpenRouterException(response.code, message)
    }
}
