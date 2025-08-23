package com.hiosdra.hreader.data.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://hiosdra.com",
        @Header("X-Title") title: String = "hReader",
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}