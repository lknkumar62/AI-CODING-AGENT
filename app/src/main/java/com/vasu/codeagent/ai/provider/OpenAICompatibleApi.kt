package com.vasu.codeagent.ai.provider

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Generic Retrofit surface for any OpenAI-compatible provider.
 * The full URL and headers (Authorization, etc.) are supplied per-call
 * since the base URL is user-configured at runtime, not compile time.
 */
interface OpenAICompatibleApi {
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>
}
