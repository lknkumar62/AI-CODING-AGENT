package com.vasu.codeagent.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class OpenAICompatibleClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun sendChat(
        config: AIProviderConfig,
        systemPrompt: String,
        history: List<ChatMessageDto>,
        isNetworkAvailable: Boolean,
    ): AIClientResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable) return@withContext AIClientResult.Error("Network is unavailable")
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            return@withContext AIClientResult.Error("AI provider is not configured")
        }

        val messages = buildList {
            add(ChatMessageDto(role = "system", content = systemPrompt))
            addAll(history)
        }
        val payload = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val requestBuilder = Request.Builder().url(endpoint).post(body)
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        runCatching {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    AIClientResult.Error("HTTP ${response.code}: ${responseText.take(500)}")
                } else {
                    val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
                    val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                    if (text.isBlank()) AIClientResult.Error("Provider returned an empty response")
                    else AIClientResult.Success(text)
                }
            }
        }.getOrElse { AIClientResult.Error(it.message ?: it.javaClass.simpleName) }
    }
}
