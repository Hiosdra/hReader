package com.hiosdra.hreader.adapter.ai.openrouter

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

internal const val OPENROUTER_API_BASE_URL = "https://openrouter.ai/api/v1/"

interface OpenRouterApiService {
    @GET("models")
    suspend fun getModels(): OpenRouterModelsResponse

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://hiosdra.com",
        @Header("X-Title") title: String = "hReader",
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}
