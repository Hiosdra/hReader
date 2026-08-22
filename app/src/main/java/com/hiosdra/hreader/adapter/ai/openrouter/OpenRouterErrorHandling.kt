package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiProviderException
import com.squareup.moshi.JsonAdapter

private const val MAX_PROVIDER_ERROR_LENGTH = 400

class OpenRouterException(val code: Int?, message: String) : AiProviderException(code, message)

internal fun providerErrorMessage(
    raw: String,
    errorAdapter: JsonAdapter<OpenRouterErrorEnvelope>
): String {
    val parsed = runCatching { errorAdapter.fromJson(raw)?.error?.message }.getOrNull()
    return (parsed ?: raw.trim()).take(MAX_PROVIDER_ERROR_LENGTH)
}
